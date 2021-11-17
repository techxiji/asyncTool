package com.jd.platform.async.executor.timeout;

/**
 * 单个任务，任务最小单元
 * @author wuweifeng
 * @version 1.0
 * @date 2021-11-16
 */
public class SingleTask {

    /**
     * 处于时间轮的第几圈，第0圈就代表本次要执行，第1圈代表下一次才执行自己
     */
    private int level;
    /**
     * 超时时间
     */
    private int delayMs;
    /**
     * 创建时时间
     */
    private long currentTime;
    /**
     * 可用于回调
     */
    private ITimeoutTask timeoutTask;

    /**
     * 构建该task
     */
    public static SingleTask build(ITimeoutTask timeoutTask) {
        SingleTask singleTask = new SingleTask();
        singleTask.delayMs = timeoutTask.delayMs();
        singleTask.currentTime = System.currentTimeMillis();
        singleTask.timeoutTask = timeoutTask;
        //计算在第几轮
        singleTask.level = singleTask.delayMs / WheelMain.MAX_SIZE;

        return singleTask;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public int getDelayMs() {
        return delayMs;
    }

    public long getCurrentTime() {
        return currentTime;
    }

    public ITimeoutTask getTimeoutTask() {
        return timeoutTask;
    }
}
