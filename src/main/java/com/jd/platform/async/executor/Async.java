package com.jd.platform.async.executor;


import com.jd.platform.async.callback.DefaultGroupCallback;
import com.jd.platform.async.callback.IGroupCallback;
import com.jd.platform.async.executor.timer.SystemClock;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperGroup;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 核心工具类。
 *
 * @author wuweifeng wrote on 2019-12-18
 * @version 1.0
 */
public class Async {

    // ========================= 任务执行核心代码 =========================

    /**
     * 出发点
     *
     * @return 只要执行未超时，就返回true。
     */
    public static boolean beginWork(long timeout,
                                    ExecutorService executorService,
                                    Collection<? extends WorkerWrapper<?, ?>> workerWrappers)
            throws InterruptedException {
        if (workerWrappers == null || workerWrappers.size() == 0) {
            return false;
        }
        //保存上次执行的线程池变量（为了兼容以前的旧功能）
        Async.lastExecutorService = Objects.requireNonNull(executorService, "ExecutorService is null ! ");
        WorkerWrapperGroup group = new WorkerWrapperGroup(SystemClock.now(), timeout);
        group.addWrapper(workerWrappers);
        workerWrappers.forEach(wrapper -> {
            if (wrapper == null) {
                return;
            }
            executorService.submit(() -> wrapper.work(executorService, timeout, group));
        });
        return group.awaitFinish();
        //处理超时的逻辑被移动到了WrapperEndingInspector中。
    }

    /**
     * 如果想自定义线程池，请传pool。不自定义的话，就走默认的COMMON_POOL
     */
    @SuppressWarnings("unchecked")
    public static boolean beginWork(long timeout, ExecutorService executorService, WorkerWrapper... workerWrapper)
            throws ExecutionException, InterruptedException {
        if (workerWrapper == null || workerWrapper.length == 0) {
            return false;
        }
        Set workerWrappers = Arrays.stream(workerWrapper).collect(Collectors.toSet());
        return beginWork(timeout, executorService, workerWrappers);
    }

    /**
     * 同步阻塞,直到所有都完成,或失败
     */
    public static boolean beginWork(long timeout, WorkerWrapper... workerWrapper) throws ExecutionException, InterruptedException {
        return beginWork(timeout, getCommonPool(), workerWrapper);
    }

    public static void beginWorkAsync(long timeout, IGroupCallback groupCallback, WorkerWrapper... workerWrapper) {
        beginWorkAsync(timeout, getCommonPool(), groupCallback, workerWrapper);
    }

    /**
     * 异步执行,直到所有都完成,或失败后，发起回调
     */
    public static void beginWorkAsync(long timeout, ExecutorService executorService, IGroupCallback groupCallback, WorkerWrapper... workerWrapper) {
        if (groupCallback == null) {
            groupCallback = new DefaultGroupCallback();
        }
        IGroupCallback finalGroupCallback = groupCallback;
        if (executorService != null) {
            executorService.submit(() -> {
                try {
                    boolean success = beginWork(timeout, executorService, workerWrapper);
                    if (success) {
                        finalGroupCallback.success(Arrays.asList(workerWrapper));
                    } else {
                        finalGroupCallback.failure(Arrays.asList(workerWrapper), new TimeoutException());
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                    finalGroupCallback.failure(Arrays.asList(workerWrapper), e);
                }
            });
        } else {
            final ExecutorService commonPool = getCommonPool();
            commonPool.submit(() -> {
                try {
                    boolean success = beginWork(timeout, commonPool, workerWrapper);
                    if (success) {
                        finalGroupCallback.success(Arrays.asList(workerWrapper));
                    } else {
                        finalGroupCallback.failure(Arrays.asList(workerWrapper), new TimeoutException());
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                    finalGroupCallback.failure(Arrays.asList(workerWrapper), e);
                }
            });
        }

    }

    // ========================= 设置/属性选项 =========================

    /**
     * 默认线程池。
     * <p>
     * 在v1.4及之前，该COMMON_POLL是被写死的。
     * <p>
     * 自v1.5后：
     * 该线程池会被懒加载。
     * 该线程池将会给线程取名为asyncTool-commonPool-thread-0（数字不重复）。
     * </p>
     */
    private static ThreadPoolExecutor COMMON_POOL;

    /**
     * 在以前（及现在）的版本中：
     * 当执行{@link #beginWork(long, ExecutorService, Collection)}方法时，ExecutorService将会被记录下来。
     * <p/>
     * 注意，这里是个static，也就是只能有一个线程池。用户自定义线程池时，也只能定义一个
     */
    private static volatile ExecutorService lastExecutorService;

    /**
     * 该方法将会返回{@link #COMMON_POOL}，如果还未初始化则会懒加载初始化后再返回。
     */
    public static ThreadPoolExecutor getCommonPool() {
        if (COMMON_POOL == null) {
            synchronized (Async.class) {
                if (COMMON_POOL == null) {
                    COMMON_POOL = new ThreadPoolExecutor(
                            Runtime.getRuntime().availableProcessors() * 2,
                            1024,
                            15L,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(),
                            new ThreadFactory() {
                                private final AtomicLong threadCount = new AtomicLong(0);

                                @Override
                                public Thread newThread(Runnable r) {
                                    Thread t = new Thread(r, "asyncTool-commonPool-thread-" + threadCount.getAndIncrement());
                                    t.setDaemon(true);
                                    return t;
                                }

                                @Override
                                public String toString() {
                                    return "asyncTool-commonPool-threadFactory";
                                }
                            }
                    ) {
                        @Override
                        public String toString() {
                            return "asyncTool-commonPool";
                        }
                    };
                }
            }
        }
        return COMMON_POOL;
    }

    public static String getThreadCount() {
        return "activeCount=" + COMMON_POOL.getActiveCount() +
                ",completedCount=" + COMMON_POOL.getCompletedTaskCount() +
                ",largestCount=" + COMMON_POOL.getLargestPoolSize();
    }

    /**
     * @param now 是否立即关闭
     * @throws IllegalStateException 如果尚未调用过{@link #getCommonPool()}，即没有使用过“使用默认线程池”的方法，该方法会抛出空指针异常。
     */
    public static synchronized void shutDownCommonPool(boolean now) {
        if (COMMON_POOL == null) {
            throw new IllegalStateException("COMMON_POOL Not initialized yet");
        }
        if (!COMMON_POOL.isShutdown()) {
            if (now) {
                COMMON_POOL.shutdownNow();
            } else {
                COMMON_POOL.shutdown();
            }
        }
    }

    /**
     * 关闭上次使用的线程池
     *
     * @deprecated 因此在v1.5时加上了废弃注解。
     * <p>
     * 这是一个很迷的方法，多线程时调用该方法的{@link #lastExecutorService}可能会被别的线程修改而引发不必要、不可控的错误。仅建议用来测试。
     * 另外，该方法现在不会关闭默认线程池。
     * </p>
     */
    @Deprecated
    public static void shutDown() {
        if (lastExecutorService != COMMON_POOL) {
            shutDown(lastExecutorService);
        }
    }

    /**
     * 关闭指定的线程池
     *
     * @param executorService 指定的线程池。传入null则会关闭默认线程池。
     * @deprecated 没啥用的方法，要关闭线程池还不如直接调用线程池的关闭方法，避免歧义。
     */
    @Deprecated
    public static void shutDown(ExecutorService executorService) {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
