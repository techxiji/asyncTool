package com.jd.platform.async.scheduling.exception;

/**
 * @author tcsnzh[zh.jobs@foxmail.com] create this in 2021/5/20-下午4:59
 */
public class IllegalSchedulingPropertyException extends IllegalSchedulingException {

    public IllegalSchedulingPropertyException(String message) {
        super(message);
    }

    public IllegalSchedulingPropertyException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * json反序列化失败
     *
     * @param json  json
     * @param cause 原因
     * @return 返回异常
     */
    public static IllegalSchedulingPropertyException deserializeJsonFailed(String json,
                                                                           Throwable cause) {
        return new IllegalSchedulingPropertyException(
                "Json deserialize to model failed , please check properties and see QuickStart.md . the json is : " + json,
                cause
        );
    }

    /**
     * 如果json反序列化有效，但是不符合规范。
     *
     * @param illegalFieldName 无效的属性名
     * @param fieldValue       属性的值
     * @param reason           原因
     * @param cause            引发异常，可以为null
     * @return 返回异常
     */
    public static IllegalSchedulingPropertyException illegalFieldParameter(String illegalFieldName,
                                                                           Object fieldValue,
                                                                           String reason,
                                                                           Throwable cause) {
        return new IllegalSchedulingPropertyException(
                "Property" + illegalFieldName + " does not conform to specification. value is : " + fieldValue
                        + " . because " + reason, cause
        );
    }

    public static IllegalSchedulingPropertyException illegalFieldParameter(String illegalFieldName,
                                                                           Object fieldValue,
                                                                           String reason) {
        return illegalFieldParameter(illegalFieldName, fieldValue, reason, null);
    }
}
