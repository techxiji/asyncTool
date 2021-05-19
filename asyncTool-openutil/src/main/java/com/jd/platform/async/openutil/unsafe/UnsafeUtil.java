package com.jd.platform.async.openutil.unsafe;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * @author create by TcSnZh on 2021/5/16-下午4:36
 */
@SuppressWarnings({"AlibabaAbstractClassShouldStartWithAbstractNaming", "unused"})
abstract class UnsafeUtil {
    private static final Unsafe unsafe;

    static {
        Field theUnsafe;
        try {
            theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        } catch (NoSuchFieldException e) {
            throw new Error(e);
        }
        theUnsafe.setAccessible(true);
        try {
            unsafe = (Unsafe) theUnsafe.get(null);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    @SuppressWarnings("FinalStaticMethod")
    public static final Unsafe getUnsafe() {
        return unsafe;
    }
}
