package com.jd.platform.async.scheduling.factory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 调度工厂。传入图纸生成一组wrapper。
 *
 * @author create by TcSnZh on 2021/5/17-上午1:11
 */
public abstract class AbstractSchedulingFactory {
    protected final String factoryName;

    /**
     * 无参构造，默认使用 {@code 栈信息<自增long值> } 作为工厂名
     */
    public AbstractSchedulingFactory() {
        this(Thread.currentThread().getStackTrace()[2] + "<" + defaultFactoryNameCount.getAndIncrement() + ">");
    }

    /**
     * 指定工厂名
     *
     * @param factoryName 工厂名
     */
    public AbstractSchedulingFactory(String factoryName) {
        this.factoryName = factoryName;
    }

    // ========== static ==========

    private static final AtomicLong defaultFactoryNameCount = new AtomicLong();
}
