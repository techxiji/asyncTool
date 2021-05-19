package com.jd.platform.async.scheduling;

import com.jd.platform.async.scheduling.model.SchedulingDrawingsModel;

/**
 * @author create by TcSnZh on 2021/5/17-下午7:22
 */
public interface SchedulingJsonParser {

    /**
     * 解析json为图纸对象
     *
     * @param json json
     * @return 返回图纸对象接口
     */
    SchedulingDrawingsModel parse(String json);

    /**
     * 默认实现
     */
    static SchedulingJsonParser getDefaultInstance() {
        return DefaultSchedulingJsonParser.getInstance();
    }
}
