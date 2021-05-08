package com.jd.platform.async.wrapper;

import com.jd.platform.async.executor.timer.SystemClock;
import com.jd.platform.async.worker.WorkResult;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;


/**
 * 判断{@link WorkerWrapper}是否链路调用完成的轮询器。
 * =================================================================================
 * <p>
 * 在v1.4及以前的版本，存在如下问题：
 * >
 * 在使用线程数量较少的线程池进行beginWork时，调用WorkerWrapper#beginNext方法时，
 * 会因为本线程等待下游Wrapper执行完成而存在线程耗尽bug。线程池会死翘翘的僵住、动弹不得。
 * >
 * 例如仅有2个线程的线程池，执行以下任务：
 * {@code
 * <p>
 * 这是旧版本(v1.4及以前)中可能会引发线程耗尽bug的情况，在test/v15.dependnew中示例testThreadPolling_V14Bug说明了这个bug
 * 线程数：2
 * A(5ms)--B1(10ms) ---|--> C1(5ms)
 * .  \                |             (B1、B2全部完成可执行C1、C2)
 * .   ---> B2(20ms) --|--> C2(5ms)
 * <p>
 * }
 * 线程1执行了A，然后在{@link java.util.concurrent.CompletableFuture#allOf(CompletableFuture[])}等待B1与B2执行完成。
 * 线程2执行了B1或B2中的一个，也在allOf方法等待C1、C2完成。
 * 结果没有线程执行C和B2了，导致超时而死，并且这个线程池线程有可能被耗尽。
 * >
 * v1.5的解决方案是，放弃使工作线程遭致阻塞的{@link java.util.concurrent.CompletableFuture}，
 * 而是让工作线程在工作前注册到本“完成检查器”{@link WrapperEndingInspector}，然后交由轮询中心{@link PollingCenter}进行检查是否完成。
 * </p>
 * =================================================================================
 * <p>
 * 本类的工作原理：
 * .
 * 原理：
 * (1)首先在Async代码中，将主动运行的wrapper都保存到一个inspector{@link #addWrapper(WorkerWrapper)}，
 * (2)主动运行的wrapper于FINISH/ERROR时，先异步submit所有下游wrapper，在其执行时将自身(下游wrapper)保存到inspector，
 * (3)然后在异步submit完所有下游wrapper后，将调用{@link #setWrapperEndWithTryPolling(WorkerWrapper)}方法，
 * .  设置自己的{@link #wrappers}为true，并呼叫轮询{@link PollingCenter#tryPolling()}。
 * (4)在下游wrapper中，经过策略器判断后，
 * .  若是不需要运行，则把本wrapper计数-1{@link WrapperNode#count}，若是计数<1则将{@link WrapperNode}移出{@link #wrappers}。
 * .  若是需要运行，则运行之，然后跳转到 (2) 的情节。如此递归，执行链路上所有需要执行的wrapper最后都会存在于{@link #wrappers}中。
 * .
 * 因此，若是存在任一其{@link WrapperNode#called}为false的wrapper，则表示这条链路还没有调用完。
 * 若是在{@link #wrappers}中所有的{@link WrapperNode#called}为true时，即可判断出链路执行完毕了。
 * </p>
 *
 * @author create by TcSnZh on 2021/5/5-下午3:22
 */
public class WrapperEndingInspector implements Comparable<WrapperEndingInspector> {
    /**
     * 最迟完成时间
     */
    private final long latestFinishTime;

    /**
     * 保存 需要检查的wrapper--相关属性 的Map。
     */
    private final ConcurrentHashMap<WorkerWrapper, WrapperNode> wrappers = new ConcurrentHashMap<>();

    /**
     * 当全部wrapper都调用结束，它会countDown
     */
    private final CountDownLatch endCDL = new CountDownLatch(1);

    /**
     * 读锁用于修改数据，写锁用于轮询。使用公平锁让wrapper的时间波动不会太长。
     * <p/>
     * 在轮询到本inspector时，之所以要上写锁，是因为：
     * 假如此时有个Wrapper正在调用{@link #addWrapper(WorkerWrapper)}，则wrappers发生了改变。
     * 假如现在恰巧访问到的是{@link #wrappers}迭代器的最后一个，但此时又加入了另一个，且这另一个又是需要去执行的。
     * 那么假如在迭代器遍历到目前访问到的wrapper都是呼叫完毕的，那么这新加入的一个就会被忽略，从而判定为全部完成。致使bug发生。
     * <p/>
     * 此外，即便轮询时上写锁，对性能的影响也是有限的。因为这只会在“呼叫别人”的时候发生工作线程与轮询线程的锁争抢，
     * 而在工作线程执行{@link com.jd.platform.async.callback.IWorker#action(Object, Map)}或
     * {@link com.jd.platform.async.callback.ICallback#result(boolean, Object, WorkResult)}时，并不会与轮询线程去
     * 争抢锁，而通常这个工作的时间才是最耗时的。
     */
    private final ReentrantReadWriteLock modifyPollingLock = new ReentrantReadWriteLock(true);

