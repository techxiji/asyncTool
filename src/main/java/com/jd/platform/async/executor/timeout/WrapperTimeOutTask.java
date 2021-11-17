package com.jd.platform.async.executor.timeout;

import com.jd.platform.async.wrapper.WorkerWrapper;

/**
 * WoerWrappper超时任务
 * @author shenkaiwen5
 * @version 1.0
 * @date 2021-11-17
 */
public class WrapperTimeOutTask implements ITimeoutTask{

    /**
     * 任务
     */
    private WorkerWrapper wrapper;
    /**
     * 延迟时间
     */
    private int delayMs;

    /**
     * 构造
     */
    public WrapperTimeOutTask(WorkerWrapper wrapper, int delayMs) {
        this.wrapper = wrapper;
        this.delayMs = delayMs;
    }

    /**
     * 状态
     */
    private static final int WORKING = 3;
    private static final int INIT = 0;

    @Override
    public int delayMs() {
        return this.delayMs;
    }

    @Override
    public void timeoutCallback() {
        //超时时间已到，查看是否为INIT或RUN,是的话自身快速失败
        if (wrapper.getState() == INIT || wrapper.getState() == WORKING) {
            wrapper.fastFail(wrapper.getState(), null);
        }
    }
}
