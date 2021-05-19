package com.jd.platform.async.wrapper;

import com.jd.platform.async.executor.PollingCenter;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.jd.platform.async.openutil.timer.*;

/**
 * @author create by TcSnZh on 2021/5/9-下午7:21
 */
public class WorkerWrapperGroup {
    /**
     * 任务开始时间
     */
    private final long groupStartTime;
    /**
     * 任务限时
     */
    private final long timeoutLength;
    /**
     * 该map存放所有wrapper的id和wrapper映射
     * <p/>
     * 需要线程安全。
     */
    private final Map<String, WorkerWrapper<?, ?>> forParamUseWrappers = new ConcurrentHashMap<>();
    /**
     * 当全部wrapper都调用结束，它会countDown
     */
    private final CountDownLatch endCDL = new CountDownLatch(1);

    private final AtomicBoolean anyTimeout = new AtomicBoolean(false);

    public WorkerWrapperGroup(long groupStartTime, long timeoutLength) {
        this.groupStartTime = groupStartTime;
        this.timeoutLength = timeoutLength;
    }

    public void addWrapper(Collection<? extends WorkerWrapper<?, ?>> wrapper) {
        Objects.requireNonNull(wrapper).forEach(this::addWrapper);
    }

    @SuppressWarnings("unused")
    public void addWrapper(WorkerWrapper<?, ?>... wrappers) {
        for (WorkerWrapper<?, ?> wrapper : Objects.requireNonNull(wrappers)) {
            addWrapper(wrapper);
        }
    }

    public void addWrapper(WorkerWrapper<?, ?> wrapper) {
        if (wrapper != null) {
            forParamUseWrappers.put(wrapper.id, wrapper);
        }
    }

    public Map<String, WorkerWrapper<?, ?>> getForParamUseWrappers() {
        return forParamUseWrappers;
    }

    /**
     * 同步等待这组wrapper执行完成
     *
     * @return false代表有wrapper超时了。true代表全部wrapper没有超时。
     */
    public boolean awaitFinish() throws InterruptedException {
        endCDL.await();
        return !anyTimeout.get();
    }

    public class CheckFinishTask implements TimerTask {

        @SuppressWarnings("RedundantThrows")
        @Override
        public void run(Timeout timeout) throws Exception {
            // 已经完成了
            if (endCDL.getCount() < 1) {
                return;
            }
            AtomicBoolean hasTimeout = new AtomicBoolean(false);
            // 记录正在运行中的wrapper里，最近的限时时间。
            AtomicLong minDaley = new AtomicLong(Long.MAX_VALUE);
            final Collection<WorkerWrapper<?, ?>> values = forParamUseWrappers.values();
            final Stream<WorkerWrapper<?, ?>> stream = values.size() > 1024 ? values.parallelStream() : values.stream();
            boolean allFinish = stream
                    // 处理超时
                    .peek(wrapper -> {
                        // time_diff :
                        // -1  ->  already timeout ;
                        // 0   ->  finish but not timeout ;
                        // X>0 ->  is running , may timeout in X seconds .
                        long time_diff = wrapper.checkTimeout(true, groupStartTime, timeoutLength);
                        if (time_diff < 0) {
                            hasTimeout.set(true);
                        }
                        if (time_diff == 0) {
                            return;
                        }
                        do {
                            long getMinDaley = minDaley.get();
                            if (getMinDaley <= time_diff || minDaley.compareAndSet(getMinDaley, time_diff)) {
                                return;
                            }
                        } while (true);
                    })
                    // 判断是否结束，这里如果还有未结束的wrapper则会提前结束流。
                    .allMatch(wrapper -> wrapper.getState().finished());
            long getMinDaley = minDaley.get();
            // 如果有正在运行的wrapper
            if (!allFinish) {
                // 如果有正在WORKING的wrapper，则计算一下限时时间，限时完成后轮询它。
                if (getMinDaley != Long.MAX_VALUE) {
                    PollingCenter.getInstance().checkGroup(this, getMinDaley);
                }
            }
            if (allFinish) {
                anyTimeout.set(hasTimeout.get());
                endCDL.countDown();
            }
        }

        // hashCode and equals will called WorkerWrapperGroup.this

        /**
         * 将会调用{@link WorkerWrapperGroup#hashCode()}
         */
        @Override
        public int hashCode() {
            return WorkerWrapperGroup.this.hashCode();
        }

        /**
         * 将会调用{@link WorkerWrapperGroup#equals(Object)}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof CheckFinishTask)) {
                return false;
            }
            return Objects.equals(WorkerWrapperGroup.this, ((CheckFinishTask) obj).getParent());
        }

        private WorkerWrapperGroup getParent() {
            return WorkerWrapperGroup.this;
        }
    }
}
