package v15.schedulingtest.cases.case1;

import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.callback.IWorker;
import com.jd.platform.async.worker.WorkResult;
import com.jd.platform.async.wrapper.WorkerWrapper;

import java.util.Map;

/**
 * @author create by TcSnZh on 2021/5/17-上午2:04
 */
class PayTaxes implements IWorker<String, User>, ICallback<String, User> {
    /**
     * 富人（资产>1亿）收30%的税，穷人发1000块。
     */
    @Override
    public User action(String selectUserWrapperId, Map<String, WorkerWrapper<?, ?>> allWrappers) {
        User u = (User) allWrappers.get(selectUserWrapperId).getWorkResult().getResult();
        double money;
        if ((money = u.getMoney()) > 1.00 * SelectUserByName.HundredMillion) {
            u.setMoney(money * 0.7);
        } else {
            u.setMoney(money + 1000);
        }
        return u;
    }

    @Override
    public void result(boolean success, String ignore, WorkResult<User> workResult) {
        User u = workResult.getResult();
        (success ? System.out : System.err).printf("User %s has %.2f yuan .\n", u.getName(), u.getMoney());
    }
}
