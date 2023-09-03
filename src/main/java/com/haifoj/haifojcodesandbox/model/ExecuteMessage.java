package com.haifoj.haifojcodesandbox.model;

import lombok.Data;

/**
 * 进程执行信息
 */
@Data
public class ExecuteMessage {

    /**
     * 退出码
     */
    private Integer exitValue;

    /**
     * 执行信息
     */
    private String message;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 程序执行时间
     */
    private Long time;

}
