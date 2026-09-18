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
        String result = fileOperationTool.readFile(fileName);
        Assertions.assertNotNull(result);
    }

    @Test
    void writeFile() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String fileName = "编程导航.txt";
        String content = "https://www.codefather.cn 程序员编程学习交流社区";
        String result = fileOperationTool.writeFile(fileName, content);
        Assertions.assertNotNull(result);
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
