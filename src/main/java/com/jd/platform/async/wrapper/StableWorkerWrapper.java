package com.jd.platform.async.wrapper;

import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.callback.IWorker;
import com.jd.platform.async.exception.SkippedException;
import com.jd.platform.async.executor.WrapperEndingInspector;
import com.jd.platform.async.executor.timer.SystemClock;
import com.jd.platform.async.worker.ResultState;
import com.jd.platform.async.wrapper.actionstrategy.DependenceAction;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * {@link WorkerWrapper}默认实现类，将上下游Wrapper保存在自己的Set中。
 *
 * @author create by TcSnZh on 2021/5/6-下午2:41
 */
class StableWorkerWrapper<T, V> extends WorkerWrapper<T, V> {
    StableWorkerWrapper(String id, IWorker<T, V> worker, T param, ICallback<T, V> callback) {
        super(id, worker, param, callback);
    }

    /**
     * 依赖的wrappers，其dependType字段决定了依赖策略。
     *
     * <p>
     * v1.5时将其抽取到本子类。
     * 且修改List为Set，并默认使用LinkedHashSet，以提高id索引效率且保持有序（虽然有序也没什么用）。
     * </p>
     */
    private Set<WorkerWrapper<?, ?>> dependWrappers;
    /**
     * 在自己后面的wrapper，如果没有，自己就是末尾；如果有一个，就是串行；如果有多个，有几个就需要开几个线程</p>
     * -------2
     * 1
     * -------3
     * 如1后面有2、3
     *
     * <p>
     * v1.5时将其抽取到本子类。
     * 且修改List为Set，并在{@link StableWorkerWrapperBuilder}中默认使用LinkedHashSet，以提高id索引效率且保持有序（虽然有序也没什么用）。
     * </p>
     */
    private Set<WorkerWrapper<?, ?>> nextWrappers;

    // ========== public impl ==========

    @Override
    public Set<WorkerWrapper<?, ?>> getNextWrappers() {
        return nextWrappers;
    }


    // ========== package impl ==========

    @Override
    void setNextWrappers(Set<WorkerWrapper<?, ?>> nextWrappers) {
        this.nextWrappers = nextWrappers;
    }

    @Override
    Set<WorkerWrapper<?, ?>> getDependWrappers() {
        return dependWrappers;
    }

    @Override
    void setDependWrappers(Set<WorkerWrapper<?, ?>> dependWrappers) {
        this.dependWrappers = dependWrappers;
    }

}
