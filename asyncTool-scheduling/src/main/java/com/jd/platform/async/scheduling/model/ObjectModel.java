package com.jd.platform.async.scheduling.model;

import com.jd.platform.async.callback.ICallback;
import com.jd.platform.async.wrapper.strategy.depend.DependOnUpWrapperStrategy;
import com.jd.platform.async.wrapper.strategy.depend.DependenceStrategy;
import com.jd.platform.async.wrapper.strategy.skip.SkipStrategy;

import java.util.HashMap;
import java.util.Map;

/**
 * @author tcsnzh[zh.jobs@foxmail.com] create this in 2021/5/19-下午7:51
 */
@SuppressWarnings("unused")
public class ObjectModel {
    protected String constObjectName;
    protected String className;
    protected Long sameObjectId;
    protected Map<String, Object> properties;

    public ObjectModel() {
    }

    public String getConstObjectName() {
        return constObjectName;
    }

    public void setConstObjectName(String constObjectName) {
        this.constObjectName = constObjectName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Long getSameObjectId() {
        return sameObjectId;
    }

    public void setSameObjectId(Long sameObjectId) {
        this.sameObjectId = sameObjectId;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public static Map<String, Object> getConstObjects() {
        return constObjects;
    }

    // static constants

    private static final Map<String, Object> constObjects;

    static {
        constObjects = new HashMap<>(16);
        constObjects.put("NOT_SKIP", SkipStrategy.NOT_SKIP);
        constObjects.put("CHECK_ONE_LEVEL", SkipStrategy.CHECK_ONE_LEVEL);
        constObjects.put("ALL_DEPENDENCIES_ALL_SUCCESS", DependenceStrategy.ALL_DEPENDENCIES_ALL_SUCCESS);
        constObjects.put("ALL_DEPENDENCIES_ANY_SUCCESS", DependenceStrategy.ALL_DEPENDENCIES_ANY_SUCCESS);
        constObjects.put("ALL_DEPENDENCIES_NONE_FAILED", DependenceStrategy.ALL_DEPENDENCIES_NONE_FAILED);
        constObjects.put("SUCCESS_CONTINUE", DependOnUpWrapperStrategy.SUCCESS_CONTINUE);
        constObjects.put("SUCCESS_START_INIT_CONTINUE", DependOnUpWrapperStrategy.SUCCESS_START_INIT_CONTINUE);
        constObjects.put("PRINT_EXCEPTION_STACK_TRACE", ICallback.PRINT_EXCEPTION_STACK_TRACE);
    }

    public static <T> T getConstObject(String name) {
        //noinspection unchecked
        return (T) constObjects.get(name);
    }
}
