package com.qian.qianaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.qian.qianaiagent.constant.FileConstant;
import com.qian.qianaiagent.util.SafePathResolver;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Path;

/**
 * 文件操作工具类（提供文件读写功能）
 * <p>
 * 🔴 对外暴露给 LLM，入参不可信 —— 所有路径都必须经 {@link SafePathResolver} 收进 FILE_ROOT。
 */
public class FileOperationTool {

    private static final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";
    private static final Path FILE_ROOT = Path.of(FILE_DIR);

    @Tool(description = "读取指定文件的内容。使用时机：需要查看之前保存的文件内容时。")
    public String readFile(@ToolParam(description = "要读取的文件名，例如：'output.txt'、'notes.md'") String fileName) {
        try {
            Path filePath = SafePathResolver.resolveWithin(FILE_ROOT, fileName);
            return FileUtil.readUtf8String(filePath.toString());
        } catch (IllegalArgumentException e) {
            return "Error: 非法路径 — " + e.getMessage();
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "将内容写入文件。使用时机：需要保存分析结果、日志、或给用户生成文件时。")
    public String writeFile(@ToolParam(description = "要写入的文件名，例如：'result.txt'、'analysis.md'") String fileName,
                            @ToolParam(description = "要写入的文件内容（支持 Markdown/文本）") String content
    ) {
        try {
            Path filePath = SafePathResolver.resolveWithin(FILE_ROOT, fileName);
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath.toString());
            return "File written successfully to: " + filePath;
        } catch (IllegalArgumentException e) {
            return "Error: 非法路径 — " + e.getMessage();
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }
}
