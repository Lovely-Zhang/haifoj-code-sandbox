package com.haifoj.haifojcodesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeRequest {

    /**
     * 代码输入
     */
    private String code;

    /**
     * 编程语言
     */
    private String language;

    /**
     * 组测试 输入输出用例
     */
    private List<String> inputList;

}
