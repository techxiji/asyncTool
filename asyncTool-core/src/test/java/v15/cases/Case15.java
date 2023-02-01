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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 示例：模拟内存溢出
 * <p>
 * 运行之前请设置
 * -Xmx20m -Xms20m
 *
 * 当内存溢出时，其中一个线程会OOM，runable不会继续调度，
 * 我通过添加一个线程主动cancel来达到提前结束任务而不是等超时
 *
 * @author create by kyle
 */
class Case15 {

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
            OnceWork work = Async.work(5000, a, d);
            ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));

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
        while (build.getWorkResult().getEx() == null) {
            //同步等待result数据写入
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

        private String id;

        private int i = 0;

        public MyWorker(String id) {
            this.id = id;
        }

        @Override
        public String action(String param, Map<String, WorkerWrapper<?, ?>> allWrappers) {
            if ("F".equals(id)) {
                System.out.println("wrapper(id=" + id + ") is working");
                while (true) {
                    System.out.println("I am alive：" + i++);
                    byte[] buf = new byte[1024 * 1024];
                    list.add(buf);
                }
            }
            return id;
        }

    }

}

