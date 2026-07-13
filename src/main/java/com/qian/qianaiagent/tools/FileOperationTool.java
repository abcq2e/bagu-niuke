package com.qian.qianaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.qian.qianaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 文件操作工具类（提供文件读写功能）
 */
public class FileOperationTool {

    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    @Tool(description = "读取指定文件的内容。使用时机：需要查看之前保存的文件内容时。")
    public String readFile(@ToolParam(description = "要读取的文件名，例如：'output.txt'、'notes.md'") String fileName) {
        String filePath = FILE_DIR + "/" + fileName;
        try {
            return FileUtil.readUtf8String(filePath);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "将内容写入文件。使用时机：需要保存分析结果、日志、或给用户生成文件时。")
    public String writeFile(@ToolParam(description = "要写入的文件名，例如：'result.txt'、'analysis.md'") String fileName,
                            @ToolParam(description = "要写入的文件内容（支持 Markdown/文本）") String content
    ) {
        String filePath = FILE_DIR + "/" + fileName;

        try {
            // 创建目录
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath);
            return "File written successfully to: " + filePath;
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }
}
