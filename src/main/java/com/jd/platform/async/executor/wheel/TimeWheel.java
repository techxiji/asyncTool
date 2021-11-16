package com.jd.platform.async.executor.wheel;

import java.util.List;

/**
 * 时间轮
 */
public class TimeWheel {

    /**
     * 一个时间槽的范围
     */
    private long tickMs;

    /**
     * 时间轮大小
     */
    private int wheelSize;

    /**
     * 时间跨度
     */
    private long interval;

    /**
     * 时间槽
     */
    private TimerTaskList[] timerTaskLists;

    /**
     * 上层时间轮
     */
    private volatile TimeWheel overflowWheel;
    /**
     * 时间轮层数
     */
    private int overflowIndex;

    /**
     * 一个Timer只有一个delayQueue
     */
    private List<List<TimerTaskList>> delayList;

    public TimeWheel(long tickMs, int wheelSize, List<List<TimerTaskList>> delayList, int overflowIndex) {
        this.tickMs = tickMs;
        this.wheelSize = wheelSize;
        this.interval = tickMs * wheelSize;
        this.timerTaskLists = new TimerTaskList[wheelSize];
        this.delayList = delayList;
        for (int i = 0; i < wheelSize; i++) {
            timerTaskLists[i] = new TimerTaskList();
        }
        this.overflowIndex = overflowIndex;
    }

    /**
     * 创建或者获取上层时间轮
     */
    private TimeWheel getOverflowWheel() {
        if (overflowWheel == null) {
            synchronized (this) {
                if (overflowWheel == null) {
                    overflowWheel = new TimeWheel(interval, wheelSize, delayList, overflowIndex+1);
                }
            }
        }
        return overflowWheel;
    }

    /**
     * 添加任务到时间轮 (底层轮方法)
     */
    public boolean addTask(TimerTask timerTask) {
        //currentTime应该所有时间轮都一致
        long currentTime = System.currentTimeMillis();
        if (!timerTask.getFlag()) {
            timerTask.setCurrentTime(currentTime);
        }
        return addTask(timerTask, currentTime);
    }


    /**
     * 添加任务到时间轮
     */
    public boolean addTask(TimerTask timerTask, long currentTime) {
        long expiration = timerTask.getDelayMsAndCur();

        //过期任务直接执行
        if (expiration < currentTime + tickMs) {
            return false;
        } else if (expiration < currentTime + interval) {
            //当前时间轮可以容纳该任务 加入时间槽
            Long virtualId = expiration / tickMs;
            int index = (int) (virtualId % wheelSize);
            //System.out.println("tickMs:" + tickMs + "------index:" + index + "------expiration:" + expiration);
            TimerTaskList timerTaskList = timerTaskLists[index];
            timerTaskList.addTask(timerTask);

            //加到delayList对应元素  【20槽, 时间轮层数】,这里添加前一定要初始化到对应层数
            while (delayList.get(index).size() < overflowIndex + 1) {
                delayList.get(index).add(new TimerTaskList());
            }
            //这里每次清空整个槽位的List，并且flush List的元素链表，所以没问题
            delayList.get(index).set(overflowIndex, timerTaskList);

        } else {
            //放到上一层的时间轮
            TimeWheel timeWheel = getOverflowWheel();
            timeWheel.addTask(timerTask, currentTime);
        }
        return true;
    }
}
