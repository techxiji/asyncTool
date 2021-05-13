package com.jd.platform.async.util.collection;

import java.util.Iterator;

/**
 * 一个反复循环的迭代器
 *
 * @author create by TcSnZh on 2021/5/9-下午6:25
 */
public interface WheelIterator<E> extends Iterator<E> {
    @Override
    E next();

    /**
     * 一轮的元素数
     */
    int cycle();

    @Override
    default boolean hasNext() {
        return cycle() > 0;
    }
}
