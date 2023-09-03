package com.haifoj.haifojcodesandbox.unsafe;


/**
 * 无限睡眠（阻塞程序执行）
 */
public class SleepError {

    public static void main(String[] args) throws InterruptedException {
        long ONE_HOUR = 60 * 60 * 1000L;
        System.out.println("开始睡觉了...");
        Thread.sleep(ONE_HOUR);
        System.out.println("睡完了");
    }

}
