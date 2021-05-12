package v15.cases;

import com.jd.platform.async.callback.IWorker;
import com.jd.platform.async.executor.Async;
import com.jd.platform.async.wrapper.WorkerWrapper;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * 示例：简单示例--依赖别的worker执行结果作为入参
 *
 * @author create by TcSnZh on 2021/5/8-下午10:46
 */
class Case2 {
    static class AddWork implements IWorker<Integer, Integer> {
        private final String id1;
        private final String id2;

        public AddWork(String id1, String id2) {
            this.id1 = id1;
            this.id2 = id2;
        }

        public AddWork() {
            this(null, null);
        }

        @Override
        public Integer action(Integer param, Map<String, WorkerWrapper<?,?>> allWrappers) {
            // 传入的参数
            if (param != null) {
                return param;
            }
            // 将两个id所对应的wrapper的结果取出，相加并返回
            Integer i1 = (Integer) allWrappers.get(id1).getWorkResult().getResult();
            Integer i2 = (Integer) allWrappers.get(id2).getWorkResult().getResult();
            return i1 + i2;
        }
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        WorkerWrapper<Integer, Integer> wrapper100 = WorkerWrapper.<Integer, Integer>builder()
                .id("id:100").worker(new AddWork()).param(100).build();
        WorkerWrapper<Integer, Integer> wrapper200 = WorkerWrapper.<Integer, Integer>builder()
                .id("id:200").worker(new AddWork()).param(200).build();
        WorkerWrapper<Integer, Integer> add = WorkerWrapper.<Integer, Integer>builder().id("id:add")
                .worker(new AddWork("id:100", "id:200")).depends(wrapper100, wrapper200).build();
        Async.work(20,wrapper100,wrapper200).awaitFinish();
        System.out.println(add.getWorkResult());
        // 输出WorkResult{result=300, resultState=SUCCESS, ex=null}
    }
}
