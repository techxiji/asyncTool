package v15.schedulingtest.cases.case1;

import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.callback.IWorker;
import com.jd.platform.async.worker.WorkResult;
import com.jd.platform.async.wrapper.WorkerWrapper;
import v15.schedulingtest.cases.case1.User;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author create by TcSnZh on 2021/5/17-上午1:33
 */
class SelectUserByName implements IWorker<String, User>, ICallback<String, User> {
    public static final long HundredMillion = 100000000;
    private static final Map<String, User> datasource;

    static {
        datasource = new ConcurrentHashMap<>();
        User pony = new User("Pony", 50, 612.0 * HundredMillion);
        User jackMa = new User("JackMa", 57, 475.0 * HundredMillion);
        User me = new User("tcsnzh", 20, 0.01);
        datasource.put(pony.getName(), pony);
        datasource.put(jackMa.getName(), jackMa);
        datasource.put(me.getName(), me);
    }

    private static final AtomicInteger queryCount = new AtomicInteger();

    /**
     * 查询用户
     */
    @Override
    public User action(String param, Map<String, WorkerWrapper<?, ?>> allWrappers) {
        return datasource.get(param);
    }

    @Override
    public void result(boolean success, String param, WorkResult<User> workResult) {
        (success ? System.out : System.err).println(
                "query[" + queryCount.getAndIncrement() + "] " +
                        "with parameter " + param +
                        (success ? " succeeded" : " failed") +
                        ". workResult is " + workResult);
    }
}
