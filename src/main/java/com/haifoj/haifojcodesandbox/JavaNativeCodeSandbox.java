package com.haifoj.haifojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.haifoj.haifojcodesandbox.model.ExecuteCodeRequest;
import com.haifoj.haifojcodesandbox.model.ExecuteCodeResponse;
import com.haifoj.haifojcodesandbox.model.ExecuteMessage;
import com.haifoj.haifojcodesandbox.model.JudgeInfo;
import com.haifoj.haifojcodesandbox.security.DefaultSecurityManager;
import com.haifoj.haifojcodesandbox.security.DenySecurityManager;
import com.haifoj.haifojcodesandbox.utils.ProcessUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaNativeCodeSandbox implements CodeSandbox {

    /**
     * 临时文件名
     */
    private static final String GLOBAL_CODE_DIR_NAME = "temp";

    /**
     * 要执行的类名
     */
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    /**
     * 时间限制
     */
    private static final Long TIME_OUT = 5000L;

    /**
     * 黑名单
     */
    private static final List<String> blackList = Arrays.asList("Files", "exec", "sleep", "while");

    /**
     * 字典树
     */
    private static final WordTree WORD_TREE;

    /**
     * 类路径
     */
    private static final String SECURITY_MANAGER_PATH = "src/main/resources/security";

    /**
     * 类名
     */
    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";


    static {
        // 初始化字典树
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(blackList);
    }



    /**
     * 测试创建文件夹，获取用户输入的代码
     */
    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
//        String code = ResourceUtil.readStr("testCode/simpleComputeArgs" + File.separator + GLOBAL_JAVA_CLASS_NAME, StandardCharsets.UTF_8);
        String code = ResourceUtil.readStr("testCode/unsafe/ReadFileError.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("testCode/simpleCompute" + File.separator + GLOBAL_JAVA_CLASS_NAME, StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4"));
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println("executeCodeResponse = " + executeCodeResponse);
    }


    /**
     * 1. 核心依赖：Java 进程类 Process
     * 2. 编译代码，得到 class 文件
     * 3. 执行代码，得到输出结果
     * 4. 收集整理输出结果
     * 5. 文件清理，释放空间
     * 6. 错误处理，提升程序健壮性
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        List<String> inputList = executeCodeRequest.getInputList();

        // 权限管理，
//        System.setSecurityManager(new DenySecurityManager());

        // 使用字典树，校验代码中是否包含敏感词汇
        FoundWord foundWord = WORD_TREE.matchWord(code);
        // 判断是否包含敏感词汇
//        if (foundWord != null) {
//            System.out.println("此代码包含黑名单词汇：" + foundWord.getFoundWord());
//            return null;
//        }


        // 获取当前用户的工作目录，也就是当前项目的根目录
        String userDir = System.getProperty("user.dir");
        // 每个操作系统的分隔符都是不一样的 使用 File.separator，无论在哪个系统运行，都能正确地创建文件
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断当前目录是否存在，没有则新建
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        // 把用户的代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        // 2. 编译代码，得到 class 文件
        // 在 userCodePath 文件中，写入用户代码字符串，设置路径，设置文件字符集
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        // 拼接要输入的命令，占位符方法为获取文件的完整路径无论是相对路径还是绝对路径
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            // 使用 Process 类在终端执行命令
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println("executeMessage = " + executeMessage);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 3. 执行代码，得到输出结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String input : inputList) {
            // 设置 -Xmx256m，限制最大堆空间，为256m。设置 -Dfile.encoding=UTF-8，不同系统系统下都能正确输出中文
            // %s;%s -Djava.security.manager=MySecurityManager，指定Java安全管理器
//            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, input);
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s:%s -Djava.security.manager=%s Main %s",
                    userCodeParentPath, SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME, input);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 超时控制，使用守护线程：守护线程在JVM关闭时会自动结束
                // 如果此进程时间结束程序仍没有运行完成，则销毁程序、运行超时
                Thread thread = getThread(runProcess);
                thread.start();
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
//                ExecuteMessage executeMessage = ProcessUtils.runInteractProcessAndGetMessage(runProcess, input);
                System.out.println("executeMessage = " + executeMessage);
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                return getErrorResponse(e);
            }
        }
        // 4. 收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        // 取用时最大值，便于判断是否超时
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                // 答案错误
                executeCodeResponse.setMessage(errorMessage);
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
        }
        // 正常运行完成
        if (executeMessageList.size() == outputList.size()) {
            executeCodeResponse.setMessage("成功");
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        // 要借助第三方库来获取内存占用，非常麻烦，此处不做实现
        // judgeInfo.setMemory();
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 5、文件清理
        if (userCodeFile != null) {
            boolean del = FileUtil.del(userCodeFile);
            System.out.println("文件清理" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;
    }

    private static Thread getThread(Process runProcess) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(TIME_OUT);
                System.out.println("超时了...中断");
                runProcess.destroy();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        // 设置线程为守护线程
        thread.setDaemon(true);
        return thread;
    }


    private ExecuteCodeResponse getErrorResponse(Throwable throwable) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setMessage(throwable.getMessage());
        // 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        executeCodeResponse.setOutputList(new ArrayList<>());
        return executeCodeResponse;
    }

}
