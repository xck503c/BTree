package com.xck.btree;

import java.io.Serializable;
import java.util.Comparator;

/**
 * @Classname ComparatorLoggable
 * @Description
 * Serializable comparator
 * @Date 2021/5/21 17:05
 * @Created by xck503c
 */
public interface ComparatorLoggable<T> extends Serializable, Comparator<T> {
}
