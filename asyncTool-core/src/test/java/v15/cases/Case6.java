package v15.cases;

import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.executor.Async;
import com.jd.platform.async.worker.ResultState;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperBuilder;
import com.jd.platform.async.wrapper.strategy.depend.DependenceAction;

import java.util.concurrent.ExecutionException;

/**
 * 示例：自定义依赖策略--对单个wrapper设置“上克下”策略--简单使用与示例
 *
 * @author create by TcSnZh on 2021/5/9-上午1:42
 */
class Case6 {
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
        //noinspection unchecked
        WorkerWrapper<?, ?> b = builder("B")
                // 这里设置了，不论a怎么样b都会快速失败。但是，a设置的对wrapper的特殊策略把它覆盖了。
                .depends((dependWrappers, thisWrapper, fromWrapper) ->
                        DependenceAction.FAST_FAIL
                                .fastFailException(ResultState.EXCEPTION, new RuntimeException("b 必定失败，除非有上游wrapper救他"))
                )
                .callback(ICallback.PRINT_EXCEPTION_STACK_TRACE)
                .build();
        WorkerWrapper<?, ?> a = builder("A")
                .setNext()
                // a将会使b直接开始工作
                // 若是去掉这行代码，则b会失败
                .specialToNextWrapper(fromWrapper -> DependenceAction.START_WORK.emptyProperty(), b)
                .wrapper(b)
                .end().build();
        Async.work(1000, a).awaitFinish();
        System.out.println(a.getWorkResult());
        System.out.println(b.getWorkResult());
        /* 输出：
        wrapper(id=A) is working
        wrapper(id=B) is working
        WorkResult{result=null, resultState=SUCCESS, ex=null}
        WorkResult{result=null, resultState=SUCCESS, ex=null}
        */
    }
}
