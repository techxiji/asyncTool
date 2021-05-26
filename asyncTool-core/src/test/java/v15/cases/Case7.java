package v15.cases;

import com.jd.platform.async.executor.Async;
import com.jd.platform.async.worker.ResultState;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperBuilder;
import com.jd.platform.async.wrapper.strategy.depend.DependenceAction;
import com.jd.platform.async.wrapper.strategy.depend.DependenceStrategy;
import com.jd.platform.async.wrapper.strategy.skip.SkipStrategy;

import java.util.concurrent.ExecutionException;

/**
 * @author create by TcSnZh on 2021/5/9-下午4:12
 */
class Case7 {
    private static WorkerWrapperBuilder<?, ?> builder(String id) {
        return builder(id, -1L);
    }

    private static WorkerWrapperBuilder<?, ?> builder(String id, long sleepTime) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    System.out.println("wrapper(id=" + id + ") is working");
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    return null;
                });
    }

    /**
     * A ==> B(10ms) ==> C ==> D  (D可在E、C任意一个完成后执行)
     * . \====> E(5ms) ====/
     */
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        WorkerWrapper<?, ?> d = builder("D").depends(DependenceStrategy.ALL_DEPENDENCIES_ANY_SUCCESS).build();
        WorkerWrapper<?, ?> a = builder("A")
                .nextOf(builder("B", 10)
                                .nextOf(builder("C")
                                        .nextOf(d)
                                        // 这里我们没有设置C的跳过策略，因为默认使用CHECK_ONE_LEVEL，可将下行代码注释去掉，则C会执行
//                                        .setSkipStrategy(SkipStrategy.NOT_SKIP)
                                        .build())
                                .build(),
                        builder("E", 5).nextOf(d).build()
                ).build();
        Async.work(1000, a).awaitFinish();
        /* 输出：
        wrapper(id=A) is working
        wrapper(id=E) is working
        wrapper(id=B) is working
        wrapper(id=D) is working
        */
    }
}
