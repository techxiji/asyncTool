package v15.cases;

import com.jd.platform.async.executor.Async;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapperBuilder;

/**
 * @author tcsnzh[zh.jobs@foxmail.com] create this in 2021/5/25-下午9:13
 */
class Case01 {
    static WorkerWrapperBuilder<?, ?> builder(String id) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    System.out.println("wrapper(id=" + id + ") is working");
                    return null;
                });
    }

    public static void main(String[] args) {
        WorkerWrapper<?, ?> a = builder("A").build();
        WorkerWrapper<?, ?> b = builder("B").depends(a).build();
        WorkerWrapper<?, ?> c = builder("C").depends(a).build();
        WorkerWrapper<?, ?> f = builder("F").depends(b, c).build();
        try {
            Async.work(100, a).awaitFinish();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        /* 输出:
        wrapper(id=A) is working
        wrapper(id=C) is working
        wrapper(id=B) is working
        wrapper(id=F) is working
        */
    }
}
