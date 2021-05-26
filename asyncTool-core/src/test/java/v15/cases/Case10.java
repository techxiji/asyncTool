package v15.cases;

import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.executor.Async;
import com.jd.platform.async.worker.OnceWork;
import com.jd.platform.async.worker.WorkResult;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperBuilder;

import java.util.concurrent.ExecutionException;

/**
 * @author tcsnzh[zh.jobs@foxmail.com] create this in 2021/5/26-下午4:07
 */
class Case10 {
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
                }))
                .allowInterrupt(true);
    }

    /**
     * A(10ms) ==> B(10ms) ==> C(10ms)
     */
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        final WorkerWrapper<?, ?> c;
        final WorkerWrapper<?, ?> b;
        final WorkerWrapper<?, ?> a = builder("A", 10)
                .nextOf(b = builder("B", 10)
                        .nextOf(c = builder("C", 10).build())
                        .build())
                .build();
        final OnceWork onceWork = Async.work(40, a);
        Thread.sleep(25);
        onceWork.pleaseCancelAndAwaitFinish();
        System.out.println("任务b信息 " + b);
        System.out.println("任务c信息 " + c);
        System.out.println("OnceWork信息 " + onceWork);
        /*
            可以看到C的state为SKIP，workResult.ex为CancelSkippedException，即被取消了。
            不过有时程序运行慢，导致B被取消了，那么C就不会执行，其状态就为INIT了。
         */
    }
}
