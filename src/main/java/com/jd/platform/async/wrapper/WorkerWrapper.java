package com.jd.platform.async.wrapper;

import com.jd.platform.async.callback.DefaultCallback;
import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.callback.IWorker;
import com.jd.platform.async.exception.SkippedException;
import com.jd.platform.async.executor.timer.SystemClock;
import com.jd.platform.async.worker.*;
import com.jd.platform.async.wrapper.skipstrategy.SkipStrategy;
import com.jd.platform.async.wrapper.actionstrategy.DependMustStrategyMapper;
import com.jd.platform.async.wrapper.actionstrategy.DependWrapperStrategyMapper;
import com.jd.platform.async.wrapper.actionstrategy.DependenceAction;
import com.jd.platform.async.wrapper.actionstrategy.DependenceStrategy;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对每个worker及callback进行包装，一对一
 * <p/>
 * v1.5时将其抽取为抽象类，以解耦并提高扩展性。
 *
 * @author wuweifeng wrote on 2019-11-19.
 */
public abstract class WorkerWrapper<T, V> {
    /**
     * 该wrapper的唯一标识
     */
    protected final String id;
    /**
     * worker将来要处理的param
     */
    protected T param;
    protected IWorker<T, V> worker;
    protected ICallback<T, V> callback;
    /**
     * 标记该事件是否已经被处理过了，譬如已经超时返回false了，后续rpc又收到返回值了，则不再二次回调
     * 经试验,volatile并不能保证"同一毫秒"内,多线程对该值的修改和拉取
     * <p>
     * 1-finish, 2-error, 3-working
     */
    protected final AtomicInteger state = new AtomicInteger(0);
    /**
     * 也是个钩子变量，用来存临时的结果
     */
    protected volatile WorkResult<V> workResult = WorkResult.defaultResult();
    /**
     * 该map存放所有wrapper的id和wrapper映射
     * <p/>
     * 需要线程安全。
     */
    private Map<String, WorkerWrapper<?, ?>> forParamUseWrappers;
    /**
     * 各种策略的封装类。
     * <p/>
     * 其实是因为加功能太多导致这个对象大小超过了128Byte，所以强迫症的我不得不把几个字段丢到策略类里面去。
     * ps: 大小超过128Byte令我(TcSnZh)难受的一比，就像走在草坪的格子上，一步嫌小、两步扯蛋。
     * IDEA可以使用JOL Java Object Layout插件查看对象大小。
     */
    private final WrapperStrategy wrapperStrategy = new WrapperStrategy();
    /**
     * 超时检查，该值允许为null。表示不设置。
     */
    private volatile TimeOutProperties timeOutProperties;

    // *****   state属性的常量值   *****

    public static final int FINISH = 1;
    public static final int ERROR = 2;
    public static final int WORKING = 3;
    public static final int INIT = 0;

    WorkerWrapper(String id, IWorker<T, V> worker, T param, ICallback<T, V> callback) {
        if (worker == null) {
            throw new NullPointerException("async.worker is null");
        }
        this.worker = worker;
        this.param = param;
        this.id = id;
        //允许不设置回调
        if (callback == null) {
            callback = new DefaultCallback<>();
        }
        this.callback = callback;
    }

    // ========== public ==========

    /**
     * 外部调用本线程运行此Wrapper的入口方法。
     *
     * @param executorService     该ExecutorService将成功运行后，在nextWrapper有多个时被使用于多线程调用。
     * @param remainTime          剩下的时间
     * @param forParamUseWrappers 用于保存经过的wrapper的信息的Map，key为id。
     * @param inspector           wrapper调度检查器
     */
    public void work(ExecutorService executorService,
                     long remainTime,
                     Map<String, WorkerWrapper<?, ?>> forParamUseWrappers,
                     WrapperEndingInspector inspector) {
        work(executorService, null, remainTime, forParamUseWrappers, inspector);
    }

    public String getId() {
        return id;
    }

    public WorkResult<V> getWorkResult() {
        return workResult;
    }

    public void setParam(T param) {
        this.param = param;
    }

    public int getState() {
        return state.get();
    }

    /**
     * 获取之后的下游Wrapper
     */
    public abstract Set<WorkerWrapper<?, ?>> getNextWrappers();

    /**
     * 使wrapper状态修改为超时失败。（但如果已经执行完成则不会修改）
     * <p/>
     * 本方法不会试图执行超时判定逻辑。
     * 如果要执行超时逻辑判断，请用{@link TimeOutProperties#checkTimeOut(boolean)}并传入参数true。
     */
    public void failNow() {
        int state = getState();
        if (state == INIT || state == WORKING) {
            fastFail(state, null);
        }
    }

