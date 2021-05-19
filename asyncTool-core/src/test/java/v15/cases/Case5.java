package v15.cases;

import com.jd.platform.async.executor.Async;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperBuilder;
import com.jd.platform.async.wrapper.strategy.depend.DependenceAction;
import com.jd.platform.async.wrapper.strategy.depend.DependenceStrategy;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 示例：设置一组必须完成的wrapper（不推荐使用）
 *
 * @author create by TcSnZh on 2021/5/9-上午1:06
 */
class Case5 {
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

    @Deprecated
    public static void main(String[] args) throws ExecutionException, InterruptedException {

        WorkerWrapper<?, ?> a1 = builder("A1").build();
        WorkerWrapper<?, ?> a2 = builder("A2").build();
        WorkerWrapper<?, ?> a3 = builder("A3").build();
        WorkerWrapper<?, ?> a4 = builder("A4").build();
        WorkerWrapper<?, ?> a5 = builder("A5").build();
        WorkerWrapper<?, ?> a6 = builder("A6").build();
        WorkerWrapper<?, ?> a7 = builder("A7").build();
        WorkerWrapper<?, ?> a8 = builder("A8").build();
        WorkerWrapper<?, ?> a9 = builder("A9").build();
        WorkerWrapper<?, ?> a10 = builder("A10").build();
        builder("B")
                .setDepend()
                // 必须a3、a4成功才能执行
                .mustRequireWrapper(a3, a4)
                // 如果a3、a4没有成功，则休息
                .strategy((dependWrappers, thisWrapper, fromWrapper) -> DependenceAction.TAKE_REST.emptyProperty())
                .wrapper(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10)
                .end()
                .build();
        WorkerWrapper<?, ?> start = builder("start").nextOf(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10).build();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Async.beginWork(1000, pool, start);
        } finally {
            pool.shutdown();
        }
        /* 输出：
        wrapper(id=A1) is working
        wrapper(id=A2) is working
        wrapper(id=A4) is working
        wrapper(id=A3) is working
        wrapper(id=A5) is working
        wrapper(id=B) is working
        wrapper(id=A6) is working
        我们可以看到，A3、A4执行后，B也执行了，之后的wrapper被跳过了
        （这里之所以a5、a6还在执行，只是因为他两正好在WORKING，所以没发现后面的B已经可以跳过了）
        */
    }
}
