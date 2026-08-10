package com.qian.qianaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class QuizAppTest {

    @Resource
    private QuizApp quizApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是程序员鱼皮，今天想被考考Java并发";
        String answer = quizApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第二轮
        message = "继续考察我，这次问Spring相关的吧";
        answer = quizApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第三轮
        message = "我刚说了我想被考察什么来着？帮我回忆一下";
        answer = quizApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doQuizReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是程序员鱼皮，请考察我对数据结构与算法的掌握程度";
        QuizApp.QuizReport quizReport = quizApp.doQuizReport(message, chatId);
        Assertions.assertNotNull(quizReport);
    }

    @Test
    void doQuizWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "请根据你的知识库考考我Spring框架的核心概念";
        String answer = quizApp.doQuizWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        testMessage("帮我搜一下最近有哪些Java技术大会");
        testMessage("看看编程导航网站（codefather.cn）上有什么学习资源推荐");
        testMessage("帮我下载一张Java技术栈的架构图");
        testMessage("执行Python3脚本来生成一份Java学习路线图");
        testMessage("把我的学习计划保存为文件");
        testMessage("生成一份'AI知识考察报告'PDF，包含我的知识点掌握情况");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = quizApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }
}
