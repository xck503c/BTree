package com.xck.btree;

/**
 * read and write itselft into byte
 */
public interface Loggable {

    int logSize();

    void writeToLog();

    boolean readFromLog();

    boolean isWrite();
}
