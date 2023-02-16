package v15.cases;

import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.callback.IWorker;
import com.jd.platform.async.executor.Async;
import com.jd.platform.async.executor.timer.SystemClock;
import com.jd.platform.async.worker.OnceWork;
import com.jd.platform.async.worker.WorkResult;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperBuilder;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 示例：模拟线程池资源不够用的情况
 *
 * @author create by kyle
 */
class Case16 {

    private static WorkerWrapperBuilder<?, ?> builder(String id) {

        return WorkerWrapper.<String, String>builder()
                .id(id)
                .param(id + "X")
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
        try {
            OnceWork work = Async.work(1000000, a, d, k, n, q, t, w);
            ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));

            pool.execute(() -> {
                while (true) {
                    try {
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

        private String id;

        private int i = 0;

        public MyWorker(String id) {
            this.id = id;
        }

        @Override
        public String action(String param, Map<String, WorkerWrapper<?, ?>> allWrappers) {
            try {
                TimeUnit.SECONDS.sleep(ThreadLocalRandom.current().nextInt(20));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return id;
        }

    }

}

