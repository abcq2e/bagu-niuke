package com.qian.qianaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class QuizDocumentLoaderTest {

    @Resource
    private QuizDocumentLoader quizDocumentLoader;

    @Test
    void loadDocuments() {
        quizDocumentLoader.loadDocuments();
    }

    @Test
    void loadMarkdowns() {
        quizDocumentLoader.loadMarkdowns();
    }

    @Test
    void loadPdfs() {
        quizDocumentLoader.loadPdfs();
    }
}
