package com.xck.btree;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * source: https://blog.csdn.net/jimo_lonely/article/details/82716142
 */
public class BTree {

    private int version;
    private int pageSize; //KB
    private int keySize;
    private int valueSize;
    private int usePages; //the number of page used by node
    private int dataUsePages; //the number of data block

    private int branchingFactor;

    private BTreeNode root;

    private ComparatorLoggable keyComparator;

    public AtomicInteger nodePageIndexGenerator = new AtomicInteger(0);
    public AtomicInteger nodeDataPageIndexGenerator = new AtomicInteger(0);

    private String homeDir = System.getProperty("user.dir") + "/db";

    private RandomAccessFile randomAccessFile;
    private RandomAccessFile randomAccessDataFile;

    public BTree() {
        this.version = 2;
        this.keySize = 256;
        this.valueSize = 8;
        this.usePages = 0;
        this.dataUsePages = 0;
        this.pageSize = 4096;
        this.branchingFactor = (pageSize+keySize+valueSize-5) / ((keySize + valueSize + 8)*2);

    }

    public void createTree() throws IOException{
        //init root node is leaf node
        this.root = createNewRoot(false, this);

        File file = new File(homeDir);
        if (!file.exists()) file.mkdir();
        if (!file.isDirectory()) {
            throw new IllegalArgumentException("homeDir=" + homeDir + ", not file");
        }

        boolean isFindRoot = false;
        File[] files = file.listFiles();

        for (File tmp : files) {
            if (tmp.getName().endsWith("index")) {
                randomAccessFile = new RandomAccessFile(tmp , "rw");
                read();
                isFindRoot = true;
            }
            if (tmp.getName().endsWith("data")){
                randomAccessDataFile = new RandomAccessFile(tmp , "rw");
                randomAccessDataFile.skipBytes(4);
                this.dataUsePages = randomAccessDataFile.readInt();
            }
        }
        if (!isFindRoot){
            randomAccessFile = new RandomAccessFile(new File(homeDir + "/index") , "rw");
            randomAccessDataFile = new RandomAccessFile(new File(homeDir + "/data") , "rw");
            write();
        }
    }

    public byte[] search(byte[] key) throws IOException{
        //convert to the same length
        if (key.length < keySize){
            byte[] tmp = new byte[keySize];
            for (int i=key.length-1, j=tmp.length-1; i>=0; i--, j--){
                tmp[j] = key[i];
            }
            key = tmp;
        }
        long dataValue = search(root, key);
        if (dataValue == -1) return null;
        return readData(dataValue);
    }

    private long search(BTreeNode node, byte[] key) {
        SearchResult result = node.search(key);
        if (result.isSearchSuc()) {
            return node.getValue(result.getSearchIndex());
        }

        //leaf no has child
        if (result.getSearchIndex() == -1 || node.isLeaf()) return -1;

        Integer childNodeId = node.getChild(result.getSearchIndex());
        BTreeNode child = diskRead(childNodeId);
        if (child != null) {
            return search(child, key);
        }

        return -1;
    }

    private BTreeNode diskRead(Integer pageIndex) {
        BTreeNode bTreeNode = new BTreeNode(false, this);
        bTreeNode.setPageIndex(pageIndex);
        bTreeNode.read();

        return bTreeNode;
    }

    public void insert(byte[] k, byte[] v) throws IOException{
        //convert to the same length
        if (k.length < keySize){
            byte[] tmp = new byte[keySize];
            for (int i=k.length-1, j=tmp.length-1; i>=0; i--, j--){
                tmp[j] = k[i];
            }
            k = tmp;
        }
        //root split
        root = diskRead(root.getPageIndex());
        if (root.keySize() == branchingFactor * 2 - 1) {
            BTreeNode newRoot = createNewRoot(true, this); //no leaf
            BTreeNode newNode = createNewNodeReferToOld(root.getPageIndex());
            root.splitRootNode(newRoot, newNode);
            root = newRoot;
            root = diskRead(root.getPageIndex());
        }
        long index = writeData(v);
        insertNoFull(root, k, index);
    }

