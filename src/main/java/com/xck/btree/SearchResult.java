package com.xck.btree;

/**
 * search B tree key result
 * @param <V>
 */
public class SearchResult<V> {

    private boolean isSearchSuc = false;

    private int searchIndex;

    public SearchResult(boolean isSearchSuc, int searchIndex) {
        this.isSearchSuc = isSearchSuc;
        this.searchIndex = searchIndex;
    }

    public boolean isSearchSuc() {
        return isSearchSuc;
    }

    public int getSearchIndex() {
        return searchIndex;
    }
}
