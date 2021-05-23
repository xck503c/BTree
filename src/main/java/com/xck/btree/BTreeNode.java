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
public class BTreeNode<K, V> implements Loggable, Serializable {

    private int nodeId;

    private boolean isWrite = false;

    private List<Entry<K, V>> keys;

    private List<Integer> childs; //child nodeId

    private ComparatorLoggable<K> keyComparator;

    public BTreeNode(boolean isLeaf) {
        this.keys = new ArrayList<Entry<K, V>>();
        this.childs = new ArrayList<Integer>();
        this.isLeaf = isLeaf;
    }

    /**
     * is leaf node?
     */
    private boolean isLeaf;

    /**
     * key-value，value is satellite information
     * @param <K>
     * @param <V>
     */
    private static class Entry<K, V> implements Serializable{
        private K key;
        private V value;

        public Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return "<" + key + "-" +value + ">";
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
    public SearchResult search(K key){
        if (keys.isEmpty()){
            return new SearchResult(false, -1);
        }

        //find key
        for(int i=0; i<keys.size(); i++){
            Entry<K, V> entry = keys.get(i);
            if(keyComparator.compare(key, entry.key) == 0){
                return new SearchResult(true, i);
            }
        }

        for(int i=0; i<keys.size(); i++){
            Entry<K, V> entry = keys.get(i);
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
    public SearchResult searchInsertChild(K key){

        for(int i=0; i<keys.size(); i++){
            Entry<K, V> entry = keys.get(i);
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

    public BTreeNode<K, V> splitRootNode(BTreeNode<K, V> newRoot, BTreeNode<K, V> newNode){

        Entry<K, V> midKey = removeChildAndKey(this, newNode);

        //root nodeId = 1
        int newRootNodeId = newRoot.getNodeId();
        newRoot.setNodeId(getNodeId());
        setNodeId(newRootNodeId);

        //right shift and up mid child
        newRoot.childs.add(this.getNodeId());
        newRoot.childs.add(newNode.getNodeId());

        //right shift and up mid key
        newRoot.keys.add(midKey);

        writeToLog();
        newRoot.writeToLog();
        newNode.writeToLog();

        return newRoot;
    }

    public void splitChildNode(int childIndex, BTreeNode<K, V> newNode){
        Integer splitChildNodeId = childs.get(childIndex);
        BTreeNode<K, V> splitChild = diskRead(splitChildNodeId);

        Entry<K, V> midKey = removeChildAndKey(splitChild, newNode);

        if (!isLeaf()) {
            //right shift and up mid child
            this.childs.add(this.childs.get(this.childs.size()-1));
            for (int i=this.childs.size()-2; i>=childIndex+1; i--){
                this.childs.set(i+1, this.childs.get(i));
            }
            this.childs.set(childIndex+1, newNode.getNodeId());
        }

        //right shift and up mid key
        this.keys.add(this.keys.get(this.keys.size()-1));
        for (int i=this.keys.size()-2; i>=childIndex; i--){
            this.keys.set(i+1, this.keys.get(i));
        }
        this.keys.set(childIndex, midKey);

        newNode.writeToLog();
        splitChild.writeToLog();
        writeToLog();
    }

    /**
     * deal split node to new node
     * @param splitOldNode
     * @param newNode
     * @return
     */
    public Entry<K, V> removeChildAndKey(BTreeNode<K, V> splitOldNode, BTreeNode<K, V> newNode){
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
        List<Entry<K, V>> keys = splitOldNode.keys;
        //full node is 2t-1, so mid index is t, start remove is t+1
        //list is start 0, so mid is t-1, remove start is t
        //removeStart=(2t-1+1)/2
        //mid=(removeStart-1)
        int removeStart = (keys.size()+1)/2;
        for(int i=removeStart; i<keys.size(); i++){
            newNode.keys.add(keys.get(i));
            keys.set(i, null); //set remove tag
        }
        clearNullInList(keys);
        Entry<K, V> midKey = keys.get(removeStart-1);
        keys.set(keys.size()-1, null);
        clearNullInList(keys);

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

    public V getValue(int keyIndex){
        return keys.get(keyIndex).value;
    }

    public void dirInsert(K key, V value){
        int insertIndex = keys.size();
        for(int i=0; i<keys.size(); i++){
            Entry<K, V> entry = keys.get(i);
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
        Entry<K, V> entry = new Entry<K, V>(key, value);
        if (keys.size() > 0) {
            Entry<K, V> rightEntry = keys.get(keys.size()-1);
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

        writeToLog();
    }

    public int logSize() {
        int size = 0;

        for(Integer nodeId : childs){
            BTreeNode<K, V> node = new BTreeNode<K, V>(false);
            node.setNodeId(nodeId);
            node.readFromLog();
            size += node.logSize();
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(this);
            size += baos.size();
            return size;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void writeToLog() {

        String homeDir = System.getProperty("user.dir") + "/db";
        File file = new File(homeDir + "/" + nodeId);

        if (!file.getParentFile().exists()){
            file.getParentFile().mkdir();
        }
        if (file.exists()) file.delete();

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this);
            fos.flush();
            isWrite = true;
            keys = null;
            childs = null;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
            }
        }
    }

    public boolean readFromLog() {
        String homeDir = System.getProperty("user.dir") + "/db";
        File file = new File(homeDir + "/" + nodeId);

        if (!file.exists()) return false;

        if (!file.getParentFile().exists()){
            file.getParentFile().mkdir();
        }

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(fis);
            BTreeNode<K, V> bTreeNode = (BTreeNode<K, V>) ois.readObject();
            isWrite = false;
            copy(bTreeNode);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e){
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
            }
        }
        return false;
    }

    public boolean isWrite() {
        return isWrite;
    }

    public void copy(BTreeNode<K, V> bTreeNode){
        if (bTreeNode == null) return;
        this.keys = bTreeNode.keys;
        this.childs = bTreeNode.childs;
        this.isLeaf = bTreeNode.isLeaf;
        this.keyComparator = bTreeNode.keyComparator;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }

    public int getNodeId() {
        return nodeId;
    }

    public ComparatorLoggable<K> getKeyComparator() {
        return keyComparator;
    }

    public void setKeyComparator(ComparatorLoggable<K> keyComparator) {
        this.keyComparator = keyComparator;
    }

    private BTreeNode diskRead(Integer nodeId){
        BTreeNode bTreeNode = new BTreeNode(false);
        bTreeNode.setNodeId(nodeId);
        boolean isReadSuc = bTreeNode.readFromLog();
        if (!isReadSuc) return null;

        return bTreeNode;
    }

    @Override
    public String toString() {
        copy(diskRead(nodeId));

        StringBuilder sb = new StringBuilder();
        sb.append(nodeId).append("|");
        if (!this.isLeaf){
            int i=0;
            sb.append("<").append(childs.get(i++)).append(">");
            for (Entry<K, V> entry : keys){
                sb.append(entry).append("<").append(childs.get(i++)).append(">");
            }
            sb.append("\n");
            for(Integer nodeId : childs){
                BTreeNode<K, V> node = new BTreeNode<K, V>(false);
                node.setNodeId(nodeId);
                node.readFromLog();
                sb.append(node.toString());
            }
        }else {
            for (Entry<K, V> entry : keys){
                sb.append(entry).append("|");
            }
            sb.delete(sb.length()-1, sb.length());
            sb.append("\n");
        }
        return sb.toString();
    }
}
