package com.jd.platform.async.openutil.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 范围锁 todo
 *
 * @author create by TcSnZh on 2021/5/15-下午6:23
 */
public interface RangeLock extends Lock {
    void lock(int start, int end);

    boolean tryLock(int start, int end);

    boolean tryLock(int start, int end, long time, TimeUnit unit) throws InterruptedException;

    void lockInterruptibly(int start, int end) throws InterruptedException;

    @Override
    Condition newCondition();

    @Override
    void unlock();

    @Override
    default void lock() {
        lock(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Override
    default void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Override
    default boolean tryLock() {
        return tryLock(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Override
    default boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return tryLock(Integer.MIN_VALUE, Integer.MAX_VALUE, time, unit);
    }
}
