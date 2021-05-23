package com.xck.breee;

import com.xck.btree.BTree;
import com.xck.btree.ComparatorLoggable;

public class SearchTest {

    public static void main(String[] args) {
        BTree<Integer, String> bTree = new BTree<Integer, String>();
        bTree.setKeyComparator(new ComparatorLoggable<Integer>() {
            public int compare(Integer o1, Integer o2) {
                if (o1 > o2) return 1;
                if (o1.equals(o2)) return 0;
                return -1;
            }
        });
        bTree.createTree();

        long start = System.currentTimeMillis();
        for(int i=0; i<1000; i++){
            try {
                if (!bTree.search(i).equals("10000000000")){
                    System.out.println(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("exception: " + i);
                break;
            }
        }
        System.out.println("useTime: " + (System.currentTimeMillis()-start));
        System.out.println(bTree);
    }
}
