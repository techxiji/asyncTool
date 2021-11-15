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
     * 轮询delayQueue获取过期任务线程
     */
    private ScheduledExecutorService bossThreadPool;

    /**
     * 总的队列
     */
    private List<List<TimerTaskList>> delayList = new ArrayList<>(20);
    /**
     * 执行到的index
     */
    private AtomicInteger INDEX = new AtomicInteger(0);

    /**
     * 构造函数
     */
    public Timer() {
        timeWheel = new TimeWheel(1, 20, delayList);
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
            //添加失败任务直接执行，反正判断超时的任务很快！
            timerTask.getTask().run();
        }
    }

    /**
     * 获取过期任务并执行
     *
     * 这个方法作为定时任务，1毫秒一执行
     */
    protected void advanceClock() {
        try {
            int index = INDEX.get();
            if (index >= 20) {
                INDEX.set(index % 20);
            }

            //取20-list对应的时间槽
            List<TimerTaskList> timerTaskList = delayList.get(index);
            //delayList一个时间槽里所有时间轮的对应槽，先清空再重新添加
            List<TimerTaskList> tmpList = new ArrayList<>(16);
            tmpList.addAll(timerTaskList);
            timerTaskList.clear();

            //遍历所有轮的槽，执行
            for (TimerTaskList singleWheelList: tmpList) {
                if (singleWheelList != null) {
                    //执行过期任务（包含降级操作）
                    //TODO 加到要执行的列表，并执行
                    singleWheelList.flush(this::addTask);
                }
            }
            INDEX.incrementAndGet();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
