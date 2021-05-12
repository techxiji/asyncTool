package v15.cases;

import com.jd.platform.async.executor.Async;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperBuilder;
import com.jd.platform.async.wrapper.strategy.depend.DependenceStrategy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 示例：设置依赖策略--快速上手
 *
 * @author create by TcSnZh on 2021/5/8-下午10:58
 */
class Case3 {
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
        WorkerWrapper<?, ?> b1 = builder("B1").depends(a).build();
        WorkerWrapper<?, ?> b2 = builder("B2").depends(a).build();
        WorkerWrapper<?, ?> b3 = builder("B3").depends(a).build();
        WorkerWrapper<?, ?> b4 = builder("B4").depends(a).build();
        WorkerWrapper<?, ?> b5 = builder("B5").depends(a).build();
        WorkerWrapper<?, ?> c1 = builder("C1")
                .depends(DependenceStrategy.ALL_DEPENDENCIES_ALL_SUCCESS, b1, b2)
                .build();
        WorkerWrapper<?, ?> c2 = builder("C2")
                .depends(DependenceStrategy.ALL_DEPENDENCIES_ANY_SUCCESS, b3, b4, b5)
                .build();
        // 这里用线程数较少的线程池做示例，对于ALL_DEPENDENCIES_ANY_SUCCESS“仅需一个”的效果会好一点
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Async.work(1000, pool, a).awaitFinish();
        } finally {
            pool.shutdown();
        }
        /* 输出:
        wrapper(id=A) is working
		wrapper(id=B3) is working
		wrapper(id=B1) is working
		wrapper(id=B2) is working
		wrapper(id=C2) is working
		wrapper(id=C1) is working
		wrapper(id=B4) is working
		// 我们看到B5被跳过了，没有执行callback
		*/
    }
}
