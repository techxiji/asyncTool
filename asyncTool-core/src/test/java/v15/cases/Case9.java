package v15.cases;

import com.jd.platform.async.callback.DefaultCallback;
import com.jd.platform.async.executor.Async;
import com.jd.platform.async.openutil.collection.CommonDirectedGraph;
import com.jd.platform.async.openutil.collection.DirectedGraph;
import com.jd.platform.async.wrapper.QuickBuildWorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.strategy.WrapperStrategy;

import java.util.concurrent.*;

/**
 * 快速构造示例。
 *
 * @author create by TcSnZh on 2021/5/17-下午5:23
 */
class Case9 {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        DirectedGraph<WorkerWrapper<?, ?>, Object> graph = DirectedGraph.synchronizedDigraph(new CommonDirectedGraph<>());
        QuickBuildWorkerWrapper<Object, Object> w1 = new QuickBuildWorkerWrapper<>("id1",
                null,
                (object, allWrappers) -> {
                    System.out.println("I am IWorker 1");
                    return null;
                },
                DefaultCallback.getInstance(),
                false,
                true,
                100,
                TimeUnit.MILLISECONDS,
                new WrapperStrategy.DefaultWrapperStrategy(),
                graph
        );
        QuickBuildWorkerWrapper<Object, Object> w2 = new QuickBuildWorkerWrapper<>("id2",
                null,
                (object, allWrappers) -> {
                    System.out.println("I am IWorker 2");
                    return null;
                },
                DefaultCallback.getInstance(),
                false,
                true,
                100,
                TimeUnit.MILLISECONDS,
                new WrapperStrategy.DefaultWrapperStrategy(),
                graph
        );
        graph.addNode(w1, w2);
        graph.putRelation(w1, new Object(), w2);

//        System.out.println(graph);

        Async.work(200, w1).awaitFinish();

        System.out.println("    Begin work end .\n    w1 : " + w1 + "\n    w2 : " + w2 + "\n");
        /* 输出：
        I am IWorker 1
        I am IWorker 2
            Begin work end .
            w1 : 省略
            w2 : 省略
         */
    }
}
