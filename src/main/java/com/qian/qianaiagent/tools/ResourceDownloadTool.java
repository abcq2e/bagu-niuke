package com.qian.qianaiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.qian.qianaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

/**
 * 资源下载工具
 */
public class ResourceDownloadTool {

    @Tool(description = "从指定 URL 下载资源文件。使用时机：用户需要下载图片、文档、或其他静态资源时。不使用时机：URL 需要认证、资源过大（>100MB）时。")
    public String downloadResource(@ToolParam(description = "资源 URL，例如：'https://example.com/chart.png'") String url, @ToolParam(description = "保存的文件名，例如：'chart.png'、'report.pdf'") String fileName) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        String filePath = fileDir + "/" + fileName;
        try {
            // 创建目录
            FileUtil.mkdir(fileDir);
            // 使用 Hutool 的 downloadFile 方法下载资源
            HttpUtil.downloadFile(url, new File(filePath));
            return "Resource downloaded successfully to: " + filePath;
        } catch (Exception e) {
            return "Error downloading resource: " + e.getMessage();
        }
    }
}