    public WrapperStrategy getWrapperStrategy() {
        return wrapperStrategy;
    }

    // ========== protected ==========

    /**
     * 快速失败
     *
     * @return 已经失败则返回false，如果刚才设置为失败了则返回true。
     */
    protected boolean fastFail(int expect, Exception e) {
        //试图将它从expect状态,改成Error
        if (!compareAndSetState(expect, ERROR)) {
            return false;
        }

        //尚未处理过结果
        if (checkIsNullResult()) {
            if (e == null) {
                workResult.setResultState(ResultState.TIMEOUT);
            } else {
                workResult.setResultState(ResultState.EXCEPTION);
                workResult.setEx(e);
            }
            workResult.setResult(worker.defaultValue());
        }
        callback.result(false, param, workResult);
        return true;
    }

    /**
     * 判断{@link #state}状态是否是初始值。
     */
    protected boolean checkIsNullResult() {
        return ResultState.DEFAULT == workResult.getResultState();
    }

    protected boolean compareAndSetState(int expect, int update) {
        return this.state.compareAndSet(expect, update);
    }

    /**
     * 工作的核心方法。
     *
     * @param fromWrapper 代表这次work是由哪个上游wrapper发起的。如果是首个Wrapper则为null。
     * @param remainTime  剩余时间。
     */
    protected void work(ExecutorService executorService,
                        WorkerWrapper<?, ?> fromWrapper,
                        long remainTime,
                        Map<String, WorkerWrapper<?, ?>> forParamUseWrappers,
                        WrapperEndingInspector inspector) {
        this.setForParamUseWrappers(forParamUseWrappers);
        //将自己放到所有wrapper的集合里去
        forParamUseWrappers.put(id, this);
        long now = SystemClock.now();
        //总的已经超时了，就快速失败，进行下一个
        if (remainTime <= 0) {
            fastFail(INIT, null);
            beginNext(executorService, now, remainTime, inspector);
            return;
        }
        //如果自己已经执行过了。
        //可能有多个依赖，其中的一个依赖已经执行完了，并且自己也已开始执行或执行完毕。当另一个依赖执行完毕，又进来该方法时，就不重复处理了
        if (getState() == FINISH || getState() == ERROR) {
            beginNext(executorService, now, remainTime, inspector);
            return;
        }

        // 判断是否要跳过自己，该方法可能会跳过正在工作的自己。
        final WrapperStrategy wrapperStrategy = getWrapperStrategy();
        if (wrapperStrategy.shouldSkip(getNextWrappers(), this, fromWrapper)) {
            fastFail(INIT, new SkippedException());
            beginNext(executorService, now, remainTime, inspector);
            return;
        }

        //如果没有任何依赖，说明自己就是第一批要执行的
        final Set<WorkerWrapper<?, ?>> dependWrappers = getDependWrappers();
        if (dependWrappers == null || dependWrappers.size() == 0) {
            fire();
            beginNext(executorService, now, remainTime, inspector);
            return;
        }

        DependenceAction.WithProperty judge = wrapperStrategy.judgeAction(dependWrappers, this, fromWrapper);

        switch (judge.getDependenceAction()) {
            case TAKE_REST:
                inspector.reduceWrapper(this);
                return;
            case FAST_FAIL:
                switch (judge.getResultState()) {
                    case TIMEOUT:
                        fastFail(INIT, null);
                        break;
                    case EXCEPTION:
                        fastFail(INIT, judge.getFastFailException());
                        break;
                    default:
                        fastFail(INIT, new RuntimeException("ResultState " + judge.getResultState() + " set to FAST_FAIL"));
                        break;
                }
                beginNext(executorService, now, remainTime, inspector);
                break;
            case START_WORK:
                fire();
                beginNext(executorService, now, remainTime, inspector);
                break;
            case JUDGE_BY_AFTER:
            default:
                inspector.reduceWrapper(this);
                throw new IllegalStateException("策略配置错误，不应当在WorkerWrapper中返回JUDGE_BY_AFTER或其他无效值 : this=" + this + ",fromWrapper=" + fromWrapper);
        }
    }

