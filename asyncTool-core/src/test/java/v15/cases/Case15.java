package v15.cases;

import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.callback.IWorker;
import com.jd.platform.async.executor.Async;
import com.jd.platform.async.executor.timer.SystemClock;
import com.jd.platform.async.worker.OnceWork;
import com.jd.platform.async.worker.WorkResult;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperBuilder;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 示例：模拟内存溢出
 * <p>
 * 运行内存溢出之前请设置
 * -Xmx20m -Xms20m
 * <p>
 * 当内存溢出时，其中一个线程会OOM，runable不会继续调度，
 * 我通过添加一个线程主动cancel来达到提前结束任务而不是等超时
 *
 * @author create by kyle
 */
class Case15 {

    private static WorkerWrapperBuilder<?, ?> builder(String id) {

        return WorkerWrapper.<String, String>builder()
                .id(id)
                .param(UUID.randomUUID().toString())
                .worker(new MyWorker(id))
                .callback((new ICallback<String, String>() {
                    @Override
                    public void begin() {
                        System.out.println("wrapper(id=" + id + ") has begin . ");
                    }

                    @Override
                    public void result(boolean success, String param, WorkResult<String> workResult) {
                        System.out.println("\t\twrapper(id=" + id + ") callback "
                                + (success ? "success " : "fail ")
                                + ", workResult is " + workResult);
                    }
                }))
                .allowInterrupt(true);
    }

    public static void main(String[] args) {
        long now = SystemClock.now();
        WorkerWrapper<?, ?> a = builder("A").build();
        WorkerWrapper<?, ?> d;
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
                                .build()
                )
                .build();
        try {
            ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100),
                    new RejectedExecutionHandler() {
                        @Override
                        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                            throw new RejectedExecutionException("Task " + r.toString() +
                                    " rejectexxxxd from " +
                                    e.toString());
                        }
                    });
            OnceWork work = Async.work(10000, executor, a, d);
            ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));

            pool.execute(() -> {
                while (true) {
                    try {
                        if (work.hasTimeout()) {
                            System.out.println("超时");
                        }
                        if (work.isCancelled()) {
                            System.out.println("取消成功");
                        }
                        if (work.isFinish()) {
                            //注意，这里的结果和“输出H节点的结果----”位置处的不一致，这是多线程写造成的
                            System.out.println("结束成功" + build.getWorkResult());
                            break;
                        }
                        TimeUnit.MILLISECONDS.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            work.awaitFinish();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("cost:" + (SystemClock.now() - now));
        int count = 1;
        while (build.getWorkResult().getEx() == null) {
            //同步等待result数据写入
            if (count++ > 800) {
                break;
            }
        }
        System.out.println("输出H节点的结果----" + build.getWorkResult());
        /* 输出:
        wrapper(id=D) is working
        wrapper(id=A) is working
        wrapper(id=E) is working
        wrapper(id=B) is working
        wrapper(id=C) is working
        wrapper(id=G) is working
        wrapper(id=F) is working
        wrapper(id=H) is working
        */
    }

    private static class MyWorker implements IWorker<String, String> {

        //用于存放模拟的对象，防止GC回收，用List做对象引用
        private final List<byte[]> list = new LinkedList<>();

        private final String id;

        private int i = 0;

        public MyWorker(String id) {
            this.id = id;
        }

        @Override
        public String action(String param, Map<String, WorkerWrapper<?, ?>> allWrappers) {
            if ("F".equals(id)) {
                while (true) {
                    System.out.println("wrapper(id=" + id + ") is working");
                    System.out.println("I am alive：" + i++);
                    /*
                    第一种问题，内存溢出OOM，由系统取消任务执行，H的结果为{result=null, resultState=DEFAULT, ex=null}，因为没有跑到H，所以H的结果为null


                    取消成功，结束成功
                     */
                    byte[] buf = new byte[1024 * 1024];
                    list.add(buf);
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
                }
            }
            if ("H".equals(id)) {
                /**
                 * 最后一个节点是否会被回调
                 *
                 * 第一种问题下不会回调
                 * 第二种问题下不会回调
                 * 第三种问题下不会回调
                 */
                System.out.println("H被回调");
            }
            return id;
        }

    }

}

