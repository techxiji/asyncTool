package dependnew;


import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.callback.IWorker;
import com.jd.platform.async.worker.WorkResult;
import com.jd.platform.async.wrapper.WorkerWrapper;

import java.util.Map;

/**
 * @author wuweifeng wrote on 2019-11-20.
 */
public class DeWorker1 implements IWorker<String, User>, ICallback<String, User> {

    @Override
    public User action(String object, Map<String, WorkerWrapper> allWrappers) {
        System.out.println("deWorker1 action");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        User user0 = (User) allWrappers.get("first").getWorkResult().getResult();
        return new User(user0.getName() + " worker1 add");
    }

    @Override
    public User defaultValue() {
        return new User("default User");
    }

    @Override
    public void begin() {
        System.out.println("DeWorker1 begin " + Thread.currentThread().getName() + "- start --" + System.currentTimeMillis());
    }

    @Override
    public void result(boolean success, String param, WorkResult<User> workResult) {
        System.out.println("worker1 的结果是：" + workResult.getResult());
    }

}
