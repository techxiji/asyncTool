package com.jd.platform.async.executor;

import com.jd.platform.async.openutil.timer.Timeout;
import com.jd.platform.async.openutil.timer.TimerTask;
import com.jd.platform.async.worker.OnceWork;

import java.util.concurrent.*;

public class ExecutorServiceWrapper {

    private final ExecutorService executorService;

    /**
     * 本次任务中所有线程提交
     */
    protected LinkedBlockingQueue<Future<?>> allThreadSubmit;

    public ExecutorServiceWrapper(ExecutorService executorService) {
        this.executorService = executorService;
        this.allThreadSubmit = new LinkedBlockingQueue<>();
    }

    public LinkedBlockingQueue<Future<?>> getAllThreadSubmit() {
        return allThreadSubmit;
    }

    public void addThreadSubmit(Async.TaskCallable callable) {
        allThreadSubmit.add(executorService.submit(callable));
    }

    public void startCheck(final OnceWork onceWork) {
        PollingCenter.getInstance().checkGroup(new ThreadCheckRunable(onceWork, this), 3000);
    }

    private static class ThreadCheckRunable implements TimerTask {

        private final OnceWork onceWork;

        private final ExecutorServiceWrapper executorServiceWrapper;

        public ThreadCheckRunable(OnceWork onceWork, ExecutorServiceWrapper executorServiceWrapper) {
            this.onceWork = onceWork;
            this.executorServiceWrapper = executorServiceWrapper;
        }

        @Override
        public void run(Timeout timeout) throws Exception {
            //任务结束就退出检查
            if (!onceWork.isFinish()) {
                if (executorServiceWrapper.getAllThreadSubmit().size() > 0) {
                    boolean isException = false;
                    boolean isCancelld = false;
                    boolean isDone = false;
                    for (Future<?> item : executorServiceWrapper.getAllThreadSubmit()) {
                        try {
                            //完成并且没有返回
                            if (item.isCancelled()) {
                                isCancelld = true;
                            }
                            if ((item.isDone() && item.get(500, TimeUnit.MILLISECONDS) == null)) {
                                isDone = true;
                            }
                        } catch (InterruptedException e) {
                            //中断等
                            e.printStackTrace();
                            System.out.println("出现中断" + e);
                            isException = true;
                        } catch (ExecutionException e) {
                            //内存溢出等
                            System.out.println("出现内存溢出等" + e);
                            e.printStackTrace();
                            isException = true;
                        } catch (TimeoutException e) {
                            //超时不管，继续检查
                        }
                    }

                    //异常或者有线程取消
                    if (isException || isCancelld) {
                        //未超时、未完成或者未取消就取消任务
                        while (!(onceWork.hasTimeout()
                                || onceWork.isFinish()
                                || onceWork.isCancelled()
                                || onceWork.isWaitingCancel())) {
                            onceWork.pleaseCancel();
                        }
                    } else {
                        if (isDone) {
                            System.out.println("部分任务已经在线程池完成");
                        }
                        //没有的话继续完成
                        onceWork.check();
                    }
                } else {
                    onceWork.check();
                }
            } else {
                System.out.println("任务已完成");
            }
        }

    }
}


