package com.jd.platform.async.scheduling.model;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.jd.platform.async.scheduling.exception.IllegalSchedulingPropertyException;

import static com.alibaba.fastjson.parser.Feature.*;

/**
 * @author create by TcSnZh on 2021/5/17-上午1:22
 */
class DefaultSchedulingJsonModelParser implements SchedulingJsonModelParser {
    // ========== singleton instance ==========

    static final DefaultSchedulingJsonModelParser instance = new DefaultSchedulingJsonModelParser();

    // ========== public methods ==========

    @Override
    public SchedulingDrawingsModel parseToModel(String json) throws IllegalSchedulingPropertyException {
        try {
            return JSONObject.parseObject(json, SchedulingDrawingsModel.class, DEFAULT_FEATURES);
        } catch (Exception e) {
            throw IllegalSchedulingPropertyException.deserializeJsonFailed(json, e);
        }
    }

    static Feature[] DEFAULT_FEATURES = {
            AllowComment, AllowUnQuotedFieldNames, AllowSingleQuotes, SafeMode, ErrorOnEnumNotMatch
    };

    // ========== util methods ==========
}
