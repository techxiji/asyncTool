package com.jd.platform.async.wrapper.actionstrategy;

import com.jd.platform.async.worker.ResultState;

/**
 * 返回执行工作类型的枚举。
 *
 * @author create by TcSnZh on 2021/5/1-下午10:47
 */
public enum DependenceAction {
    /**
     * 开始工作。WorkerWrapper会执行工作方法。
     */
    START_WORK,
    /**
     * 还没轮到，休息一下。WorkerWrapper中的调用栈会返回，以等待可能发生的下次调用。
     */
    TAKE_REST,
    /**
     * 立即失败。WorkerWrapper会去执行快速失败的方法。
     */
    FAST_FAIL,
    /**
     * 交给下层{@link DependenceStrategy}进行判断。
     * 在WorkerWrapper中不需要考虑此值，因为配置正常的情况下不会返回这个值。
     */
    JUDGE_BY_AFTER;

    // 空值单例

    public WithProperty emptyProperty() {
        return empty;
    }

    private final WithProperty empty = new WithProperty() {
        @Override
        public void setResultState(ResultState resultState) {
            throw new UnsupportedOperationException("empty not support modify");
        }

        @Override
        public void setFastFailException(Exception fastFailException) {
            throw new UnsupportedOperationException("empty not support modify");
        }

        private final String toString = getDependenceAction() + ".empty";

        @Override
        public String toString() {
            return toString;
        }
    };

    // 携带异常信息、ResultState的返回值

    public WithProperty fastFailException(ResultState resultState, Exception e) {
        WithProperty withProperty = this.new WithProperty();
        withProperty.setResultState(resultState);
        withProperty.setFastFailException(e);
        return withProperty;
    }

    /**
     * 有时需要封装一些参数来返回，则使用本内部类进行返回。
     * <p/>
     * 所有的构造方法权限均为private，请在父枚举类{@link DependenceAction}的方法中选择合适的模板生成内部类WithProperty。
     */
    public class WithProperty {
        private ResultState resultState;
        private Exception fastFailException;

        // getter setter

        public ResultState getResultState() {
            return resultState;
        }

        public void setResultState(ResultState resultState) {
            this.resultState = resultState;
        }

        public Exception getFastFailException() {
            return fastFailException;
        }

        public void setFastFailException(Exception fastFailException) {
            this.fastFailException = fastFailException;
        }

        public DependenceAction getDependenceAction() {
            return DependenceAction.this;
        }

        // constructor always private.

        private WithProperty() {
        }
    }
}