    /**
     * insert entry into leaf node
     *
     * @param node
     * @param key
     * @param value
     */
    private void insertNoFull(BTreeNode node, byte[] key, long value) {
        if (node.isLeaf()) {
            //direct insert
            node.dirInsert(key, value);
            return;
        }

        SearchResult result = node.searchInsertChild(key);
        //visit child node
        Integer childNodeId = node.getChild(result.getSearchIndex());
        BTreeNode child = diskRead(childNodeId);

        //split child node
        if (child.keySize() == branchingFactor * 2 - 1) {
            BTreeNode newNode = createNewNodeReferToOld(childNodeId);
            node.splitChildNode(result.getSearchIndex(), child, newNode);

            //chose old child node or split child node
            //because childNode is left from node.key,node.key>=k
            //split new node key < node.key
            node = diskRead(node.getPageIndex());
            result = node.searchInsertChild(key);
            childNodeId = node.getChild(result.getSearchIndex());
            child = diskRead(childNodeId);
        }

        insertNoFull(child, key, value);
    }

    public void delete(byte[] key) {

    }

    /**
     * refer to old node:
     * 1. isLeaf
     * 2. comparator
     * new nodeId to uniq
     *
     * @param oldNodeId
     * @return
     */
    public BTreeNode createNewNodeReferToOld(Integer oldNodeId) {
        BTreeNode oldNode = diskRead(oldNodeId);

        BTreeNode newNode = new BTreeNode(oldNode.isLeaf(), this);
        newNode.setPageIndex(nodePageIndexGenerator.getAndIncrement());
        newNode.setKeyComparator(oldNode.getKeyComparator());
        return newNode;
    }

    /**
     * first root node is leaf node
     *
     * @param isHasChild
     * @param bTree
     * @return
     */
    public BTreeNode createNewRoot(boolean isHasChild, BTree bTree) {
        BTreeNode root = new BTreeNode(!isHasChild, bTree);
        int pageIndex = nodePageIndexGenerator.getAndIncrement();
        root.setPageIndex(pageIndex);
        root.setKeyComparator(keyComparator);
        return root;
    }

    public void setKeyComparator(ComparatorLoggable keyComparator) {
        this.keyComparator = keyComparator;
    }

    public void setHomeDir(String homeDir) {
        this.homeDir = homeDir;
    }

    /**
     * read tree header and root node
     * @throws IOException
     */
    public void read()  throws IOException {
        randomAccessFile.seek(0);

        this.version = randomAccessFile.readInt();
        this.keySize = randomAccessFile.readInt();
        this.valueSize = randomAccessFile.readShort();
        this.pageSize = randomAccessFile.readInt();
        this.usePages = (randomAccessFile.readInt());
        nodePageIndexGenerator.set(usePages);

        //t child t-1 keys
        //= (2t-1)*(keySize+valueSize) + 2t*4 + 5
        //= (2t-1)*(keySize+valueSize) + 8t + 5
        //= (2t)*(keySize+valueSize) - (keySize+valueSize) + 8t + 5
        //= 2t*(keySize+valueSize + 8) - (keySize+valueSize) + 5
        //
        //(pagesize+keySize+valueSize-5)/(2*(keySize+valueSize + 8))
        this.branchingFactor = (pageSize+keySize+valueSize-5) / ((keySize + valueSize + 8)*2);

        int rootPageIndex = randomAccessFile.readInt();
        root.setPageIndex(rootPageIndex);
        root.read();

        randomAccessDataFile.seek(0);
        randomAccessDataFile.skipBytes(4);
        this.dataUsePages = randomAccessDataFile.readInt();
        nodeDataPageIndexGenerator.set(dataUsePages);

//        System.out.println("read tree, usePages=" + usePages
//                + ", dataPages=" + dataUsePages
//                + ", rootPageIndex=" + root.getPageIndex());
    }

    /**
     * write tree header and root node
     * @throws IOException
     */
    public void write()  throws IOException {
        randomAccessFile.seek(0);

        randomAccessFile.writeInt(version);
        randomAccessFile.writeInt(keySize);
        randomAccessFile.writeShort(valueSize);
        randomAccessFile.writeInt(pageSize);
        randomAccessFile.writeInt(nodePageIndexGenerator.get());
        randomAccessFile.writeInt(root.getPageIndex());

        randomAccessDataFile.seek(0);
        randomAccessDataFile.skipBytes(4);
        randomAccessDataFile.writeInt(nodeDataPageIndexGenerator.get());

        root.write();

//        System.out.println("write tree, usePages=" + nodePageIndexGenerator.get()
//                + ", dataPages=" + nodeDataPageIndexGenerator.get()
//                + ", rootPageIndex=" + root.getPageIndex());
    }

