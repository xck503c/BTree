package com.xck.btree;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * source: https://blog.csdn.net/jimo_lonely/article/details/82716142
 *
 * @param <K>
 * @param <V>
 */
public class BTree<K, V> {

    public static int branchingFactory = 50;

    private BTreeNode<K, V> root;

    private ComparatorLoggable<K> keyComparator;

    public static AtomicInteger nodeIdGenerator = new AtomicInteger(0);

    private String homeDir = System.getProperty("user.dir") + "/db";

    public BTree() {
    }

    public void createTree() {
        //init root node is leaf node
        this.root = createNewRoot(false);

        File file = new File(homeDir);
        if (!file.exists()) file.mkdir();
        if (!file.isDirectory()) {
            throw new IllegalArgumentException("homeDir=" + homeDir + ", not file");
        }

        boolean isFindRoot = false;
        File[] files = file.listFiles();

        for (File tmp : files) {
            if (tmp.getName().equals("1")) {
                isFindRoot = this.root.readFromLog();
            }
        }
        if (!isFindRoot){
            root.writeToLog();
        }
    }

    public V search(K key) {
        return search(root, key);
    }

    private V search(BTreeNode<K, V> node, K key) {
        SearchResult<V> result = node.search(key);
        if (result.isSearchSuc()) {
            return node.getValue(result.getSearchIndex());
        }

        if (result.getSearchIndex() == -1) return null;

        if (node.isLeaf()) return null; //leaf no has child

        Integer childNodeId = node.getChild(result.getSearchIndex());
        BTreeNode<K, V> child = diskRead(childNodeId);
        if (child != null) {
            return search(child, key);
        }

        return null;
    }

    private BTreeNode<K, V> diskRead(Integer nodeId) {
        BTreeNode<K, V> bTreeNode = new BTreeNode<K, V>(false);
        bTreeNode.setNodeId(nodeId);
        bTreeNode.readFromLog();

        return bTreeNode;
    }

    public void insert(K k, V v) {
        //root split
        root = diskRead(root.getNodeId());
        if (root.keySize() == branchingFactory * 2 - 1) {
            BTreeNode<K, V> newRoot = createNewRoot(true); //no leaf
            BTreeNode<K, V> newNode = createNewNodeReferToOld(root.getNodeId());
            root.splitRootNode(newRoot, newNode);
            root = newRoot;
        }

        root = diskRead(root.getNodeId());
        insertNoFull(root, k, v);
    }

    /**
     * insert entry into leaf node
     *
     * @param node
     * @param key
     * @param value
     */
    private void insertNoFull(BTreeNode<K, V> node, K key, V value) {

        if (node.isLeaf()) {
            //direct insert
            node.dirInsert(key, value);
            return;
        }

        SearchResult<V> result = node.searchInsertChild(key);
        //visit child node
        Integer childNodeId = node.getChild(result.getSearchIndex());
        BTreeNode<K, V> child = diskRead(childNodeId);

        //split child node
        if (child.keySize() == branchingFactory * 2 - 1) {
            BTreeNode<K, V> newNode = createNewNodeReferToOld(
                    node.getChild(result.getSearchIndex()));
            node.splitChildNode(result.getSearchIndex(), newNode);

            //chose old child node or split child node
            //because childNode is left from node.key,node.key>=k
            //split new node key < node.key
            node = diskRead(node.getNodeId());
            result = node.searchInsertChild(key);
            childNodeId = node.getChild(result.getSearchIndex());
            child = diskRead(childNodeId);
        }

        insertNoFull(child, key, value);
    }

    public void delete(K key) {

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
    public BTreeNode<K, V> createNewNodeReferToOld(Integer oldNodeId) {
        BTreeNode<K, V> oldNode = diskRead(oldNodeId);

        BTreeNode<K, V> newNode = new BTreeNode<K, V>(oldNode.isLeaf());
        newNode.setNodeId(BTree.nodeIdGenerator.incrementAndGet());
        newNode.setKeyComparator(oldNode.getKeyComparator());
        return newNode;
    }

    /**
     * first root node is leaf node
     *
     * @param isHasChild
     * @return
     */
    public BTreeNode<K, V> createNewRoot(boolean isHasChild) {
        BTreeNode<K, V> root = new BTreeNode<K, V>(!isHasChild);
        root.setNodeId(BTree.nodeIdGenerator.incrementAndGet());
        root.setKeyComparator(keyComparator);
        return root;
    }

    public void setKeyComparator(ComparatorLoggable<K> keyComparator) {
        this.keyComparator = keyComparator;
    }

    public void setHomeDir(String homeDir) {
        this.homeDir = homeDir;
    }

    public void writeToLog() {
        root.writeToLog();
    }

    @Override
    public String toString() {
        return root.toString();
    }
}
