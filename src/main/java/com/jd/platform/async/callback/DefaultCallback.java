package com.jd.platform.async.callback;

import com.jd.platform.async.exception.SkippedException;
import com.jd.platform.async.worker.WorkResult;

/**
 * 默认回调类，如果不设置的话，会默认给这个回调
 *
 * @author wuweifeng wrote on 2019-11-19.
 */
public class DefaultCallback<T, V> implements ICallback<T, V> {
    @Override
    public void begin() {

    }

    /**
     * 默认将打印存在的非{@link com.jd.platform.async.exception.SkippedException}的异常。
     */
    @Override
    public void result(boolean success, T param, WorkResult<V> workResult) {
        Exception ex = workResult.getEx();
        if (ex != null && !(ex instanceof SkippedException)) {
            ex.printStackTrace();
        }
    }

}
