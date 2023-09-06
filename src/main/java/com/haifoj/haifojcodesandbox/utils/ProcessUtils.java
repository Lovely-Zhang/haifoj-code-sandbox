package com.haifoj.haifojcodesandbox.utils;

import cn.hutool.core.util.StrUtil;
import com.haifoj.haifojcodesandbox.model.ExecuteMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 进程工具类
 */
public class ProcessUtils {

    /**
     * 执行进程，并获取信息
     * @param compilOrRunProcess
     * @param opName
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process compilOrRunProcess, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            // 调用waitFor()方法等待该进程完成，并返回该进程的退出值，返回值为整数：0 为正常退出
            int exitValue = compilOrRunProcess.waitFor();
            executeMessage.setExitValue(exitValue);
            // 使用InputStreamReader将compileProcess的输入流转换为字符流，并将其传递给BufferedReader构造函数，以创建一个缓冲字符输入流。
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(compilOrRunProcess.getInputStream()));
            // 正常退出
            if (exitValue == 0) {
                System.out.println(opName + "成功！退出码为：" + exitValue);
                // 获取程序的控制台输出结果
//                // 通过 StringBuilder 拼接返回结果信息
//                StringBuilder compileOutputStringBuilder = new StringBuilder();
                List<String> outputStrList = new ArrayList<>();
                // 循环，逐行读取控制台的输出
                // 注意：这里读取行时，需要重新赋值进行判断，方便拿到输出结果，
                // 执行bufferedReader.readLine()时会获取第一行的输出结果，这时指针便已经指向了第二行，
                // 在循环内再次使用 bufferedReader.readLine() 就会跳过一行数据，从而导致丢失数据
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
//                    compileOutputStringBuilder.append(compileOutputLine).append("\n");
                    outputStrList.add(compileOutputLine);
                }
//                executeMessage.setMessage(compileOutputStringBuilder.toString());
                // 拼接
                executeMessage.setMessage(StringUtils.join(outputStrList, "\n"));
            } else {
                // 异常退出
                System.out.println(opName + "失败...退出码为：" + exitValue);
                List<String> compileOutputStrList = new ArrayList<>();
                // 逐行读取
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStrList.add(compileOutputLine);
                }
                executeMessage.setMessage(StringUtils.join(compileOutputStrList, "\n"));
                // 通过标准错误流获取程序报错信息
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(compilOrRunProcess.getErrorStream()));
                List<String> errorCompileOutputStrList = new ArrayList<>();
                // 循环，逐行读取控制台的输出
                String errorComOutputLine;
                while ((errorComOutputLine = errorBufferedReader.readLine()) != null) {
                    errorCompileOutputStrList.add(errorComOutputLine);
                }
                executeMessage.setErrorMessage(StringUtils.join(errorCompileOutputStrList, "\n"));
            }
            stopWatch.stop();
            // 获取时间
            long timeMillis = stopWatch.getLastTaskTimeMillis();
            executeMessage.setTime(timeMillis);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return executeMessage;
    }

    /**
     * 执行交互式进程并获取信息（只写了成功地返回示例）
     *
     * @param runProcess
     * @param args
     * @return
     */
    public static ExecuteMessage runInteractProcessAndGetMessage(Process runProcess, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            // 向控制台输入程序
            OutputStream outputStream = runProcess.getOutputStream();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            String[] s = args.split(" ");
            String join = StrUtil.join("\n", s) + "\n";
            outputStreamWriter.write(join);
            // 相当于按了回车，执行输入的发送
            outputStreamWriter.flush();

            // 分批获取进程的正常输出
            InputStream inputStream = runProcess.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder compileOutputStringBuilder = new StringBuilder();
            // 逐行读取
            String compileOutputLine;
            while ((compileOutputLine = bufferedReader.readLine()) != null) {
                compileOutputStringBuilder.append(compileOutputLine);
            }
            executeMessage.setMessage(compileOutputStringBuilder.toString());
            // 记得资源的释放，否则会卡死
            outputStreamWriter.close();
            outputStream.close();
            inputStream.close();
            runProcess.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMessage;
    }


}
