package com.haifoj.haifojcodesandbox;


import com.haifoj.haifojcodesandbox.model.ExecuteCodeRequest;
import com.haifoj.haifojcodesandbox.model.ExecuteCodeResponse;

/**
 * 实现代码沙箱的不同调用
 */
public interface CodeSandbox {

    /**
     * 代码沙箱接口
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);

}
