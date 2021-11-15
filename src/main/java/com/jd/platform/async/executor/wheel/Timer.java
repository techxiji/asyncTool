package com.jd.platform.async.executor.wheel;

import java.util.concurrent.*;

/**
 * 定时器
 */
public class Timer {

    /**
     * 底层时间轮
     */
    private TimeWheel timeWheel;

    /**
     * 一个Timer只有一个delayQueue
     */
    private DelayQueue<TimerTaskList> delayQueue = new DelayQueue<>();

    /**
     * 过期任务执行线程
     */
    private ExecutorService workerThreadPool;

    /**
     * 轮询delayQueue获取过期任务线程
     */
    private ExecutorService bossThreadPool;

    /**
     * 构造函数
     */
    public Timer() {
        timeWheel = new TimeWheel(1, 20, System.currentTimeMillis(), delayQueue);
//        workerThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2 - 1);
        workerThreadPool = Executors.newCachedThreadPool();
        bossThreadPool = Executors.newFixedThreadPool(1);
        //20ms获取一次过期任务
        bossThreadPool.submit(() -> {
            while (true) {
                this.advanceClock(20);
            }
        });
    }

    /**
     * 添加任务
     */
    public void addTask(TimerTask timerTask) {
        //添加失败任务直接执行
        if (!timeWheel.addTask(timerTask)) {
            //TODO 不要线程池，改成一个线程直接执行
            workerThreadPool.submit(timerTask.getTask());
        }
    }

    /**
     * 获取过期任务
     */
    private void advanceClock(long timeout) {
        try {
            //取20-list对应的时间槽
            TimerTaskList timerTaskList = delayQueue.poll(timeout, TimeUnit.MILLISECONDS);
            if (timerTaskList != null) {
                //推进时间
                timeWheel.advanceClock(timerTaskList.getExpiration());
                //执行过期任务（包含降级操作）
                //TODO 加到要执行的列表
                timerTaskList.flush(this::addTask);
                //TODO 执行。这个方法作为定时任务，1毫秒一执行
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
