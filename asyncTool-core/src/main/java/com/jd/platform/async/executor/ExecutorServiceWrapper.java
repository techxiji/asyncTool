package com.jd.platform.async.executor;

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

    public void startCheck(final OnceWork.Impl onceWork) {
        executorService.execute(new ThreadCheckRunable(onceWork, this));
    }

    private static class ThreadCheckRunable implements Runnable {

        private final OnceWork.Impl onceWork;

        private final ExecutorServiceWrapper executorServiceWrapper;

        public ThreadCheckRunable(OnceWork.Impl onceWork, ExecutorServiceWrapper executorServiceWrapper) {
            this.onceWork = onceWork;
            this.executorServiceWrapper = executorServiceWrapper;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //任务结束就退出检查
                if (onceWork.isFinish()) {
                    break;
                } else if (executorServiceWrapper.getAllThreadSubmit().size() > 0) {
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
                            isException = true;
                        } catch (ExecutionException e) {
                            //内存溢出等
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
                        break;
                    } else {
                        if (isDone) {
                            System.out.println("部分任务已经在线程池完成");
                        }
                        //没有的话继续完成
                        onceWork.check();
                    }
                } else {
                    //FIXME 高强度检查会不会造成检查线程过多？
                    onceWork.check();
                }
            }
        }

    }

}
