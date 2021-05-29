package com.xck.breee;

import com.xck.btree.BTree;
import com.xck.btree.ComparatorLoggable;

import java.io.IOException;

public class SearchTest {

    public static void main(String[] args) throws IOException {
        BTree bTree = new BTree();
        bTree.setKeyComparator(new ComparatorLoggable<byte[]>() {
            public int compare(byte[] o1, byte[] o2) {
                for (int i=0; i<o1.length; i++){
                    if (o1[i] > o2[i]) return 1;
                    if (o1[i] < o2[i]) return -1;
                }
                return 0;
            }
        });
        bTree.createTree();

        long start = System.currentTimeMillis();
        for(int i=0; i<1000; i++){
            try {
//                byte[] key = new byte[]{((Integer)i).byteValue()};
                byte[] key = (""+i).getBytes();
                String value = new String(bTree.search(key));
//                System.out.println(i + " " + value);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("exception: " + i);
                break;
            }
        }
//        System.out.println(bTree);
        System.out.println("useTime: " + (System.currentTimeMillis()-start));
    }
}
