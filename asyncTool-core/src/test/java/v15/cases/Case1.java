package v15.cases;

import com.jd.platform.async.executor.Async;
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
                            Thread.sleep(5000);
                        }else {
                            System.out.println("wrapper(id=" + id + ") is worki444ng");
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return id;
                });
    }

    public static void main(String[] args) {
        WorkerWrapper<?, ?> a = builder("A").build();
        WorkerWrapper<?, ?> d;
        builder("H")
                .depends(
                        builder("F")
                                .depends(builder("B").depends(a).build())
                                .depends(builder("C").depends(a).build())
                                .build(),
                        builder("G")
                                .depends(builder("E")
                                        .depends(d = builder("D").build())
                                        .build())
                                .build()
                )
                .build();
        try {
            Async.work(1000, a, d).awaitFinish();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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

