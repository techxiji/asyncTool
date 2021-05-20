package v15.schedulingtest.cases.case1;

import com.jd.platform.async.callback.IWorker;
import com.jd.platform.async.wrapper.WorkerWrapper;

import java.util.Map;

/**
 * @author create by TcSnZh on 2021/5/17-上午2:31
 */
class PrintParam implements IWorker<Object, Object> {
    @Override
    public Object action(Object object, Map<String, WorkerWrapper<?, ?>> allWrappers) {
        System.out.println("print param : " + object);
        return object;
    }
}
