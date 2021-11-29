package com.jd.platform.async.timewheel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 入口调度器
 *
 * @author wuweifeng
 * @version 1.0
 * @date 2021-11-16
 */
public class WheelMain {
    /**
     * 共20个槽位
     */
    public static final int MAX_SIZE = 20;
    /**
     * 总的队列，外层是20个槽位，内层是每个槽位内的任务集合
     * 注意，即便是这个List，一样会发生在遍历时，同时有数据新插入的情况。造成无法对level进行减1的操作
     */
    private static List<CopyOnWriteArrayList<SingleTask>> allTaskList = new ArrayList<>(MAX_SIZE);

    /**
     * 执行到的index
     */
    private static final AtomicInteger INDEX = new AtomicInteger(0);
    /**
     * 是否已经初始化过了，避免重复初始化
     */
    private static boolean hasInit = false;

    /**
     * 初始化
     */
    public static void init() {
        if (hasInit) {
            return;
        }
        for (int i = 0; i < MAX_SIZE; i++) {
            CopyOnWriteArrayList<SingleTask> list = new CopyOnWriteArrayList<>();
            allTaskList.add(list);
        }
        //单线程调度器，1ms执行一次。注意，当发生fullGc时，该定时器将不再能准确执行
        ScheduledExecutorService bossThreadPool = Executors.newSingleThreadScheduledExecutor();
        bossThreadPool.scheduleAtFixedRate(WheelMain::advanceClock, 0, 1, TimeUnit.MILLISECONDS);
        hasInit = true;
    }

    /**
     * 获取过期任务并执行
     * <p>
     * 这个方法作为定时任务，1毫秒一执行
     */
    private static void advanceClock() {
        try {
            int index = INDEX.get();
            if (index >= MAX_SIZE) {
                INDEX.set(index % MAX_SIZE);
            }
            //取当前走到的槽位
            CopyOnWriteArrayList<SingleTask> timerTaskList = allTaskList.get(index % 20);
            //索引前进一格
            INDEX.incrementAndGet();

            long currentTimeMillis = System.currentTimeMillis();

//            System.out.println("当前时间 " + currentTimeMillis);
            //遍历槽内所有任务
            for (SingleTask singleTask : timerTaskList) {
                //如果level = 0，代表时间到了，或者创建时间+超时时间>当前时间了，也是时间到了
                if (singleTask.getLevel() <= 0 || singleTask.getCurrentTime() + singleTask.getDelayMs() <= currentTimeMillis) {
                    //给予回调
                    singleTask.getTimeoutTask().timeoutCallback();
                    //从列表删除
                    timerTaskList.remove(singleTask);
                } else {
                    singleTask.setLevel(singleTask.getLevel() - 1);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 添加一个任务到时间轮的对应槽位
     */
    public synchronized static void addTask(ITimeoutTask timeoutTask) {
        SingleTask singleTask = SingleTask.build(timeoutTask);
        //获取超时时间
        int delay = timeoutTask.delayMs();
        if (delay <= 0) {
            return;
        }
        //放到第几个槽
        int putIndex = INDEX.get() + delay % MAX_SIZE;
        CopyOnWriteArrayList<SingleTask> list = allTaskList.get(putIndex % MAX_SIZE);
        //添加到该槽位的队列中
        list.add(singleTask);
    }


}