    /**
     * 当轮询发现超时时，该值被设为false
     */
    private final AtomicBoolean haveNotTimeOut = new AtomicBoolean(true);

    public WrapperEndingInspector(long latestFinishTime) {
        this.latestFinishTime = latestFinishTime;
    }

    public void registerToPollingCenter() {
        modifyPollingLock.readLock().lock();
        try {
            // 不重复put，以免InspectorNode被替换为另一个
            PollingCenter.getInstance().inspectionMap.putIfAbsent(this, new PollingCenter.InspectorNode());
        } finally {
            modifyPollingLock.readLock().unlock();
        }
    }

    public void addWrapper(WorkerWrapper wrapper) {
        modifyPollingLock.readLock().lock();
        try {
            wrappers.computeIfAbsent(wrapper, k -> new WrapperNode()).count.incrementAndGet();
        } finally {
            modifyPollingLock.readLock().unlock();
        }
    }

    public void addWrapper(Collection<? extends WorkerWrapper> wrappers) {
        modifyPollingLock.readLock().lock();
        try {
            Objects.requireNonNull(wrappers).forEach(this::addWrapper);
        } finally {
            modifyPollingLock.readLock().unlock();
        }
    }

    public void reduceWrapper(WorkerWrapper wrapper) {
        modifyPollingLock.readLock().lock();
        try {
            /*
             * 有可能发生这情况，一个Wrapper刚被加进去，执行了零/一/多次，均不满足执行条件，但是下次调用却应当使其启动。
             */
            if (wrapper.getState() != WorkerWrapper.INIT) {
                final WrapperNode wrapperNode = wrappers.get(wrapper);
                if (wrapperNode == null) {
                    return;
                }
                synchronized (wrapperNode) {
                    if (wrapperNode.count.decrementAndGet() < 1) {
                        wrappers.remove(wrapper);
                    }
                }
            }
        } finally {
            modifyPollingLock.readLock().unlock();
        }
    }

    /**
     * 原子的设置这个Wrapper已经呼叫完成了。
     * <p/>
     * 该方法会调用{@link PollingCenter#tryPolling()}，呼叫轮询线程
     *
     * @return 如果为true，表示设置成功。为false表示已经被设置过了。
     */
    public boolean setWrapperEndWithTryPolling(WorkerWrapper wrapper) {
        modifyPollingLock.readLock().lock();
        try {
            return !wrappers.get(wrapper).called.getAndSet(true);
        } finally {
            modifyPollingLock.readLock().unlock();
            PollingCenter.getInstance().tryPolling();
        }
    }

    /**
     * 供外部调用的等待方法
     *
     * @return 在超时前完成，返回true。超时时间一到，就会返回false。就像，人被杀，就会死。
     * @throws InterruptedException 外部调用的当前线程被中断时，会抛出这个异常。
     */
    public boolean await() throws InterruptedException {
        endCDL.await();
        return haveNotTimeOut.get();
    }

    /**
     * {@link PollingCenter}会优先把最迟完成时间（即开始时间+超时时间）较早的Inspection放在前面。
     */
    @Override
    public int compareTo(WrapperEndingInspector other) {
        if (this.latestFinishTime - other.latestFinishTime < 0) {
            return -1;
        }
        return 1;
    }

    @Override
    public String toString() {
        return "WrapperEndingInspector{" +
                "remainTime=" + (latestFinishTime - SystemClock.now()) +
                ", wrappers=" +
                wrappers.entrySet().stream()
                        .collect(Collectors.toMap(entry -> entry.getKey().getId(), Map.Entry::getValue))
                +
                ", endCDL.getCount()=" + endCDL.getCount() +
                ", writePollingLock={read=" + modifyPollingLock.getReadLockCount() + ",write=" + modifyPollingLock.getWriteHoldCount() +
                "} }";
    }

    /**
     * 节点对象，保存属性信息于{@link #wrappers}中。
     * <p/>
     * 当试图把Node移出本Map时，该Node对象自身将会被上锁。
     */
    public static class WrapperNode {
        /**
         * 是否已经呼叫完了下游wrapper
         */
        AtomicBoolean called = new AtomicBoolean(false);
        /**
         * 本wrapper总共被呼叫次数的统计。若小于1则会被移出map。
         */
        AtomicInteger count = new AtomicInteger(0);

        @Override
        public String toString() {
            return "{" +
                    "called=" + called.get() +
                    ", count=" + count.get() +
                    '}';
        }
    }

