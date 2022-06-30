package v15.cases;

import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.executor.Async;
import com.jd.platform.async.executor.timer.SystemClock;
import com.jd.platform.async.worker.WorkResult;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperBuilder;

/**
 * 示例：简单示例--复杂点的
 *
 * @author create by TcSnZh on 2021/5/8-下午10:29
 */
class Case1 {

    private static WorkerWrapperBuilder<?, ?> builder(String id) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    try {
                        if ("F".equals(id)) {
                            System.out.println("wrapper(id=" + id + ") is working");
                            Thread.sleep(2000);
                        } else {
                            System.out.println("wrapper(id=" + id + ") is worki444ng");
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return id;
                }).callback((new ICallback<String, String>() {
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

    public static void main(String[] args) {
        long now = SystemClock.now();
        WorkerWrapper<?, ?> a = builder("A").build();
        WorkerWrapper<?, ?> d = builder("D").build();
        builder("H")
                .depends(
                        builder("F")
                                .depends(builder("B").depends(a).build())
                                .depends(builder("C").depends(a).build())
                                .build(),
                        builder("G")
                                .depends(builder("E")
                                        .depends(d)
                                        .build())
                                .build()
                )
                .build();
        try {
            Async.work(1000, a, d).awaitFinish();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("now:" + (SystemClock.now() - now));
        /* 输出:
        wrapper(id=D) is working
        wrapper(id=A) is working
        wrapper(id=E) is working
        wrapper(id=B) is working
        wrapper(id=C) is working
        wrapper(id=G) is working
        wrapper(id=F) is working
        wrapper(id=H) is working
        */
    }

}

