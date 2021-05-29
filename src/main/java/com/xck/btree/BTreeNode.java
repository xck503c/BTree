package com.xck.btree;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @Classname BTreeNode
 * @Description
 * B tree node
 * @Date 2021/5/21 17:05
 * @Created by xck503c
 */
public class BTreeNode implements Serializable {

    private BTree bTree;

    private int pageIndex;

    private boolean isWrite = false;

    private List<Entry> keys;

    private List<Integer> childs; //child nodeId

    private ComparatorLoggable keyComparator;

    public BTreeNode(boolean isLeaf, BTree bTree) {
        this.keys = new ArrayList<Entry>();
        this.childs = new ArrayList<Integer>();
        this.isLeaf = isLeaf;
        this.bTree = bTree;
        this.keyComparator = new ComparatorLoggable<byte[]>() {
            public int compare(byte[] o1, byte[] o2) {
                for (int i=0; i<o1.length; i++){
                    if (o1[i] > o2[i]) return 1;
                    if (o1[i] < o2[i]) return -1;
                }
                return 0;
            }
        };
    }

    /**
     * is leaf node?
     */
    private boolean isLeaf;

    /**
     * key-value，value is satellite information
     */
    private static class Entry implements Serializable{
        private byte[] key;
        private long value;

        public Entry(byte[] key, long value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return "<" + key[key.length-2]+" "+key[key.length-1] + "-" +value + ">";
        }
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    public int keySize(){
        return keys.size();
    }

    /**
     * seq search
     * if keys is empty : return -1
     * @param key
     * @return
     */
    public SearchResult search(byte[] key){
        if (keys.isEmpty()){
            return new SearchResult(false, -1);
        }

        //find key
        for(int i=0; i<keys.size(); i++){
            Entry entry = keys.get(i);
            if(keyComparator.compare(key, entry.key) == 0){
                return new SearchResult(true, i);
            }
        }

        for(int i=0; i<keys.size(); i++){
            Entry entry = keys.get(i);
            //search first entry.key >= key
            if(keyComparator.compare(key, entry.key) > 0) {
                for (int j = i + 1; j < keys.size(); j++) {
                    //find last entry.key < key
                    if (keyComparator.compare(key, keys.get(j).key) > 0) {
                        i++;
                    }
                }
                //if search true return i means keyIndex
                //if false return i+1 means childIndex
                //example: key1 child1 key2, key1 is first less than searchKey
                return new SearchResult(false, i+1);
            }
        }
        //default 0
        return new SearchResult(false, 0);
    }

    /**
     *
     * @param key
     * @return child index
     */
    public SearchResult searchInsertChild(byte[] key){

        for(int i=0; i<keys.size(); i++){
            Entry entry = keys.get(i);
            //search first entry.key >= key
            if(keyComparator.compare(key, entry.key) >= 0){
                for (int j=i+1; j<keys.size(); j++){
                    //jump equals entry.key
                    if (keyComparator.compare(entry.key, keys.get(j).key) == 0){
                        i++;
                    }else if (keyComparator.compare(key, keys.get(j).key) >= 0){
                        //jump equals key and find last entry.key < key
                        i++;
                    }
                }

                return new SearchResult(false, i+1);
            }
        }
        //default 0
        return new SearchResult(false, 0);
    }

    public BTreeNode splitRootNode(BTreeNode newRoot, BTreeNode newNode){

        Entry midKey = removeChildAndKey(this, newNode);

        //right shift and up mid child
        newRoot.childs.add(this.getPageIndex());
        newRoot.childs.add(newNode.getPageIndex());

        //right shift and up mid key
        newRoot.keys.add(midKey);

        write();
        newRoot.write();
        newNode.write();

        return newRoot;
    }

    public void splitChildNode(int childIndex, BTreeNode splitChild, BTreeNode newNode){

        Entry midKey = removeChildAndKey(splitChild, newNode);

        if (!isLeaf()) {
            //right shift and up mid child
            this.childs.add(this.childs.get(this.childs.size()-1));
            for (int i=this.childs.size()-2; i>=childIndex+1; i--){
                this.childs.set(i+1, this.childs.get(i));
            }
            this.childs.set(childIndex+1, newNode.getPageIndex());
        }

        //right shift and up mid key
        this.keys.add(this.keys.get(this.keys.size()-1));
        for (int i=this.keys.size()-2; i>=childIndex; i--){
            this.keys.set(i+1, this.keys.get(i));
        }
        this.keys.set(childIndex, midKey);

        newNode.write();
        splitChild.write();
        write();
    }

