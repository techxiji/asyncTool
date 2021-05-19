package com.jd.platform.async.callback;


import com.jd.platform.async.exception.SkippedException;
import com.jd.platform.async.worker.WorkResult;

/**
 * 每个执行单元执行完毕后，会回调该接口</p>
 * 需要监听执行结果的，实现该接口即可
 *
 * @author wuweifeng wrote on 2019-11-19.
 */
@FunctionalInterface
public interface ICallback<T, V> {
    /**
     * 任务开始的监听
     */
    default void begin() {

    }

    /**
     * 耗时操作执行完毕后，就给value注入值
     * <p/>
     * 只要Wrapper被调用后成功或失败/超时，该方法都会被执行。
     */
    void result(boolean success, T param, WorkResult<V> workResult);

    /**
     * 提供常量选项：打印异常信息，跳过时的异常{@link SkippedException}不会打印。
     */
    ICallback<?, ?> PRINT_EXCEPTION_STACK_TRACE = new ICallback<Object, Object>() {
        @Override
        public void result(boolean success, Object param, WorkResult<Object> workResult) {
            Exception ex = workResult.getEx();
            if (ex != null && !(ex instanceof SkippedException)) {
                ex.printStackTrace();
            }
        }

        @Override
        public String toString() {
            return "PRINT_EXCEPTION_STACK_TRACE";
        }
    };
}
