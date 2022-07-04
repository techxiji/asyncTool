package com.jd.platform.async.openutil.timer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author create by TcSnZh on 2021/5/12-下午6:36
 */
public abstract class AbstractWheelTimer implements Timer, AutoCloseable {
    public static final int WORKER_STATE_INIT = 0;
    public static final int WORKER_STATE_STARTED = 1;
    public static final int WORKER_STATE_SHUTDOWN = 2;

    protected final AtomicInteger workerState = new AtomicInteger(WORKER_STATE_INIT); // 0 - init, 1 - started, 2 - shut down

    public abstract void start();

    @SuppressWarnings("RedundantThrows")
    @Override
    public void close() throws Exception {
        stop();
    }

    protected boolean changeState(int workerStateStarted, int workerStateShutdown) {
        return workerState.compareAndSet(workerStateStarted, workerStateShutdown);
    }

}
