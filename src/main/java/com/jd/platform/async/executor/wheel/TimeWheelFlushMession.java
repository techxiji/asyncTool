package com.jd.platform.async.executor.wheel;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author shenkaiwen5
 * @version 1.0
 * @date 2021-11-15
 */
public class TimeWheelFlushMession implements Runnable {

    /**
     * 定时器
     */
    private Timer timer;

    public TimeWheelFlushMession(Timer timer) {
        this.timer = timer;
    }

    @Override
    public void run() {
        timer.advanceClock();
    }
}
