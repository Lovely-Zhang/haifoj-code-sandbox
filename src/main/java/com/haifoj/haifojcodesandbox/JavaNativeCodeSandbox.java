package com.haifoj.haifojcodesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import com.haifoj.haifojcodesandbox.model.ExecuteCodeRequest;
import com.haifoj.haifojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Java 原生代码实现（复用模版方法）
 */
@Component
public class JavaNativeCodeSandbox extends JavaNativeCodeSandboxTemplate {

    /**
     * 要执行的类名
     */
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    /**
     * 测试创建文件夹，获取用户输入的代码
     */
    public static void main(String[] args) {
        JavaNativeCodeSandboxTemplate javaNativeCodeSandbox = new JavaNativeCodeSandboxTemplate();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs" + File.separator + GLOBAL_JAVA_CLASS_NAME, StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("testCode/unsafe/ReadFileError.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("testCode/simpleCompute" + File.separator + GLOBAL_JAVA_CLASS_NAME, StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4"));
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println("executeCodeResponse = " + executeCodeResponse);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

}