    /**
     * deal split node to new node
     * @param splitOldNode
     * @param newNode
     * @return
     */
    public Entry removeChildAndKey(BTreeNode splitOldNode, BTreeNode newNode){
        //remove t child
        List<Integer> childs = splitOldNode.childs;
        //full node is 2t childs
        //remove t child to new node
        //so start=child.size/2 (half=0~child.size/2-1 and child.size/2~child.size)
        if (!splitOldNode.isLeaf()) { //leaf no has child
            for(int i=childs.size()/2; i<childs.size(); i++){
                newNode.childs.add(childs.get(i));
                childs.set(i, null); //set remove tag
            }
            clearNullInList(childs);
        }

        //remove t-1 key
        //full node is 2t-1, so mid index is t, start remove is t+1
        //list is start 0, so mid is t-1, remove start is t
        //removeStart=(2t-1+1)/2
        //mid=(removeStart-1)
        int removeStart = (splitOldNode.keys.size()+1)/2;
        for(int i=removeStart; i<splitOldNode.keys.size(); i++){
            newNode.keys.add(splitOldNode.keys.get(i));
            splitOldNode.keys.set(i, null); //set remove tag
        }
        clearNullInList(splitOldNode.keys);
        Entry midKey = splitOldNode.keys.get(removeStart-1);
        splitOldNode.keys.set(splitOldNode.keys.size()-1, null);
        clearNullInList(splitOldNode.keys);

        return midKey;
    }

    private void clearNullInList(List list){
        Iterator it = list.iterator();
        while (it.hasNext()){
            Object obj = it.next();
            if (obj == null) it.remove();
        }
    }

    public Integer getChild(int childIndex){
        return childs.get(childIndex);
    }

    public long getValue(int keyIndex){
        return keys.get(keyIndex).value;
    }

    public void dirInsert(byte[] key, long value){
        int insertIndex = keys.size();
        for(int i=0; i<keys.size(); i++){
            Entry entry = keys.get(i);
            //if key equals, inset rightmost
            if(keyComparator.compare(key, entry.key) >= 0){
                //jump equals find key
                for (int j=i+1; j<keys.size(); j++){
                    if (keyComparator.compare(entry.key, keys.get(j).key) == 0){
                        i++;
                    }else if (keyComparator.compare(key, keys.get(j).key) >= 0){
                        //jump equals key and find last entry.key < key
                        i++;
                    }
                }
                insertIndex = i+1; //right
            }
        }
        Entry entry = new Entry(key, value);
        if (keys.size() > 0) {
            Entry rightEntry = keys.get(keys.size()-1);
            keys.add(rightEntry);
            for(int i=keys.size()-2; i>=insertIndex; i--){
                //if new entry already exists, put left
                keys.set(i+1, keys.get(i)); //shift right one step
            }
            keys.set(insertIndex, entry);
        }else {
            //empty no shift
            keys.add(entry);
        }

        write();
    }

    public boolean isWrite() {
        return isWrite;
    }

    public void copy(BTreeNode bTreeNode){
        if (bTreeNode == null) return;
        this.keys = bTreeNode.keys;
        this.childs = bTreeNode.childs;
        this.isLeaf = bTreeNode.isLeaf;
        this.keyComparator = bTreeNode.keyComparator;
    }


    public ComparatorLoggable getKeyComparator() {
        return keyComparator;
    }

    public void setKeyComparator(ComparatorLoggable keyComparator) {
        this.keyComparator = keyComparator;
    }

    private BTreeNode diskRead(Integer pageIndex){
        BTreeNode bTreeNode = new BTreeNode(false, bTree);
        bTreeNode.setPageIndex(pageIndex);
        boolean isReadSuc = bTreeNode.read();
        if (!isReadSuc) return null;

        return bTreeNode;
    }

