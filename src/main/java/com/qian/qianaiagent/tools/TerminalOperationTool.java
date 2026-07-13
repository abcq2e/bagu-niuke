package com.qian.qianaiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;

/**
 * 终端操作工具
 *
 * ===== 🎯 Task 8 Part B: 安全加固 =====
 * 当前代码可以执行任意 Windows 命令（cmd.exe /c <用户输入>），极其危险！
 * 攻击者可以通过 Prompt Injection 诱导 AI 执行 destructive 命令。
 *
 * 你需要实现命令白名单机制：只允许安全命令，拒绝其他一切。
 *
 * 💡 引导问题：
 * 1. 白名单应该包含哪些命令？（安全：python, dir, echo, type, findstr, mkdir）
 * 2. 黑名单思路为什么不行？（提示：你能列举所有危险命令吗？del /f、format、shutdown...）
 * 3. 如何从完整命令中提取"命令名"？（提示："dir /s C:\\" → "dir"，用 split 取第一个）
 * 4. 白名单用什么数据结构？（提示：Set<String>，contains() 是 O(1)）
 * 5. 命令参数要不要也做限制？（比如 python -c 可以执行任意代码）
 * 6. 当命令不在白名单时，返回什么提示？
 *
 * 📖 安全原则: 默认拒绝，显式允许（Default Deny）
 * 📖 白名单命令建议：
 *    python (执行Python脚本), dir (列出目录), echo (输出文本),
 *    type (显示文件内容), findstr (搜索文本), mkdir (创建目录)
 *
 */

@Slf4j
public class TerminalOperationTool {

    private static final Set<String> ALLOWED_COMMANDS = Set.of("dir", "echo", "type", "findstr", "mkdir", "python");

    // 危险参数模式：即使命令在白名单内，带这些参数也拒绝执行
    private static final Set<String> DANGEROUS_ARG_PATTERNS = Set.of("-c", "/c");
    @Tool(description = """
            在 Windows 终端执行白名单内的安全命令。
            白名单：dir（列目录）、echo（输出文本）、type（查看文件）、findstr（文本搜索）、mkdir（创建目录）、python（运行脚本）。
            使用时机：需要读写文件、查看目录结构、运行 Python 脚本时。
            不使用时机：命令不在白名单内、或命令含危险参数（如 python -c）时一律拒绝。
            注意事项：Agent 应先确认命令在白名单内再调用，不确定时优先用其他工具替代。""")
    public String executeTerminalCommand(@ToolParam(description = "要执行的命令，例如：'dir C:\\\\Users'、'echo Hello'、'type readme.md'") String command) {
        // 第一步：空值校验
        if (command == null || command.trim().isEmpty()) {
            log.warn("收到空命令");
            return "⚠️ 命令为空，无法执行。Agent 建议：请提供要执行的命令。"
                    + "允许的命令：" + ALLOWED_COMMANDS;
        }

        // 第二步：提取命令名（"dir C:\\Users" → ["dir", "C:\\Users"] → "dir"）
        String[] parts = command.trim().split("\\s+");
        String commandName = parts[0].toLowerCase();

        // 第三步：白名单校验
        if (!ALLOWED_COMMANDS.contains(commandName)) {
            log.warn("拒绝执行非白名单命令：{}", commandName);
            return "⛔ 命令 '" + commandName + "' 不在白名单中，已被拒绝执行。"
                    + "允许的命令：" + ALLOWED_COMMANDS + "。"
                    + "Agent 建议：① 用允许的命令组合实现需求 ② 若确有需要，提示用户手动执行。";
        }

        // 第四步（进阶）：危险参数校验 —— python -c 可以执行任意代码，必须拦截
        for (int i = 1; i < parts.length; i++) {
            if (DANGEROUS_ARG_PATTERNS.contains(parts[i].toLowerCase())) {
                log.warn("拦截危险参数：{} {}", commandName, parts[i]);
                return "⛔ 命令 '" + commandName + "' 携带危险参数 '" + parts[i] + "'，已被拦截。"
                        + "Agent 建议：该参数可执行任意代码，不安全。请换一种方式完成任务。";
            }
        }

        // 第五步：执行命令
        log.info("执行终端命令：{}", command);
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("⚠️ 命令执行完毕，退出码：").append(exitCode)
                        .append("（非 0 表示执行异常）");
            }
        } catch (IOException e) {
            log.error("命令执行 IO 异常：{}", command, e);
            return "❌ 命令执行失败：" + e.getMessage()
                    + "。Agent 建议：检查命令语法是否正确，或尝试其他方式。";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("命令执行被中断：{}", command);
            return "⚠️ 命令执行被中断。Agent 建议：任务可能超时，请简化操作后重试。";
        }
        return output.toString();
    }
}
