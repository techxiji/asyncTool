package v15.cases;

import com.jd.platform.async.executor.Async;
import com.jd.platform.async.worker.ResultState;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperBuilder;
import com.jd.platform.async.wrapper.strategy.depend.DependenceAction;
import com.jd.platform.async.wrapper.strategy.depend.DependenceStrategy;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 示例：自定义全局策略
 *
 * @author create by TcSnZh on 2021/5/8-下午11:28
 */
class Case4 {
    private static WorkerWrapperBuilder<?, ?> builder(String id) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    System.out.println("wrapper(id=" + id + ") is working");
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return null;
                });
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        WorkerWrapper<?, ?> a = builder("A").build();
        WorkerWrapper<?, ?> c = builder("C")
                .setDepend().strategy(new DependenceStrategy() {
                    @Override
                    public DependenceAction.WithProperty judgeAction(Set<WorkerWrapper<?, ?>> dependWrappers, WorkerWrapper<?, ?> thisWrapper, WorkerWrapper<?, ?> fromWrapper) {
                        return dependWrappers.stream()
                                .filter(workerWrapper -> workerWrapper.getWorkResult().getResultState() == ResultState.SUCCESS)
                                .count() > 3 ?
                                DependenceAction.START_WORK.emptyProperty()
                                : DependenceAction.TAKE_REST.emptyProperty();
                    }
                }).end()
                .build();
        for (int i = 1; i < 10; i++) {
            builder("B" + i).depends(a).nextOf(c).build();
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Async.beginWork(1000, pool, a);
        } finally {
            pool.shutdown();
        }
        /* 输出:
        wrapper(id=A) is working
        wrapper(id=B2) is working
        wrapper(id=B1) is working
        wrapper(id=B4) is working
        wrapper(id=B3) is working
        wrapper(id=B5) is working
        wrapper(id=C) is working
        由于B1-B10是并行的，所以正好仅有3个wrapper成功，在多线程环境中是比较难遇到的。
         */
    }
}
