package v15.cases;

import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.callback.IWorker;
import com.jd.platform.async.executor.Async;
import com.jd.platform.async.worker.OnceWork;
import com.jd.platform.async.worker.WorkResult;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperBuilder;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * 示例：模拟线程池资源不够用的情况
 *
 * @author create by kyle
 */
class Case16 {

    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 20,
            30L, TimeUnit.MINUTES, new LinkedBlockingQueue<>(10),
            (r, e) -> {
                System.out.println("Task " + r.toString() +
                        " rejected from " +
                        e.toString());
                if (!e.isShutdown()) {
                    r.run();
                }
            });

    private static WorkerWrapperBuilder<?, ?> builder(String id) {

        return WorkerWrapper.<String, String>builder()
                .id(id)
                .param(UUID.randomUUID().toString())
                .worker(new MyWorker(id))
                .callback((new ICallback<String, String>() {
                    @Override
                    public void begin() {
                    }

                    @Override
                    public void result(boolean success, String param, WorkResult<String> workResult) {
                       /* System.out.println("(id=" + id + ") callback "
                                + (success ? "success " : "fail ")
                                + ", workResult is " + workResult);*/
                    }
                }))
                .allowInterrupt(true);
    }

    public static void main(String[] args) {
        //任务数量
        final int count = 40;
        //超时时间
        int timeout = 10000;
        List<OnceWork> workList = new ArrayList<>(count);
        //每个任务约23个节点
        for (int i = 0; i < count; i++) {
            WorkerWrapper<?, ?> a = builder("A").build();
            WorkerWrapper<?, ?> d;
            WorkerWrapper<?, ?> k;
            WorkerWrapper<?, ?> n;
            WorkerWrapper<?, ?> q;
            WorkerWrapper<?, ?> t;
            WorkerWrapper<?, ?> w;
            WorkerWrapper<?, ?> build = builder("H")
                    .depends(
                            builder("F")
                                    .depends(builder("B").depends(a).build())
                                    .depends(builder("C").depends(a).build())
                                    .build(),
                            builder("G")
                                    .depends(builder("E")
                                            .depends(d = builder("D").build())
                                            .build())
                                    .build(),
                            builder("I")
                                    .depends(builder("J")
                                            .depends(k = builder("K").build())
                                            .build())
                                    .build(),
                            builder("L")
                                    .depends(builder("M")
                                            .depends(n = builder("N").build())
                                            .build())
                                    .build(),
                            builder("O")
                                    .depends(builder("P")
                                            .depends(q = builder("Q").build())
                                            .build())
                                    .build(),
                            builder("R")
                                    .depends(builder("S")
                                            .depends(t = builder("T").build())
                                            .build())
                                    .build(),
                            builder("U")
                                    .depends(builder("V")
                                            .depends(w = builder("W").build())
                                            .build())
                                    .build()
                    )
                    .build();
            OnceWork work = Async.work(timeout, executor, a, d, k, n, q, t, w);
            workList.add(work);
        }

        while (true) {
            long finishCount = workList.stream().filter(OnceWork::isFinish).count();
            if (finishCount == count) {
                break;
            }
        }
        for (OnceWork work : workList) {
            try {
                System.out.println("cost:" + (work.getFinishTime() - work.getStartTime()));
            } catch (IllegalStateException e) {
            }
        }
        long cancelCount = workList.stream().filter(onceWork -> onceWork.isCancelled() || onceWork.isWaitingCancel()).count();
        long timeoutCount = workList.stream().filter(OnceWork::hasTimeout).count();
        long finishCount = workList.stream().filter(OnceWork::isFinish).count();

        System.out.println("取消数量" + cancelCount);
        System.out.println("超时数量" + timeoutCount);
        System.out.println("完成数量" + finishCount);

    }

    private static class MyWorker implements IWorker<String, String> {

        //用于存放模拟的对象，防止GC回收，用List做对象引用
        private final List<byte[]> list = new LinkedList<>();

        private final String id;

        private final int i = 0;

        public MyWorker(String id) {
            this.id = id;
        }

        @Override
        public String action(String param, Map<String, WorkerWrapper<?, ?>> allWrappers) {
            if ("F".equals(id)) {
                while (true) {
                    /*
                    第一种问题，内存溢出OOM，由系统取消任务执行，H的结果为{result=null, resultState=DEFAULT, ex=null}，因为没有跑到H，所以H的结果为null


                    取消成功，结束成功
                     */
                    /*byte[] buf = new byte[1024 * 1024];
                    list.add(buf);*/
                    /*
                    第二种问题，存在异常，H的结果为WorkResult{result=null, resultState=EXCEPTION, ex=java.lang.ArithmeticException: / by zero}


                    结束成功
                     */
                    /*if(i==20000){
                        int a=1/0;
                    }*/
                    /*
                    第三种问题，啥也不做就是等待，结果执行超时，WorkResult{result=null, resultState=TIMEOUT, ex=null}，AsyncTool会在超时时发出中断指令，停止运行


                    超时，结束成功
                     */
                    /*try {
                        TimeUnit.SECONDS.sleep(3);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        //如果将下面的语句注释，那么任务将永远不会结束
                        throw new RuntimeException("被中断");
                    }*/


                    //模拟有任务不退出的情况
                    System.out.println(param + " running");
                    Thread.yield();
                }
            }
            return id;
        }

    }

}

