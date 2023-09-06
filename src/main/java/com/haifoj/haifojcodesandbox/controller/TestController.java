package com.haifoj.haifojcodesandbox.controller;

import com.haifoj.haifojcodesandbox.JavaNativeCodeSandbox;
import com.haifoj.haifojcodesandbox.model.ExecuteCodeRequest;
import com.haifoj.haifojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController("/test")
public class TestController {

    /**
     * 自定义 鉴权请求头和密钥
     */
    public static final String AUTH_REQUEST_HEADER = "auth";
    public static final String AUTH_REQUEST_SECRET = "secretKey";


    @Resource
    private JavaNativeCodeSandbox javaNativeCodeSandbox;

    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }


    /**
     * 执行代码
     */
    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCodeResponse(@RequestBody ExecuteCodeRequest executeCodeRequest,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) {
        // 基本认证
        String header = request.getHeader(AUTH_REQUEST_HEADER);
        if (!header.equals(AUTH_REQUEST_SECRET)) {
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }


}
