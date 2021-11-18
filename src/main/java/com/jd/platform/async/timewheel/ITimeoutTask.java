package com.jd.platform.async.timewheel;

/**
 * 一个单一的任务
 * @author wuweifeng
 * @version 1.0
 * @date 2021-11-16
 */
public interface ITimeoutTask {
    /**
     * 超时时间
     */
    int delayMs();

    /**
     * 时间到了后的回调
     */
    void timeoutCallback();
}
