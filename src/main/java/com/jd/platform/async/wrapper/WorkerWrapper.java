package com.jd.platform.async.wrapper;

import com.jd.platform.async.callback.DefaultCallback;
import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.callback.IWorker;
import com.jd.platform.async.exception.SkippedException;
import com.jd.platform.async.executor.WrapperEndingInspector;
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
     * 总控制台超时，停止所有任务
     */
    public void stopNow() {
        if (getState() == INIT || getState() == WORKING) {
            fastFail(getState(), null);
        }
    }

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
            nextWrappers.forEach(next -> {
                executorService.submit(() -> next.work(executorService, this, nextRemainTIme, getForParamUseWrappers(), inspector));
            });
        } finally {
            inspector.setWrapperEndWithTryPolling(this);
        }
    }

    /**
     * 执行自己的job.具体的执行是在另一个线程里,但判断阻塞超时是在work线程
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
            callback.begin();
            //执行耗时操作
            V resultValue = resultValue = (V) worker.action(param, (Map) getForParamUseWrappers());
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

    WrapperStrategy getWrapperStrategy() {
        return wrapperStrategy;
    }

    // ========== toString ==========

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(150)
                .append("WorkerWrapper{id=").append(id)
                .append(", param=").append(param)
                .append(", worker=").append(worker)
                .append(", callback=").append(callback)
                .append(", state=").append(state)
                .append(", workResult=").append(workResult)
                // 防止循环引用，这里只输出相关Wrapper的id
                .append(", forParamUseWrappers::getId=");
        getForParamUseWrappers().keySet().forEach(wrapperId -> sb.append(wrapperId).append(", "));
        if (getForParamUseWrappers().keySet().size() > 0) {
            sb.delete(sb.length() - 2, sb.length());
        }
        sb
                .append(", dependWrappers::getId=[");
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
}
