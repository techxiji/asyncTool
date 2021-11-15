package com.jd.platform.async.executor.wheel;

import com.jd.platform.async.wrapper.WorkerWrapper;

/**
 * 任务的超时检查
 * @author shenkaiwen5
 * @version 1.0
 * @date 2021-11-12
 */
public class TimeOutCheckMession implements Runnable {

    /**
     * 任务
     */
    private WorkerWrapper wrapper;

    /**
     * 状态
     */
    private static final int WORKING = 3;
    private static final int INIT = 0;

    public TimeOutCheckMession(WorkerWrapper wrapper) {
        this.wrapper = wrapper;
    }

    /**
     * 超时定义：
     *
     * 1.单个依赖： 当前任务超时，全链路超时
     * 2.多个依赖： 当前任务超时，
     */

    @Override
    public void run() {
        //超时时间已到，查看是否为INIT或RUN,是的话自身快速失败
        if (wrapper.getState() == INIT || wrapper.getState() == WORKING) {
            wrapper.fastFail(wrapper.getState(), null);
        }
    }
}