    /**
     * 进行下一个任务
     */
    protected void beginNext(ExecutorService executorService, long now, long remainTime, WrapperEndingInspector inspector) {
        //花费的时间
        final long costTime = SystemClock.now() - now;
        final long nextRemainTIme = remainTime - costTime;
        Set<WorkerWrapper<?, ?>> nextWrappers = getNextWrappers();
        if (nextWrappers == null) {
            inspector.setWrapperEndWithTryPolling(this);
            return;
        }
        // nextWrappers只有一个，就用本线程继续跑。
        if (nextWrappers.size() == 1) {
            try {
                WorkerWrapper<?, ?> next = nextWrappers.stream().findFirst().get();
                inspector.addWrapper(next);
                next.work(executorService, WorkerWrapper.this, nextRemainTIme, getForParamUseWrappers(), inspector);
            } finally {
                inspector.setWrapperEndWithTryPolling(this);
            }
            return;
        }
        // nextWrappers有多个
        try {
            inspector.addWrapper(nextWrappers);
            nextWrappers.forEach(next -> executorService.submit(() ->
                    next.work(executorService, this, nextRemainTIme, getForParamUseWrappers(), inspector))
            );
        } finally {
            inspector.setWrapperEndWithTryPolling(this);
        }
    }

    /**
     * 本工作线程执行自己的job.判断阻塞超时这里开始时会判断一次总超时时间，但在轮询线程会判断单个wrapper超时时间，并也会判断总超时时间。
     */
    protected void fire() {
        //阻塞取结果
        //避免重复执行
        if (!checkIsNullResult()) {
            return;
        }
        try {
            //如果已经不是init状态了，说明正在被执行或已执行完毕。这一步很重要，可以保证任务不被重复执行
            if (!compareAndSetState(INIT, WORKING)) {
                return;
            }
            V resultValue;
            try {
                callback.begin();
                if (timeOutProperties != null) {
                    timeOutProperties.startWorking();
                }
                //执行耗时操作
                resultValue = (V) worker.action(param, (Map) getForParamUseWrappers());
            } finally {
                if (timeOutProperties != null) {
                    timeOutProperties.endWorking();
                }
            }
            //如果状态不是在working,说明别的地方已经修改了
            if (!compareAndSetState(WORKING, FINISH)) {
                return;
            }
            workResult.setResultState(ResultState.SUCCESS);
            workResult.setResult(resultValue);
            //回调成功
            callback.result(true, param, workResult);
        } catch (Exception e) {
            //避免重复回调
            if (!checkIsNullResult()) {
                return;
            }
            fastFail(WORKING, e);
        }
    }

    // ========== hashcode and equals ==========

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    /**
     * {@code return id.hashCode();}返回id值的hashcode
     */
    @Override
    public int hashCode() {
        // final String id can use to .hashcode() .
        return id.hashCode();
    }

    // ========== Builder ==========

    public static <T, V> WorkerWrapperBuilder<T, V> builder() {
        return new Builder<>();
    }

    /**
     * 自v1.5，该类被抽取到{@link StableWorkerWrapperBuilder}抽象类，兼容之前的版本。
     */
    public static class Builder<W, C> extends StableWorkerWrapperBuilder<W, C, Builder<W, C>> {
        /**
         * @deprecated 建议使用 {@link #builder()}返回{@link WorkerWrapperBuilder}接口，以调用v1.5之后的规范api
         */
        @Deprecated
        public Builder() {
        }
    }

    // ========== package access methods , for example , some getter/setter that doesn't want to be public ==========

    T getParam() {
        return param;
    }

    IWorker<T, V> getWorker() {
        return worker;
    }

    void setWorker(IWorker<T, V> worker) {
        this.worker = worker;
    }

    ICallback<T, V> getCallback() {
        return callback;
    }

    void setCallback(ICallback<T, V> callback) {
        this.callback = callback;
    }

    void setState(int state) {
        this.state.set(state);
    }

    Map<String, WorkerWrapper<?, ?>> getForParamUseWrappers() {
        return forParamUseWrappers;
    }

    void setForParamUseWrappers(Map<String, WorkerWrapper<?, ?>> forParamUseWrappers) {
        this.forParamUseWrappers = forParamUseWrappers;
    }

    void setWorkResult(WorkResult<V> workResult) {
        this.workResult = workResult;
    }

    abstract void setNextWrappers(Set<WorkerWrapper<?, ?>> nextWrappers);

    abstract Set<WorkerWrapper<?, ?>> getDependWrappers();

    abstract void setDependWrappers(Set<WorkerWrapper<?, ?>> dependWrappers);

    TimeOutProperties getTimeOut() {
        return timeOutProperties;
    }

    void setTimeOut(TimeOutProperties timeOutProperties) {
        this.timeOutProperties = timeOutProperties;
    }

