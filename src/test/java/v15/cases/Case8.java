package v15.cases;

import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.executor.Async;
import com.jd.platform.async.worker.WorkResult;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperBuilder;
import com.jd.platform.async.wrapper.strategy.depend.DependenceStrategy;

import java.util.concurrent.ExecutionException;

/**
 * @author create by TcSnZh on 2021/5/9-下午4:34
 */
class Case8 {
    private static WorkerWrapperBuilder<?, ?> builder(String id) {
        return builder(id, -1L);
    }

    private static WorkerWrapperBuilder<?, ?> builder(String id, long sleepTime) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    System.out.println("\twrapper(id=" + id + ") is working");
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    return null;
                })
                .callback((new ICallback<String, String>() {
                    @Override
                    public void begin() {
                        System.out.println("wrapper(id=" + id + ") has begin . ");
                    }

                    @Override
                    public void result(boolean success, String param, WorkResult<String> workResult) {
                        System.out.println("\t\twrapper(id=" + id + ") callback "
                                + (success ? "success " : "fail ")
                                + ", workResult is " + workResult);
                    }
                }));
    }

    /**
     * A ==> B(10ms) ==> C(20ms)
     */
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        WorkerWrapper<?, ?> a = builder("A")
                .nextOf(builder("B", 10)
                        .nextOf(builder("C", 20).build())
                        .build())
                .build();
        Async.beginWork(15, a);
        /* 输出：
        wrapper(id=A) has begin .
            wrapper(id=A) is working
                wrapper(id=A) callback success , workResult is WorkResult{result=null, resultState=SUCCESS, ex=null}
        wrapper(id=B) has begin .
            wrapper(id=B) is working
                wrapper(id=B) callback success , workResult is WorkResult{result=null, resultState=TIMEOUT, ex=null}
        wrapper(id=C) has begin .
                wrapper(id=C) callback fail , workResult is WorkResult{result=null, resultState=TIMEOUT, ex=null}
        java.lang.InterruptedException: sleep interrupted
            at java.lang.Thread.sleep(Native Method)
            以下异常信息省略
        */
    }
}
