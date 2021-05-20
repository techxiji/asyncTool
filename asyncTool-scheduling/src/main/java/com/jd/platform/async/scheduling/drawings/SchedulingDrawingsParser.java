package com.jd.platform.async.scheduling.drawings;

import com.jd.platform.async.scheduling.exception.IllegalSchedulingPropertyException;
import com.jd.platform.async.scheduling.model.SchedulingDrawingsModel;

/**
 * @author tcsnzh[zh.jobs@foxmail.com] create this in 2021/5/20-上午12:03
 */
public interface SchedulingDrawingsParser {
    /**
     * 从模型转化为图纸
     */
    SchedulingDrawings parseDrawings(SchedulingDrawingsModel model) throws IllegalSchedulingPropertyException;

    default SchedulingDrawingsParser getDefaultInstance() {
        return DefaultSchedulingDrawingsParser.instance;
    }
}
