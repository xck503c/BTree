package com.xck.bplustree;

/**
 * B+ tree internal node, no leaf node
 *
 * @author xuchengkun
 * @date 2021/05/26 09:07
 **/
public class BplusTreeINode<K, V> extends BplusTreeNode<K, V>{

    BplusTreeNode[] childs;
}
