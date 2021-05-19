package com.jd.platform.async.scheduling;

import com.jd.platform.async.scheduling.model.SchedulingDrawingsModel;

/**
 * @author create by TcSnZh on 2021/5/17-上午1:22
 */
public class DefaultSchedulingJsonParser implements SchedulingJsonParser {


    // ========== singleton instance ==========

    /**
     * 私有构造方法，需要通过{@link #getInstance()}方法获取单例。
     */
    private DefaultSchedulingJsonParser() {
    }

    /**
     * 获取单例
     */
    public static DefaultSchedulingJsonParser getInstance() {
        return instance;
    }

    private static final DefaultSchedulingJsonParser instance = new DefaultSchedulingJsonParser();

    // ========== public methods ==========

    @Override
    public SchedulingDrawingsModel parse(String json) {



        // todo
        return null;
    }

    // ========== util methods ==========
}
