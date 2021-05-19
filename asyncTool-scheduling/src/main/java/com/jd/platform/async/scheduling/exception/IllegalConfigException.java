package com.jd.platform.async.scheduling.exception;

/**
 * @author tcsnzh[zh.jobs@foxmail.com] create this in 2021/5/19-下午7:20
 */
public class IllegalConfigException extends Exception{
    public IllegalConfigException() {
    }

    public IllegalConfigException(String message) {
        super(message);
    }

    public IllegalConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalConfigException(Throwable cause) {
        super(cause);
    }

    public IllegalConfigException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
