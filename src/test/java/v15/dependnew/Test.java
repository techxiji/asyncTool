package v15.dependnew;

import com.jd.platform.async.callback.IWorker;
import com.jd.platform.async.executor.Async;
import com.jd.platform.async.executor.timer.SystemClock;
import com.jd.platform.async.worker.ResultState;
import com.jd.platform.async.wrapper.actionstrategy.DependenceAction;
import com.jd.platform.async.wrapper.actionstrategy.DependenceStrategy;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperBuilder;
import com.jd.platform.async.wrapper.skipstrategy.SkipStrategy;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * @author create by TcSnZh on 2021/5/2-下午9:25
 */
class Test {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
//        ExecutorService pool = Executors.newFixedThreadPool(3);
        ExecutorService pool = Async.getCommonPool();
        try {
            testNew2(pool);
            System.out.println("\n\n\n");
            testNew1(pool);
            System.out.println("\n\n\n");
            testNew2(pool);
            System.out.println("\n\n\n");
            testThreadPolling_Speed(pool);
            System.out.println("\n\n\n");
            testThreadPolling_V14Bug();
        } finally {
            //Async.shutDownCommonPool();
            pool.shutdown();
        }
    }

    /**
     * 简简单单的测试一下新的编排方式
     * <p>
     * .A ===> B1 ===> C1  ----> D1
     * .   ||> B2 |    ||  \--> D2
     * .   ||> B3 |     ``========v
     * .   ||> B4 |---> C2 ====> E1
     * .                    \--> E2
     */
    private static void testNew1(ExecutorService pool) throws ExecutionException, InterruptedException {
        WorkerWrapper<Object, Object> a = builder("A")
                .build();
        WorkerWrapper<Object, Object> b1 = builder("B1").depends(a).build();
        WorkerWrapper<Object, Object> b2 = builder("B2").depends(a).build();
        WorkerWrapper<Object, Object> b3 = builder("B3").depends(a).build();
        WorkerWrapper<Object, Object> b4 = builder("B4").depends(a).build();
        WorkerWrapper<Object, Object> c1 = builder("C1")
                .depends(DependenceStrategy.ALL_DEPENDENCIES_ALL_SUCCESS, b1, b2, b3, b4)
                .nextOf(builder("D1").build(),
                        builder("D2").build())
                .build();
        WorkerWrapper<Object, Object> c2 = builder("C2")
                .depends(DependenceStrategy.ALL_DEPENDENCIES_ANY_SUCCESS, b1, b2, b3, b4)
                .nextOf(builder("E1").depends(DependenceStrategy.ALL_DEPENDENCIES_ALL_SUCCESS, c1).build(),
                        builder("E2").build())
                .build();
        Async.beginWork(2000, pool, a);
        logAll();
    }

    /**
     * 测试船新的编排方式的花里胡哨的玩法。
     * A => {B1 ~ B10} >>> C
     * <p>
     * C仅需要b1-b10中任意3个Worker工作完成即可启动。
     * （不过C不一定一定在3个完成后启动，具体还要看线程池属性与线程抢占的顺序，线程池线程数小一点的话更容易让C早日执行）
     * </p>
     */
    private static void testNew2(ExecutorService pool) throws ExecutionException, InterruptedException {
        WorkerWrapper<Object, Object> a = builder("A").build();
        ArrayList<WorkerWrapper> bList = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            bList.add(builder("B" + i).depends(a).build());
        }
        WorkerWrapper<Object, Object> c = builder("C")
                .setDepend().strategy((dependWrappers, thisWrapper, fromWrapper) -> {
                    if (dependWrappers.stream()
                            .filter(w -> w.getWorkResult().getResultState() == ResultState.SUCCESS).count() >= 3) {
                        return DependenceAction.START_WORK.emptyProperty();
                    } else {
                        return DependenceAction.TAKE_REST.emptyProperty();
                    }
                }).wrapper(bList).end().build();
        Async.beginWork(2000, pool, a);
        logAll();
    }

    /**
     * 测试线程轮询的效率
     */
    private static void testThreadPolling_Speed(ExecutorService pool) throws ExecutionException, InterruptedException {
        int MAX = 1000;
        Collection<WorkerWrapper<?, ?>> wrappers = new ArrayList<>(MAX);
        AtomicLong a = new AtomicLong(0);
        for (int i = 0; i < MAX; i++) {
            WorkerWrapperBuilder<Void, Void> builder = WorkerWrapper.<Void, Void>builder()
                    .id(String.valueOf(i))
                    // 拷贝数组测试，每次在数组最后加一个递增的值+1的数
                    .worker((object, allWrappers) -> {
                        for (int j = 0; j < 100000; j++) {
                            a.incrementAndGet();
                        }
                        return null;
                    })
                    .setSkipStrategy(SkipStrategy.NOT_SKIP);
            wrappers.add(builder.build());
        }
        long t1 = SystemClock.now();
        PrintStream out = Async.beginWork(10000, pool, wrappers) ? System.out : System.err;
        out.println("无依赖任务的测试：\n1000个wrapper对AtomicLong分别自增100000次，耗时 : " + (SystemClock.now() - t1) + "ms    a=" + a.get());
        WorkerWrapper.<Void, Integer>builder();
    }

    /**
     * 测试旧版本(v1.4及以前)中可能会引发线程耗尽bug的情况：
     * <p>
     * A(5ms)--B1(10ms) ---|--> C1(5ms)
     * .  \                |             (B1、B2全部完成可执行C1、C2)
     * .   ---> B2(20ms) --|--> C2(5ms)
     */
    private static void testThreadPolling_V14Bug() throws ExecutionException, InterruptedException {
        System.out.println("以下代码可复制到v1.4，复现线程耗尽bug : ");
        BiFunction<String, Long, IWorker<Void, Void>> sleepWork = (id, time) -> (IWorker<Void, Void>) (object, allWrappers) -> {
            try {
                System.out.println("wrapper.id=" + id + " before sleep");
                Thread.sleep(time);
                System.out.println("wrapper.id=" + id + " after sleep");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        };
        WorkerWrapper<Void, Void> a = new WorkerWrapper.Builder<Void, Void>()
                .id("A")
                .worker(sleepWork.apply("A", 5L))
                .build();
        WorkerWrapper.Builder<Void, Void> cBuilder = new WorkerWrapper.Builder<Void, Void>()
                .depend(new WorkerWrapper.Builder<Void, Void>()
                        .id("B1")
                        .worker(sleepWork.apply("B1", 10L))
                        .depend(a)
                        .build())
                .depend(new WorkerWrapper.Builder<Void, Void>()
                        .id("B2")
                        .worker(sleepWork.apply("B2", 10L))
                        .depend(a)
                        .build());
        cBuilder.id("C1").worker(sleepWork.apply("C1", 5L)).build();
        cBuilder.id("C2").worker(sleepWork.apply("C2", 5L)).build();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Async.beginWork(100, pool, a);
        } finally {
            pool.shutdown();
        }
        System.out.println(a.getNextWrappers());
    }

    // ========== util method ==========

    static final AtomicInteger count = new AtomicInteger(1);
    static final AtomicReference<ConcurrentHashMap<Integer, String>> logger = new AtomicReference<>(new ConcurrentHashMap<>());

    static WorkerWrapperBuilder<Object, Object> builder(String id) {
        return builder(id, -1);
    }

    static WorkerWrapperBuilder<Object, Object> builder(String id, long sleepTime) {
        return WorkerWrapper.builder()
                .id(id)
                .worker((param, allWrap) -> {
                    logger.get().put(count.getAndIncrement(), id + " working ");
                    if (sleepTime >= 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    return "I am success";
                }).callback((success, param, workResult) -> {
                    String str = "               " + id + " callback " + workResult.getResultState();
                    switch (workResult.getResultState()) {
                        case SUCCESS:
                            str += "   getResult = " + workResult.getResult();
                            break;
                        case TIMEOUT:
                        case EXCEPTION:
                            str += "   getEx     = " + workResult.getEx();
                            break;
                        case DEFAULT:
                            throw new RuntimeException();
                    }
                    logger.get().put(count.getAndIncrement(), str);
                });
    }

    static void logAll() {
        TreeMap<Integer, String> map = new TreeMap<>(Integer::compare);
        map.putAll(logger.get());
        StringBuilder sb = new StringBuilder(30);
        map.forEach((count, str) -> {
            sb.append('(').append(count).append(')');
            if (count < 10) {
                sb.append(' ');
            }
            sb.append("    ").append(str).append('\n');
        });
        System.out.println("--------------------------------\n" + sb);
        logger.set(new ConcurrentHashMap<>());
        count.set(1);
    }

}
