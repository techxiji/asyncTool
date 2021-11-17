package com.jd.platform.async.executor.wheel;

import java.util.function.Consumer;

/**
 * 时间槽
 */
public class TimerTaskList {

    /**
     * 根节点
     */
    private TimerTask root = new TimerTask(-1L, null);

    {
        root.pre = root;
        root.next = root;
    }

    /**
     * 新增任务
     */
    public void addTask(TimerTask timerTask) {
        synchronized (this) {
            if (timerTask.timerTaskList == null) {
                timerTask.timerTaskList = this;
                TimerTask tail = root.pre;
                timerTask.next = root;
                timerTask.pre = tail;
                tail.next = timerTask;
                root.pre = timerTask;
            }
        }
    }

    /**
     * 移除任务
     */
    public void removeTask(TimerTask timerTask) {
        synchronized (this) {
            if (timerTask.timerTaskList.equals(this)) {
                timerTask.next.pre = timerTask.pre;
                timerTask.pre.next = timerTask.next;
                timerTask.timerTaskList = null;
                timerTask.next = null;
                timerTask.pre = null;
            }
        }
    }

    /**
     * 重新分配
     */
    public synchronized void flush(Consumer<TimerTask> flush) {
        TimerTask timerTask = root.next;
        while (!timerTask.equals(root)) {
            this.removeTask(timerTask);
            flush.accept(timerTask);
            timerTask = root.next;
        }
    }
}
