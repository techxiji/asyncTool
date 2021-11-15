package com.jd.platform.async.executor.wheel;

/**
 * 任务
 */
public class TimerTask {

    /**
     * 延迟时间
     */
    private long delayMs;

    /**
     * 任务
     */
    private Runnable task;

    /**
     * 时间槽
     */
    protected TimerTaskList timerTaskList;

    /**
     * 下一个节点
     */
    protected TimerTask next;

    /**
     * 上一个节点
     */
    protected TimerTask pre;
    /**
     * 创建时时间
     */
    protected long currentTime;

    /**
     * 描述
     */
    public String desc;
    /**
     * 设置当前时间标志位
     */
    private boolean flag = false;

    public TimerTask(long delayMs, Runnable task) {
        this.delayMs = delayMs;
        this.task = task;
        this.timerTaskList = null;
        this.next = null;
        this.pre = null;
    }

    public Runnable getTask() {
        return task;
    }

    public long getDelayMsAndCur() {
        return delayMs + currentTime;
    }

    public void setCurrentTime(long currentTime) {
        this.currentTime = currentTime;
        flag = true;
    }

    /**
     * 获取标志位
     */
    public boolean getFlag() {
        return flag;
    }

    @Override
    public String toString() {
        return desc;
    }
}
