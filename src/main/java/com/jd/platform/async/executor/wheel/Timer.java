package com.jd.platform.async.executor.wheel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
     * 轮询delayQueue获取过期任务线程
     */
    private ScheduledExecutorService bossThreadPool;

    /**
     * 总的队列
     */
    private List<List<TimerTaskList>> delayList = new ArrayList<>(20);

    /**
     * 待执行的任务队列
     */
    private List<TimerTask> todoList = new ArrayList<>(16);
    /**
     * 执行到的index
     */
    private AtomicInteger INDEX = new AtomicInteger(0);

    /**
     * 构造函数
     */
    public Timer() {
        timeWheel = new TimeWheel(1, 20, System.currentTimeMillis(), delayList);
        //默认就1线程
        bossThreadPool = Executors.newSingleThreadScheduledExecutor();
        //20ms获取一次过期任务
        bossThreadPool.scheduleAtFixedRate(() -> {
            advanceClock();
        }, 0, 1, TimeUnit.MILLISECONDS);
    }

    /**
     * 添加任务
     */
    public void addTask(TimerTask timerTask) {
        //尝试添加任务
        if (!timeWheel.addTask(timerTask)) {
            //添加失败任务直接执行
            todoList.add(timerTask);
        }
    }

    /**
     * 获取过期任务并执行
     *
     * 这个方法作为定时任务，1毫秒一执行
     */
    protected void advanceClock() {
        try {
            //取20-list对应的时间槽
            List<TimerTaskList> timerTaskList = delayList.get(INDEX.getAndIncrement());
            //delayList一个时间槽里所有时间轮的对应槽，先清空再重新添加
            List<TimerTaskList> tmpList = new ArrayList<>(16);
            tmpList.addAll(timerTaskList);
            timerTaskList.clear();

            //遍历所有轮的槽，执行
            for (TimerTaskList singleWheelList: tmpList) {
                if (singleWheelList != null) {
                    //推进时间
                    timeWheel.advanceClock(singleWheelList.getExpiration());
                    //执行过期任务（包含降级操作）
                    //TODO 加到要执行的列表
                    singleWheelList.flush(this::addTask);
                    //TODO 执行
                    for (TimerTask task: todoList) {
                        task.getTask().run();
                    }
                    todoList.clear();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
