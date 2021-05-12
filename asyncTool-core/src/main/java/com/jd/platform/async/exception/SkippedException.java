package com.jd.platform.async.exception;

import com.jd.platform.async.executor.timer.SystemClock;

/**
 * 如果任务在执行之前，自己后面的任务已经执行完或正在被执行，则抛该exception
 *
 * @author wuweifeng wrote on 2020-02-18
 * @version 1.0
 */
public class SkippedException extends RuntimeException {
    private final long skipAt;

    public SkippedException() {
        this(null);
    }

    public SkippedException(String message) {
        this(message, SystemClock.now());
    }

    public SkippedException(String message, long skipAt) {
        super(message);
        this.skipAt = skipAt;
    }

    public long getSkipAt() {
        return skipAt;
    }
}