    /**
     * read data in data file
     * @param offset high 4B means page index，low 4B means pageDataOffset
     * @return
     * @throws IOException
     */
    public byte[] readData(long offset)  throws IOException {
        //cal startPagedataPageIndex
        int pageOffset = (int)(offset & 0xffffffff);
        int dataPageIndex = (int)(offset >>> 16);
        int dataOffset = pageSize * dataPageIndex + 8 + pageOffset;
        //get real offset
        randomAccessDataFile.seek(dataOffset);

        int dataLen = randomAccessDataFile.readInt();
        byte[] value = new byte[dataLen];
        randomAccessDataFile.read(value);
        return value;
    }

    /**
     * write data in data file
     * @param data
     * @return data index: high 4B means page index，low 4B means pageDataOffset
     * @throws IOException
     */
    public long writeData(byte[] data)  throws IOException {
        //if full, new data page
        int dataPageIndex = nodeDataPageIndexGenerator.get();
        if (isCurDataPageFull(data.length)){
            dataPageIndex = nodeDataPageIndexGenerator.incrementAndGet();
        }

        int startPageOffset = pageSize * dataPageIndex + 8;
        randomAccessDataFile.seek(startPageOffset);
        int dataTotalLen = 0;
        int dataLen = 0;
        try {
            while ((dataLen = randomAccessDataFile.readInt()) != 0){
                randomAccessDataFile.skipBytes(dataLen);
                dataTotalLen+=4;
                dataTotalLen+=dataLen;
            }
        } catch (EOFException e) {
            //no data , readInt will throw EOFException
        }

        randomAccessDataFile.writeInt(data.length);
        randomAccessDataFile.write(data);

        long value = 0L;
        value = value | dataPageIndex;
        value = value << 16;
        value = value | dataTotalLen;
        return value;
    }

    /**
     * is cur dataPage full?
     * @param valueLen
     * @return
     * @throws IOException
     */
    public boolean isCurDataPageFull(int valueLen) throws IOException{
        int curDataPageOffset = pageSize * nodeDataPageIndexGenerator.get() + 8;
        randomAccessDataFile.seek(curDataPageOffset);
        int dataTotalLen = 0;
        int dataLen = 0;
        try {
            while ((dataLen = randomAccessDataFile.readInt()) != 0){
                randomAccessDataFile.skipBytes(dataLen);
                dataTotalLen+=dataLen;
            }
        } catch (IOException e) {
            //no data throw
            return false;
        }
        int curDataPageEndOffset = pageSize * (nodeDataPageIndexGenerator.get()+1);
        if ((curDataPageEndOffset - dataTotalLen) < valueLen){
            return false;
        }
        return true;
    }

    public void close() {
        try {
            write();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (randomAccessFile != null) {
            try {
                randomAccessFile.getFD().sync();
                randomAccessFile.close();
            } catch (IOException e) {
            }
        }
        if (randomAccessDataFile != null) {
            try {
                randomAccessDataFile.getFD().sync();
                randomAccessDataFile.close();
            } catch (IOException e) {
            }
        }
    }

    @Override
    public String toString() {
        return root.toString();
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getKeySize() {
        return keySize;
    }

    public void setKeySize(int keySize) {
        this.keySize = keySize;
    }

    public int getValueSize() {
        return valueSize;
    }

    public void setValueSize(int valueSize) {
        this.valueSize = valueSize;
    }

    public int getUsePages() {
        return usePages;
    }

    public void setUsePages(int usePages) {
        this.usePages = usePages;
    }

    public int getBranchingFactor() {
        return branchingFactor;
    }

    public void setBranchingFactor(int branchingFactor) {
        this.branchingFactor = branchingFactor;
    }

    public BTreeNode getRoot() {
        return root;
    }

    public void setRoot(BTreeNode root) {
        this.root = root;
    }

    public RandomAccessFile getRandomAccessFile() {
        return randomAccessFile;
    }

    public int headerSize(){
        return keySize + valueSize + 16;
    }
}
