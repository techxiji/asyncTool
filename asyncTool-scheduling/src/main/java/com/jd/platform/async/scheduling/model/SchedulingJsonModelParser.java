package com.jd.platform.async.scheduling.model;

import com.jd.platform.async.scheduling.exception.IllegalSchedulingPropertyException;

/**
 * @author create by TcSnZh on 2021/5/17-下午7:22
 */
public interface SchedulingJsonModelParser {
    /**
     * 解析json为配置模型对象
     *
     * @param json json
     * @return 返回图纸对象接口
     */
    SchedulingDrawingsModel parseToModel(String json) throws IllegalSchedulingPropertyException;

    /**
     * 默认实现
     */
    static SchedulingJsonModelParser getDefaultInstance() {
        return DefaultSchedulingJsonModelParser.instance;
    }
}
