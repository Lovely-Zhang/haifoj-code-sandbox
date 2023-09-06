package com.haifoj.haifojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.haifoj.haifojcodesandbox.model.ExecuteCodeRequest;
import com.haifoj.haifojcodesandbox.model.ExecuteCodeResponse;
import com.haifoj.haifojcodesandbox.model.ExecuteMessage;
import com.haifoj.haifojcodesandbox.model.JudgeInfo;
import com.haifoj.haifojcodesandbox.utils.ProcessUtils;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class JavaDockerCodeSandboxOld implements CodeSandbox {

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
     * 首次拉取镜像
     */
    public static final Boolean FIRST_INIT = false;

    static {
        // 初始化字典树
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(blackList);
    }


    /**
     * 测试创建文件夹，获取用户输入的代码
     */
    public static void main(String[] args) {
        JavaDockerCodeSandboxOld javaNativeCodeSandbox = new JavaDockerCodeSandboxOld();
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
        if (foundWord != null) {
            System.out.println("此代码包含黑名单词汇：" + foundWord.getFoundWord());
            return null;
        }


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
        // 代码编译后的路径(加类名)
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        switch (language) {
            case "java":
                // 返回编译后的 class 文件路径
                executeCodeResponse = getCodeFilePath(code, inputList, userCodePath, userCodeParentPath);
                break;
            case "C++":
                System.out.println("输出了C++代码");
                break;
            default:
                throw new RuntimeException("编程语言错误");
        }
        return executeCodeResponse;
    }

    private static ExecuteCodeResponse getCodeFilePath(String code, List<String> inputList, String userCodePath, String userCodeParentPath) {
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
        // 获取默认的 docker client
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        // 下载镜像，jdk轻量版本
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                /**
                 * 每下载一次就会触发一次方法
                 */
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下镜像执行状态：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("下载完成！");
        }
        // 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        // 配置容器的 主机配置
        HostConfig hostConfig = new HostConfig();
        // 使用 linux 内核机制，管理权限
        String profileConfig = ResourceUtil.readUtf8Str("/home/haif/haifoj-code-sandbox/src/main/resources/profile.json");
        hostConfig.withSecurityOpts(Collections.singletonList("seccomp=" + profileConfig));
        // 设置内存使用
        hostConfig.withMemory(0L);
        // 设置容器内存限制
        hostConfig.withMemory(100 * 1000 * 1000L);
        // 设置 cpu 核心数
        hostConfig.withCpuCount(1L);
        // 文件挂载：将主机上的文件或目录与容器内部的路径关联起来，以便容器可访问主机上的特定文件或目录
        // 参数一是：主机路径，参数二是：容器内部路径，通过 Bind 对象进行绑定
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        // 创建容器命令，并配置容器参数
        CreateContainerResponse createContainerResponse = containerCmd
                // 设置网络配置为关闭
                .withNetworkDisabled(true)
                // 限制用户不能向 root 根目录写文件
                .withReadonlyRootfs(true)
                // 设置主机配置
                .withHostConfig(hostConfig)
                // 将容器的标准错误输出连接到当前进程
                .withAttachStderr(true)
                // 将容器的标准输出连接到当前进程
                .withAttachStdout(true)
                // 启用标准输入连接，允许向容器发送输入
                .withAttachStdin(true)
                // 启用终端模式，通常用于交互式应用程序
                .withTty(true)
                // 执行容器创建命令，并将结果保存在 createContainerResponse 变量中
                .exec();
        System.out.println("容器参数：" + createContainerResponse);
        // 获取容器 id
        String containerResponseId = createContainerResponse.getId();
        // 启动容器
        dockerClient.startContainerCmd(containerResponseId).exec();
        System.out.println("容器启动成功！" + containerResponseId);

        // 3. 执行代码，得到输出结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java","-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerResponseId)
                    // 命令数组
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse);
            String createCmdResponseId = execCreateCmdResponse.getId();
            ExecuteMessage executeMessage = new ExecuteMessage();
            final boolean[] timeOut = {true};
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(){
                /**
                 * 但这个方法被调用的时候，表示操作已经完成
                 */
                @Override
                public void onComplete() {
                    // 如果执行没超时，则改为false
                    timeOut[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType type = frame.getStreamType();
                    if (type.equals(StreamType.STDERR)) {
                        // 错误输出
                        String errorMessage = new String(frame.getPayload());
                        executeMessage.setErrorMessage(errorMessage);
                        executeMessage.setExitValue(1);
                        System.out.println("输出错误结果：" + errorMessage);
                    }else {
                        // 正确输出
                        String message = new String(frame.getPayload());
                        executeMessage.setMessage(message);
                        executeMessage.setExitValue(0);
                        System.out.println("输出结果：" + message);
                    }
                    super.onNext(frame);
                }
            };
            final long[] maxMemory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerResponseId);
            ResultCallback<Statistics> resultCallback = new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    // 获取内存占用
                    System.out.println("获取占用内存：" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                }

                @Override
                public void close() {

                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }
            };
            statsCmd.exec(resultCallback);
            try {
                stopWatch.start();
                dockerClient.execStartCmd(createCmdResponseId)
                        .exec(execStartResultCallback)
                        // TIME_OUT, 最长执行时间，接着往下走不会抛异常，参数二：单位
                        // todo 实际运行会造成输出结果为空，记得先暂时去除
                        .awaitCompletion(TIME_OUT, TimeUnit.MICROSECONDS);
                stopWatch.stop();
                // 关闭
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            long lastTaskTimeMillis = stopWatch.getLastTaskTimeMillis();
            System.out.println("获取占用时间：" + lastTaskTimeMillis);
            executeMessage.setTime(lastTaskTimeMillis);
            executeMessage.setMemory(maxMemory[0]);
            // 添加进程执行信息
            executeMessageList.add(executeMessage);
        }

        // 4. 封装结果，收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        // 取用时最大值，便于判断是否超时
        long maxTime = 0;
        long maxMemory = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                // 答案错误
                executeCodeResponse.setMessage(errorMessage);
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            // 时间
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            // 内存
            Long memory = executeMessage.getMemory();
            if (memory != null) {
                maxMemory = Math.max(maxMemory, memory);
            }
        }
        // 正常运行完成
        if (executeMessageList.size() == outputList.size()) {
            executeCodeResponse.setMessage("成功");
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        // 获取执行信息，内存占用，时间占用，
        judgeInfo.setMemory(maxMemory);
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 5、文件清理
        if (userCodeFile != null) {
            boolean del = FileUtil.del(userCodeFile);
            System.out.println("文件清理" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;
    }
}