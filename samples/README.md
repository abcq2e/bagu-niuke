# Samples（学习演示代码）

本目录存放从生产源码 `src/main/java` 中移出的**学习/实验性演示代码**，**不参与主项目构建**，仅作参考。

> 这些代码最初是项目早期探索不同 AI 调用方式时留下的实验，含硬编码示例，**切勿直接用于生产**。

## 目录说明

```
samples/demo/
├── AgentEvaluationDemo.java        # Agent 评估演示
├── invoke/                         # 各种 AI SDK 调用方式对比
│   ├── HttpAiInvoke.java           #   直接 HTTP 调用
│   ├── LangChainAiInvoke.java      #   LangChain4j 调用
│   ├── OllamaAiInvoke.java         #   Ollama 本地模型
│   ├── SdkAiInvoke.java            #   官方 SDK 调用
│   ├── SpringAiAiInvoke.java       #   Spring AI 调用
│   └── TestApiKey.java             #   API Key 连通性测试
└── rag/
    └── MultiQueryExpanderDemo.java # 多查询扩展 Demo（含硬编码）
```

## 如何运行

这些代码保留了原始的 `package com.qian.qianaiagent.demo.*` 声明与依赖（Spring AI / DashScope / LangChain4j）。

若要临时运行某个 demo，可将其对应 `.java` 文件复制回 `src/main/java/com/qian/qianaiagent/demo/` 目录，用主项目依赖编译运行；结束后删除即可。