    /**
     * 轮询中心。具体的轮询调度由其完成。
     * <p/>
     * {@link #registerToPollingCenter()}调用时，就会将inspector注册到本轮询中心以供轮询。
     */
    public static class PollingCenter {
        public static class InspectorNode {
            /**
             * 延迟轮询时间戳。
             */
            private volatile long delayTimeStamp = Long.MAX_VALUE;

            private final ReadWriteLock lockOfDelayTimeStamp = new ReentrantReadWriteLock();

            /**
             * 比较传入时间戳与{@link #delayTimeStamp}，并设置小的那个为{@link #delayTimeStamp}的值。
             *
             * @param otherDelayTimeStamp 试图用来比较的另一个时间戳
             */
            public void compareAndSetMinDelayTimeStamp(long otherDelayTimeStamp) {
                lockOfDelayTimeStamp.writeLock().lock();
                try {
                    long dif = otherDelayTimeStamp - delayTimeStamp;
                    if (dif > 0) {
                        return;
                    }
                    delayTimeStamp = otherDelayTimeStamp;
                } finally {
                    lockOfDelayTimeStamp.writeLock().unlock();
                }
            }

            public long getDelayTimeStamp() {
                lockOfDelayTimeStamp.readLock().lock();
                try {
                    return delayTimeStamp;
                } finally {
                    lockOfDelayTimeStamp.readLock().unlock();
                }
            }

            public long clearTimeStamp() {
                lockOfDelayTimeStamp.writeLock().lock();
                try {
                    long old = this.delayTimeStamp;
                    delayTimeStamp = Long.MAX_VALUE;
                    return old;
                } finally {
                    lockOfDelayTimeStamp.writeLock().unlock();
                }
            }

            @Override
            public String toString() {
                return "InspectorNode{" +
                        "delayTimeStamp=" + delayTimeStamp +
                        ", lockOfDelayTimeStamp=" + lockOfDelayTimeStamp +
                        '}';
            }
        }

        /**
         * 将被轮询的WrapperFinishInspection集合。
         */
        private final Map<WrapperEndingInspector, InspectorNode> inspectionMap = new ConcurrentSkipListMap<>();

        /**
         * 请求轮询。
         */
        private void tryPolling() {
            // 开始轮询
            SINGLETON_POLLING_POOL.submit(() -> {
                // 用来判断在轮询过程中是否有新增的inspector的值
                int expectCount;
                // 如果此值变化过，则在结束时让自己在此值后的时间再启动轮询
                while (!inspectionMap.isEmpty()) {
                    // expectCount是本线程用来记录本次循环开始时inspectionMap的个数。
                    // 每当移出一个inspector时，该值-1。
                    expectCount = inspectionMap.size();
                    // 开始检查
                    for (Map.Entry<WrapperEndingInspector, InspectorNode> entry : inspectionMap.entrySet()) {
                        final WrapperEndingInspector inspector = entry.getKey();
                        final InspectorNode inspectorNode = entry.getValue();
                        // 直接抢锁，轮询期间禁止修改inspector
                        inspector.modifyPollingLock.writeLock().lock();
                        try {
                            // 对一个inspector进行检查
                            if (PollingCenter.this.checkInspectorIsEnd(inspector, inspectorNode)) {
                                // inspector中的wrapper调用结束了
                                // 先要把wrapper给停了
                                inspector.wrappers.forEach((wrapper, wrapperNode) -> {
                                    WorkerWrapper.TimeOutProperties timeOut = wrapper.getTimeOut();
                                    if (timeOut != null) {
                                        timeOut.checkTimeOut(true);
                                    }
                                });
                                // 修改此inspector和expectCount的状态
                                if (inspector.endCDL.getCount() > 0) {
                                    // 双重检查使endCDL原子性countDown。
                                    synchronized (inspector.endCDL) {
                                        if (inspector.endCDL.getCount() > 0) {
                                            inspectionMap.remove(inspector);
                                            expectCount--;
                                            inspector.endCDL.countDown();
                                        }
                                    }
                                }
                            }
                        } finally {
                            inspector.modifyPollingLock.writeLock().unlock();
                        }
                    }
                    /*
                     * 根据 expectCount == inspectionMap.size() 的值，在仅有本线程1个线程在轮询的情况下：
                     * 1. 若值为true，表示轮询过程中没有新的inspector被添加进set中。此时就可以break了。
                     * .  之所以可以break，是因为这个inspection还没有调用结束，在其结束前还会来催促轮询的。
                     * 2. 若值为false，表示有新的inspector在本线程轮询时，被加入到了set中，且没有被我们迭代到。此时还要重新轮询一次。
                     */
                    if (expectCount == inspectionMap.size()) {
                        break;
                    }
                }
            });
        }

