package com.jd.platform.async.worker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 执行结果
 */
@Builder
@Data
@AllArgsConstructor
public class WorkResult<V> {
    /**
     * 执行的结果
     */
    private V result;
    /**
     * 结果状态
     */
    private ResultState resultState;
    /**
     * 异常信息
     */
    private Exception ex;

    /**
     * 带结果&状态的构造器
     *
     * @param result
     * @param resultState
     */
    public WorkResult(V result, ResultState resultState) {
        this(result, resultState, null);
    }

    /**
     * 默认结果
     *
     * @param <V>
     * @return
     */
    public static <V> WorkResult<V> defaultResult() {
        return (WorkResult<V>) WorkResult.builder().result(null).resultState(ResultState.DEFAULT).build();
    }
}
