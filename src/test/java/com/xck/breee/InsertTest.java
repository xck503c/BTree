package com.xck.breee;

import com.xck.btree.BTree;
import com.xck.btree.ComparatorLoggable;

import java.io.IOException;

public class InsertTest {

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

        try {
            long start = System.currentTimeMillis();
            long num = 10000000000L;
            for(int i=0; i<1000; i++){
                try {
                    long start1 = System.currentTimeMillis();
                    byte[] key = (""+i).getBytes();
                    byte[] value = (""+num).getBytes();
                    bTree.insert(key, value);
                    System.out.println("use:" + (System.currentTimeMillis()-start1));
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
            System.out.println("useTime: " + (System.currentTimeMillis()-start));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println(bTree);
            bTree.close();
        }
    }
}
