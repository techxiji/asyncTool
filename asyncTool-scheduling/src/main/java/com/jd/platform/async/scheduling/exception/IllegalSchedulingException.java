package com.jd.platform.async.scheduling.exception;

/**
 * @author tcsnzh[zh.jobs@foxmail.com] create this in 2021/5/19-下午7:20
 */
public class IllegalSchedulingException extends Exception{
    public IllegalSchedulingException() {
    }

    public IllegalSchedulingException(String message) {
        super(message);
    }

    public IllegalSchedulingException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalSchedulingException(Throwable cause) {
        super(cause);
    }

    public IllegalSchedulingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