        private boolean checkInspectorIsEnd(WrapperEndingInspector inspector, InspectorNode inspectorNode) {
            // 判断一下inspector整组是否超时
            if (inspector.latestFinishTime < SystemClock.now()) {
                inspector.haveNotTimeOut.set(false);
                inspector.wrappers.forEach(((wrapper, wrapperNode) -> {
                    wrapper.failNow();
                    wrapperNode.called.set(true);
                }));
                return true;
            }
            // 将延迟检查时间设为离现在最近的值。
            // 此处判断的是inspector所代表整次任务的超时时间
            inspectorNode.compareAndSetMinDelayTimeStamp(inspector.latestFinishTime);
            // 判断inspector是否结束，并顺便记录、判断、修改wrapper的超时信息
            for (Map.Entry<WorkerWrapper, WrapperNode> entry : inspector.wrappers.entrySet()) {
                WorkerWrapper wrapper = entry.getKey();
                // 判断单个wrapper是否超时
                WorkerWrapper.TimeOutProperties timeOutProperties = wrapper.getTimeOut();
                if (timeOutProperties != null && timeOutProperties.isEnable()) {
                    // 将延迟检查时间设为离现在最近的值。
                    // 此处判断的是wrapper的超时时间
                    if (timeOutProperties.checkTimeOut(true)) {
                        inspector.haveNotTimeOut.set(false);
                    }
                    // 未超时但是设置了超时检查的话，记录一下inspector延时轮询时间
                    else {
                        inspectorNode.compareAndSetMinDelayTimeStamp(
                                (timeOutProperties.isStarted() ? timeOutProperties.getStartWorkingTime() : SystemClock.now())
                                        + timeOutProperties.getUnit().toMillis(timeOutProperties.getTime())
                        );
                    }
                }
                // 判断wrapper是否执行完毕
                WrapperNode node = entry.getValue();
                if (wrapper.getState() == WorkerWrapper.INIT
                        // 上值如果为false，表示该Wrapper要么还没来得及执行，要么判断不需要执行但是还未被移出
                        || !node.called.get()
                    // 上值如果为false，表示该Wrapper正在工作或是刚刚结束/失败，还未将所有下游Wrapper调用一遍。
                ) {
                    return false;
                }
            }
            return true;
        }

        {
            final String executorName = "asyncTool-pollingDelayCaller";
            ScheduledThreadPoolExecutor delayPollingExecutor = new ScheduledThreadPoolExecutor(
                    1,
                    new ThreadFactory() {
                        private final AtomicLong threadCount = new AtomicLong(0);

                        @Override
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, executorName + "-thread-" + threadCount.getAndIncrement());
                            t.setDaemon(true);
                            // 线程优先级不高
                            t.setPriority(1);
                            return t;
                        }

                        @Override
                        public String toString() {
                            return executorName + "-threadFactory";
                        }
                    }
            ) {
                @Override
                public String toString() {
                    return executorName + "{PollingCenter.this=" + PollingCenter.this + "}";
                }
            };
            // 每毫秒判断一次：map.value的每个延迟轮询队列的头号元素是否抵达当前时间，如果到了，则清除并调用轮询
            delayPollingExecutor.scheduleAtFixedRate(() -> inspectionMap.values().stream()
                    .min(Comparator.comparingLong(InspectorNode::getDelayTimeStamp))
                    .ifPresent(node -> {
                        long delayTimeStamp = node.getDelayTimeStamp();
                        if (Long.MAX_VALUE != delayTimeStamp && SystemClock.now() > delayTimeStamp) {
                            tryPolling();
                        }
                    }), 1, 1, TimeUnit.MILLISECONDS);
        }

        // ========== static ==========

        private final static PollingCenter instance = new PollingCenter();

        public static PollingCenter getInstance() {
            return instance;
        }

        /**
         * 单线程的轮询线程池
         */
        private static final ThreadPoolExecutor SINGLETON_POLLING_POOL;

        static {
            SINGLETON_POLLING_POOL = new ThreadPoolExecutor(
                    0,
                    // 轮询线程数必须为1
                    1,
                    15L,
                    TimeUnit.SECONDS,
                    // 必须保存至少一个轮询请求，以便在本线程轮询结束时，获取到已轮询过的线程提交的轮询请求
                    new ArrayBlockingQueue<>(1),
                    new ThreadFactory() {
                        private final AtomicLong threadCount = new AtomicLong(0);

                        @Override
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "asyncTool-pollingCenterPool-thread-" + threadCount.getAndIncrement());
                            t.setDaemon(true);
                            // 线程优先级不高
                            t.setPriority(3);
                            return t;
                        }

                        @Override
                        public String toString() {
                            return "asyncTool-pollingCenterPool-threadFactory";
                        }
                    },
                    // 多的就丢了，反正都是催这一个线程去轮询
                    new ThreadPoolExecutor.DiscardPolicy()
            ) {
                @Override
                public String toString() {
                    return "asyncTool-pollingCenterPool";
                }
            };
        }
    }

}

