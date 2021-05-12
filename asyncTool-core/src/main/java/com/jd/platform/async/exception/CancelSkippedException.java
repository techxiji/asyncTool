package com.jd.platform.async.exception;

/**
 * 整组取消，设置该异常。
 *
 * @author tcsnzh[zh.jobs@foxmail.com] create this in 2021/5/25-下午6:12
 */
public class CancelSkippedException extends SkippedException {
    public CancelSkippedException() {
    }

    public CancelSkippedException(String message) {
        super(message);
    }

    public CancelSkippedException(String message, long skipAt) {
        super(message, skipAt);
    }
}