    @Override
    public String toString() {
        copy(diskRead(pageIndex));

        StringBuilder sb = new StringBuilder();
        sb.append(pageIndex).append("|");
        if (!this.isLeaf){
            int i=0;
            sb.append("<").append(childs.get(i++)).append(">");
            for (Entry entry : keys){
                sb.append(entry).append("<").append(childs.get(i++)).append(">");
            }
            sb.append("\n");
            for(Integer pageIndex : childs){
                BTreeNode node = new BTreeNode(false, bTree);
                node.setPageIndex(pageIndex);
                node.read();
                sb.append(node.toString());
            }
        }else {
            for (Entry entry : keys){
                sb.append(entry).append("|");
            }
            sb.delete(sb.length()-1, sb.length());
            sb.append("\n");
        }
        return sb.toString();
    }

    public boolean read(){
        RandomAccessFile randomAccessFile = bTree.getRandomAccessFile();
        long pageStart = -1;
        long childOffset = -1;
        try {
            //seek node page start
            pageStart = pageIndex * bTree.getPageSize() + bTree.headerSize();
            randomAccessFile.seek(pageStart);

            this.isLeaf = randomAccessFile.readBoolean();
            if (keys == null) keys = new ArrayList<Entry>();
            //read key-value
            int entrySize = randomAccessFile.readInt();
            for (int i = 0; i < entrySize; i++) {
                byte[] key = new byte[bTree.getKeySize()];
                randomAccessFile.read(key);
                keys.add(new Entry(key, randomAccessFile.readLong()));
            }
            //read child
            if(!isLeaf){
                //seek child start:
                //1. entrySize = (t-1)*(keySize + valueSize)
                //2. header = 5
                if (childs == null) childs = new ArrayList<Integer>();
                childOffset = pageStart + (2*bTree.getBranchingFactor()-1)*(bTree.getKeySize() + bTree.getValueSize()) + 5;
                randomAccessFile.seek(childOffset);
                for (int i = 0; i < entrySize+1; i++) {
                    childs.add(randomAccessFile.readInt());
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
//            System.out.println("read page:" + pageIndex + ", pageStart=" + pageStart
//                    + ", keys=" + keys
//                    + ", childs=" + childs + ", childOffset=" + childOffset
//                    + ", isLeaf=" + isLeaf);
            isWrite = false;
        }
        return false;
    }

    public boolean write(){
        RandomAccessFile randomAccessFile = bTree.getRandomAccessFile();
        long pageStart = -1;
        long childOffset = -1;
        try {
            //seek node page start
            pageStart = pageIndex * bTree.getPageSize() + bTree.headerSize();
            randomAccessFile.seek(pageStart);

            randomAccessFile.writeBoolean(isLeaf);
            randomAccessFile.writeInt(keys.size()); //entrySize
            //read key-value
            for (int i = 0; i < keys.size(); i++) {
                Entry entry = keys.get(i);
                if (bTree.getKeySize() - entry.key.length > 0){
                    byte[] bytes = new byte[bTree.getKeySize() - entry.key.length];
                    randomAccessFile.write(bytes);
                }
                randomAccessFile.write(entry.key);
                randomAccessFile.writeLong(entry.value);
            }
            //read child
            if(!isLeaf && childs.size() > 0){
                childOffset = pageStart + (2*bTree.getBranchingFactor()-1)*(bTree.getKeySize() + bTree.getValueSize()) + 5;
                randomAccessFile.seek(childOffset);
                for (int i = 0; i < keys.size()+1; i++) {
                    randomAccessFile.writeInt(childs.get(i));
                }
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            isWrite = true;
//            System.out.println("write page:" + pageIndex + ", pageStart=" + pageStart
//                    + ", keys=" + keys
//                    + ", childs=" + childs + ", childOffset=" + childOffset
//                    + ", isLeaf=" + isLeaf);
        }
        return false;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public void setLeaf(boolean leaf) {
        isLeaf = leaf;
    }

    public BTree getbTree() {
        return bTree;
    }

    public void setbTree(BTree bTree) {
        this.bTree = bTree;
    }
}
