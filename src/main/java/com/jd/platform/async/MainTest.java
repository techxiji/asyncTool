package com.jd.platform.async;

import com.jd.platform.async.wrapper.WorkerWrapper;

/**
 * @author XC
 * @descrption
 * @date 2023/3/25 0:07
 */
public class MainTest {
    public static void main(String[] args) {
        WorkerWrapper.Builder<String, String> wrapper = new WorkerWrapper.Builder<>();
        System.out.println(wrapper);
    }
}
