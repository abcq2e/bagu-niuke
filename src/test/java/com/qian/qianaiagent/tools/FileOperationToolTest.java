package com.qian.qianaiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 工具级测试 —— 不需要 Spring 上下文（Spring 上下文依赖数据库，单测里起不来也没必要起）。
 */
class FileOperationToolTest {

    @Test
    void readFile() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String fileName = "编程导航.txt";
        // 先确保文件存在再读 —— 否则读的是从未创建的路径，assertNotNull 恒真、测不出任何东西
        fileOperationTool.writeFile(fileName, "https://www.codefather.cn 程序员编程学习交流社区");
        String result = fileOperationTool.readFile(fileName);
        Assertions.assertTrue(result.contains("codefather.cn"), "实际: " + result);
    }

    @Test
    void writeFile() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String result = fileOperationTool.writeFile("编程导航.txt",
                "https://www.codefather.cn 程序员编程学习交流社区");
        Assertions.assertTrue(result.startsWith("File written successfully"), "实际: " + result);
    }

    @Test
    void rejectsTraversalOnRead() {
        FileOperationTool tool = new FileOperationTool();
        String result = tool.readFile("../../pom.xml");
        Assertions.assertTrue(result.startsWith("Error: 非法路径"), "实际: " + result);
    }

    @Test
    void rejectsTraversalOnWrite() {
        FileOperationTool tool = new FileOperationTool();
        String result = tool.writeFile("../escaped.txt", "x");
        Assertions.assertTrue(result.startsWith("Error: 非法路径"), "实际: " + result);
    }
}