    // ========== toString ==========

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(200)
                .append("WorkerWrapper{id=").append(id)
                .append(", param=").append(param)
                .append(", worker=").append(worker)
                .append(", callback=").append(callback)
                .append(", state=");
        int state = this.state.get();
        if (state == FINISH) {
            sb.append("FINISH");
        } else if (state == WORKING) {
            sb.append("WORKING");
        } else if (state == INIT) {
            sb.append("INIT");
        } else if (state == ERROR) {
            sb.append("ERROR");
        } else {
            throw new IllegalStateException("unknown state : " + state);
        }
        sb
                .append(", workResult=").append(workResult)
                // 防止循环引用，这里只输出相关Wrapper的id
                .append(", forParamUseWrappers::getId=[");
        getForParamUseWrappers().keySet().forEach(wrapperId -> sb.append(wrapperId).append(", "));
        if (getForParamUseWrappers().keySet().size() > 0) {
            sb.delete(sb.length() - 2, sb.length());
        }
        sb
                .append("], dependWrappers::getId=[");
        getDependWrappers().stream().map(WorkerWrapper::getId).forEach(wrapperId -> sb.append(wrapperId).append(", "));
        if (getDependWrappers().size() > 0) {
            sb.delete(sb.length() - 2, sb.length());
        }
        sb
                .append("], nextWrappers::getId=[");
        getNextWrappers().stream().map(WorkerWrapper::getId).forEach(wrapperId -> sb.append(wrapperId).append(", "));
        if (getNextWrappers().size() > 0) {
            sb.delete(sb.length() - 2, sb.length());
        }
        sb
                .append("]")
                .append(", wrapperStrategy=").append(getWrapperStrategy())
                .append(", timeOutProperties=").append(getTimeOut())
                .append('}');
        return sb.toString();
    }

    public static class WrapperStrategy implements DependenceStrategy, SkipStrategy {

        // ========== 这三个属性用来判断是否要开始工作 ==========

        // 从前往后依次判断的顺序为 dependWrapperStrategyMapper -> dependMustStrategyMapper -> dependenceStrategy

        /**
         * 对特殊Wrapper专用的依赖响应策略。
         * <b>该值允许为null</b>
         */
        private DependWrapperStrategyMapper dependWrapperStrategyMapper;
        /**
         * 对必须完成的（must的）Wrapper的依赖响应策略。
         * <b>该值允许为null</b>
         * <p/>
         * 这是一个不得不向历史妥协的属性。用于适配must开关方式。
         */
        private DependMustStrategyMapper dependMustStrategyMapper;
        /**
         * 依赖响应全局策略。
         */
        private DependenceStrategy dependenceStrategy;

        @Override
        public DependenceAction.WithProperty judgeAction(Set<WorkerWrapper<?, ?>> dependWrappers,
                                                         WorkerWrapper<?, ?> thisWrapper,
                                                         WorkerWrapper<?, ?> fromWrapper) {
            // 如果存在依赖，则调用三层依赖响应策略进行判断
            DependenceStrategy strategy = dependWrapperStrategyMapper;
            if (dependMustStrategyMapper != null) {
                strategy = strategy == null ? dependMustStrategyMapper : strategy.thenJudge(dependMustStrategyMapper);
            }
            if (dependenceStrategy != null) {
                strategy = strategy == null ? dependenceStrategy : strategy.thenJudge(dependenceStrategy);
            }
            if (strategy == null) {
                throw new IllegalStateException("配置无效，三层判断策略均为null，请开发者检查自己的Builder是否逻辑错误！");
            }
            return strategy.judgeAction(dependWrappers, thisWrapper, fromWrapper);
        }

        public DependWrapperStrategyMapper getDependWrapperStrategyMapper() {
            return dependWrapperStrategyMapper;
        }

        public void setDependWrapperStrategyMapper(DependWrapperStrategyMapper dependWrapperStrategyMapper) {
            this.dependWrapperStrategyMapper = dependWrapperStrategyMapper;
        }

        public DependMustStrategyMapper getDependMustStrategyMapper() {
            return dependMustStrategyMapper;
        }

        public void setDependMustStrategyMapper(DependMustStrategyMapper dependMustStrategyMapper) {
            this.dependMustStrategyMapper = dependMustStrategyMapper;
        }

        public DependenceStrategy getDependenceStrategy() {
            return dependenceStrategy;
        }

        public void setDependenceStrategy(DependenceStrategy dependenceStrategy) {
            this.dependenceStrategy = dependenceStrategy;
        }

        // ========== 跳过策略 ==========

        private SkipStrategy skipStrategy;

        @Override
        public boolean shouldSkip(Set<WorkerWrapper<?, ?>> nextWrappers, WorkerWrapper<?, ?> thisWrapper, WorkerWrapper<?, ?> fromWrapper) {
            return skipStrategy != null && skipStrategy.shouldSkip(nextWrappers, thisWrapper, fromWrapper);
        }

        public SkipStrategy getSkipStrategy() {
            return skipStrategy;
        }

        public void setSkipStrategy(SkipStrategy skipStrategy) {
            this.skipStrategy = skipStrategy;
        }

        // ========== toString ==========

        @Override
        public String toString() {
            return "WrapperStrategy{" +
                    "dependWrapperStrategyMapper=" + dependWrapperStrategyMapper +
                    ", dependMustStrategyMapper=" + dependMustStrategyMapper +
                    ", dependenceStrategy=" + dependenceStrategy +
                    ", skipStrategy=" + skipStrategy +
                    '}';
        }
    }

    public static class TimeOutProperties {
        private final boolean enable;
        private final long time;
        private final TimeUnit unit;
        private final boolean allowInterrupt;
        private final WorkerWrapper<?, ?> wrapper;

        private final Object lock = new Object();

        private volatile boolean started = false;
        private volatile boolean ended = false;
        private volatile long startWorkingTime;
        private volatile long endWorkingTime;
        private volatile Thread doWorkingThread;

        public TimeOutProperties(boolean enable, long time, TimeUnit unit, boolean allowInterrupt, WorkerWrapper<?, ?> wrapper) {
            this.enable = enable;
            this.time = time;
            this.unit = unit;
            this.allowInterrupt = allowInterrupt;
            this.wrapper = wrapper;
        }

        // ========== 工作线程调用 ==========

        public void startWorking() {
            synchronized (lock) {
                started = true;
                startWorkingTime = SystemClock.now();
                doWorkingThread = Thread.currentThread();
            }
        }

        public void endWorking() {
            synchronized (lock) {
                ended = true;
                doWorkingThread = null;
                endWorkingTime = SystemClock.now();
            }
        }

        // ========== 轮询线程调用 ==========

        /**
         * 检查超时。
         * 可以将boolean参数传入true以在超时的时候直接失败。
         *
         * @param withStop 如果为false，不会发生什么，仅仅是单纯的判断是否超时。
         *                 如果为true，则会去快速失败wrapper{@link #failNow()}，有必要的话还会打断线程。
         * @return 如果 超时 或 执行时间超过限制 返回true；未超时返回false。
         */
        public boolean checkTimeOut(boolean withStop) {
            if (enable) {
                synchronized (lock) {
                    if (started) {
                        // 判断执行中的wrapper是否超时
                        long dif = (ended ? endWorkingTime : SystemClock.now()) - startWorkingTime;
                        if (dif > unit.toMillis(time)) {
                            if (withStop) {
                                if (allowInterrupt) {
                                    doWorkingThread.interrupt();
                                }
                                wrapper.failNow();
                                ended = true;
                            }
                            return true;
                        }
                        return false;
                    }
                }
            }
            return false;
        }

        // ========== package ==========

        boolean isEnable() {
            return enable;
        }

        long getTime() {
            return time;
        }

        TimeUnit getUnit() {
            return unit;
        }

        boolean isAllowInterrupt() {
            return allowInterrupt;
        }

        Object getLock() {
            return lock;
        }

        boolean isStarted() {
            return started;
        }

        void setStarted(boolean started) {
            this.started = started;
        }

        boolean isEnded() {
            return ended;
        }

        void setEnded(boolean ended) {
            this.ended = ended;
        }

        long getStartWorkingTime() {
            return startWorkingTime;
        }

        void setStartWorkingTime(long startWorkingTime) {
            this.startWorkingTime = startWorkingTime;
        }

        long getEndWorkingTime() {
            return endWorkingTime;
        }

        void setEndWorkingTime(long endWorkingTime) {
            this.endWorkingTime = endWorkingTime;
        }

        Thread getDoWorkingThread() {
            return doWorkingThread;
        }

        void setDoWorkingThread(Thread doWorkingThread) {
            this.doWorkingThread = doWorkingThread;
        }


        // ========== toString ==========

        @Override
        public String toString() {
            return "TimeOutProperties{" +
                    "enable=" + enable +
                    ", time=" + time +
                    ", unit=" + unit +
                    ", allowInterrupt=" + allowInterrupt +
                    ", wrapper::getId=" + wrapper.getId() +
                    ", started=" + started +
                    ", ended=" + ended +
                    ", startWorkingTime=" + startWorkingTime +
                    ", endWorkingTime=" + endWorkingTime +
                    ", doWorkingThread=" + doWorkingThread +
                    '}';
        }
    }
}
