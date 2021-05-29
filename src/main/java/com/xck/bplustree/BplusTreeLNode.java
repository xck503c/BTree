package com.xck.bplustree;

/**
 * B+ tree leaf node
 *
 * @author xuchengkun
 * @date 2021/05/26 09:08
 **/
public class BplusTreeLNode<K, V> extends BplusTreeNode<K, V>{

    BplusTreeNode<K, V> prevLeaf;
    BplusTreeLNode<K, V> nextLeaf;
    V[] datas;
}
