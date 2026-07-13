# Spring AI Tool Calling 学习指南

> 🎯 **学习目标**：理解 AI 模型怎么调用你的 Java 方法，能自己动手写一个完整的工具调用流程。
>
> ⚠️ **使用方式**：本文档引导你思考和动手。看到 `✍️` 标记的地方，停下来自己写，不要往下看。卡住 15 分钟再继续。

---

## 一、一句话讲清

> **让 AI 模型不只是"说话"，还能"做事"——调用你的 Java 方法来搜网页、读文件、写 PDF、执行终端命令。**

---

## 二、核心流程图

```
用户: "帮我搜一下今天北京的天气"
  │
  ▼
ChatClient ──→ AI 模型（DeepSeek/GPT）
  │               │
  │               └─ 模型判断："我需要调用 searchWeb 工具"
  │                 返回 → toolName="searchWeb", arguments={query:"北京天气"}
  │
  ▼
Spring AI 自动执行 WebSearchTool.searchWeb("北京天气")
  │
  ▼
返回搜索结果 → 回传 AI 模型 → 模型总结成自然语言
  │
  ▼
用户看到: "今天北京晴，15-25℃，适合出行..."
```

**Spring AI 做的事**：自动把 Java 方法转成 AI 能理解的 JSON Schema → 自动解析 AI 的调用请求 → 自动执行方法 → 自动把结果回传模型。

---

## 三、📚 基础补充：注解（Annotation）

> 你学了 4 个月 Java，肯定见过 `@Override`。注解本质是**给代码贴标签**，框架读取这些标签后自动执行相应逻辑。

| 概念 | 一句话 |
|------|--------|
| 注解的定义 | 用 `@interface` 关键字定义 |
| 元注解 | 修饰注解的注解，如 `@Target`（能放哪）、`@Retention`（保留到何时） |
| 注解处理器 | 框架运行时扫描注解并执行逻辑——Spring 的核心机制 |

> 🧠 **思考**：Spring 怎么"看到"你方法上的 `@Tool` 注解？提示：想想 Spring 启动时做了什么事。

---

## 四、定义工具：`@Tool` 注解

两个核心注解：

| 注解 | 作用 | 打在哪里 |
|------|------|---------|
| `@Tool` | 标记方法为 AI 可调用的工具 | 方法上 |
| `@ToolParam` | 描述工具方法的参数 | 参数上 |

### ✍️ 练习 1（🟢入门）：写一个无参工具

**需求**：创建一个 `DateTimeTool` 类，AI 可以调用它获取当前时间。

**引导**（按顺序思考，不要跳）：
1. 这个类需要什么注解让 Spring 管理它？
2. 方法需要什么注解让 AI 看到它？
3. `description` 写什么 AI 才能准确判断何时调用？
4. 返回值用什么类型？（提示：基本类型能用吗？为什么？）

```java
// TODO: 你来完成
// 思考路径：
// 第1步：类需要 Spring 管理的注解 — 是 @Component 还是 @Service？
// 第2步：方法需要 @Tool 注解 — description 怎么写？写英文还是中文？为什么？
// 第3步：方法的访问修饰符用 public 还是 private？为什么？
// 第4步：返回类型选 String 还是 void？还是 LocalDateTime？
//         基础补充：Spring AI 工具方法不能用基本类型（int/long/boolean）
//         也不能用 void、Optional、List、Map 作为返回类型
```

<details>
<summary>🆘 卡住超过 15 分钟？点击看提示（不是答案）</summary>

- 提示 1：Spring 管理 Bean 用 `@Component` 或 `@Service`
- 提示 2：AI 能调用的方法用 `@Tool(description = "...")` 标记
- 提示 3：description 建议用英文——AI 模型对英文指令理解更准确
- 提示 4：返回类型用 `String`

还是写不出来？告诉我卡在哪一步，我给你更具体的引导。

</details>

### ✍️ 练习 2（🟡进阶）：写一个带参工具

**需求**：创建一个 `WebSearchTool` 类，AI 可以调用它搜索网页。

**引导**：
1. 参数需要什么注解描述？
2. `@ToolParam` 的 `description` 写什么？`required` 默认值是什么？
3. 如果参数是可选的，怎么标记？

```java
// TODO: 你来完成
// 思考路径：
// @Tool 的 description 要写清楚触发条件，否则 AI 不知道什么时候该调用
//   错误示例："搜索"
//   正确思路：告诉 AI "什么时候该用这个工具" + "工具做什么"
// @ToolParam 的 description 告诉 AI 参数的含义和格式
```

---

## 五、注册工具：`ToolCallbacks.from()`

写完工具方法后，需要把多个工具集中注册到 Spring 容器中。

### ✍️ 练习 3（🟡进阶）：创建工具注册配置类

**需求**：创建 `ToolRegistration` 配置类，把所有工具统一注册。

**引导**：
1. 这个类需要什么注解标识它是配置类？
2. 怎么把多个 `@Tool` 对象转成 `ToolCallback[]`？
   - 提示：Spring AI 提供了静态方法 `ToolCallbacks.from()`
3. `@Bean` 方法应该返回什么类型？

```java
// TODO: 你来完成
// 思考路径：
// 第1步：配置类的注解 —— 两个选择：@Configuration 还是 @Component？
//         基础补充：@Configuration 是 @Component 的特化版，
//         它告诉 Spring "这个类里面有 @Bean 方法"
// 第2步：方法需要 @Bean 注解 —— 为什么？
//         基础补充：@Bean 方法返回的对象会被 Spring 容器管理
// 第3步：ToolCallbacks.from() 的参数是什么？它能接收几个工具对象？
// 第4步：方法的返回类型 —— ToolCallback[] 还是 List<ToolCallback>？
```

<details>
<summary>🆘 卡住了？框架提示</summary>

```java
@Configuration  // 告诉Spring：我是配置类
public class ToolRegistration {

    @Bean  // 告诉Spring：把返回值放进容器
    public ToolCallback[] 方法名自己取() {
        // 1. 先 new 你的工具对象
        // 2. 用 ToolCallbacks.from(工具1, 工具2, ...) 转成 ToolCallback[]
        // 3. return
    }
}
```
注意：这只是框架结构，具体实现你写。

</details>

> 🧠 **关键理解**：`ToolCallbacks.from()` 内部做了什么？它怎么找到哪些方法是工具方法？
>
> 答案：反射 + 注解扫描。遍历对象的所有方法 → 找带 `@Tool` 的 → 读取 description 等属性 → 生成 `ToolCallback`。

---

## 六、使用工具：传给 ChatClient

### ✍️ 练习 4（🔴挑战）：在对话中使用工具

**需求**：在 Controller 或 Service 中实现一个对话方法，让 AI 能调用注册的工具。

**引导**（这是整个流程中最关键的一步）：

```java
// TODO: 你来完成
// 思考路径：

// 第1步：怎么把 ToolRegistration 注册的工具拿过来？
//   提示：Spring 容器管理的 Bean 怎么获取？

// 第2步：ChatClient 的调用链 —— 你需要理解方法链的每一步：
//   chatClient.prompt()      // 开始一次对话
//       .user(message)       // 设置用户消息
//       .???                 // 把工具传进去（查文档或看项目代码）
//       .call()              // 发起调用
//       .???                 // 怎么拿到回复内容？

// 第3步：拿到回复后怎么返回给前端？
//   提示：.chatResponse().getResult().getOutput().getText()

// 基础补充：方法链（Fluent API / Builder 模式）
// 每个方法返回 this 或一个新 Builder，让你可以连续调用
// 这是 Builder 模式的典型应用
```

### 三种使用方式对比

| 方式 | 适用场景 |
|------|---------|
| `.toolCallbacks(allTools)` 每次传入 | 不同对话用不同工具 |
| `.defaultTools(allTools)` 全局默认 | 所有对话都用同一套工具 |
| 注入 `ToolCallbackProvider` | MCP 等动态工具场景 |

> 🧠 **思考**：你的项目应该用哪种方式？为什么？

---

## 七、三种定义方式对比

| 方式 | 写法 | 适用场景 |
|------|------|----------|
| **声明式** `@Tool` | 注解在方法上 | **最常用**，90% 的场景 |
| **编程式** `MethodToolCallback` | Builder 手动构建 | 运行时动态创建工具 |
| **函数式** `FunctionToolCallback` | 传 Function 接口 | 和已有函数式代码集成 |

> ⏭️ 编程式和函数式先跳过，等你掌握了声明式再回来看。

---

## 八、Agent 中的工具调用：ReAct 循环

> 🔴 **这是最核心、对你编程思维提升最大的部分，一定要自己理解和实现！**

### 概念

普通对话中，AI 调一次工具就结束了。Agent 需要在**多轮循环**中反复决策：

```
Step 1: 用户说"帮我创建一个网页项目"
  → Think: "我需要先创建目录" → Act: 执行 terminal("mkdir my-project")
Step 2: Think: "目录建好了，初始化 npm" → Act: 执行 terminal("npm init -y")
Step 3: Think: "任务完成了" → 返回最终回复
```

这就是 **ReAct 模式**：**Think（思考）→ Act（行动）→ Observe（观察）→ 再 Think...**

### 📚 基础补充：模板方法模式

`BaseAgent.run()` 定义骨架，`step()` 留给子类实现：

```
BaseAgent.run() {
    初始化
    while (未完成 && 步骤 < maxSteps) {
        step();   // ← 抽象方法，子类决定每一步做什么
    }
    清理
}
```

**这就是模板方法模式**：父类定流程，子类填细节。

### ✍️ 练习 5（🔴挑战）：精读 ToolCallAgent 的 think() 和 act()

> ⚠️ **这是整个项目最值得你花时间的代码。不要看答案，自己读、画图、写注释。**

**任务**：
1. 打开你的 `ToolCallAgent.java`，阅读 `think()` 和 `act()` 方法
2. 画一张流程图，描述一轮 think→act 的完整过程
3. 回答以下问题（用自己的话）：
   - `think()` 为什么返回 `boolean`？什么情况 true、什么情况 false？
   - `act()` 怎么判断 AI 是否想终止？
   - 构造函数为什么设置 `internalToolExecutionEnabled(false)`？
   - `think()` 中工具调用有结果和无结果时，消息上下文分别怎么处理？为什么不同？

> 📖 **学习建议**：在代码旁边写注释，不是翻译代码，是解释**为什么这样写**。解释不清楚的地方，就是你没真懂的地方。

---

## 九、新旧 API 对照（Spring AI 1.0 变化）

| 旧 API（已弃用） | 新 API（Spring AI 1.0+） |
|---|---|
| `FunctionCallingOptions` | `ToolCallingChatOptions` |
| `.defaultFunctions()` | `.defaultTools()` |
| `.functions()` | `.tools()` |
| `.functionCallbacks()` | `.toolCallbacks()` |
| `FunctionCallback` | `ToolCallback` |
| 包：`org.springframework.ai.model.function` | 包：`org.springframework.ai.tool` |

---

## 十、常见坑

### 1. MCP 工具用 `ToolCallbackProvider`

MCP 工具数量随连接的 Server 动态变化，不能用固定数组，必须用 `ToolCallbackProvider`：

> ✍️ 去你的项目里找到 MCP 工具的用法，对比和 `ToolCallback[]` 的区别。

### 2. 方法参数限制

不能用这些类型：
- 基本类型（用包装类 `Integer` 不用 `int`）
- `List`、`Map`、`Set`（作为参数类型）
- `Optional`、`CompletableFuture`、`Future`
- 响应式类型：`Mono`、`Flux`
- 函数式类型：`Function`、`Supplier`、`Consumer`

> 🧠 **思考**：为什么不能返回 void？提示：AI 需要看到执行结果。

### 3. description 写不好，AI 就不调用

```java
// ❌ 太模糊，AI 不知道啥时候调
@Tool(description = "搜索")

// ✅ 写清楚触发条件
@Tool(description = """
    Search for information from Baidu Search Engine.
    Call this when the user asks about current events
    or factual information that requires real-time search.
    """)
```

> ✍️ **练习**：检查你项目中所有 `@Tool` 的 description，看看有没有可以改进的。

### 4. CGLIB 代理可能导致 `@Tool` 注解扫描失败

> ⏭️ 先跳过，遇到相关问题再回来看。

---

## 十一、📚 基础补充总结

作为 4 个月 Java 学习者，理解这个项目你需要补：

| 知识点 | 重要程度 | 自检 |
|--------|---------|------|
| Spring Bean 容器 | ⭐⭐⭐⭐⭐ | 能解释 `@Bean` vs `@Component` 吗？ |
| 依赖注入 | ⭐⭐⭐⭐⭐ | `@Resource` vs `@Autowired` 什么区别？ |
| 反射机制 | ⭐⭐⭐⭐ | Java 怎么在运行时获取方法上的注解？ |
| Builder 模式 | ⭐⭐⭐⭐ | 为什么 ChatClient 用 Builder 构建？ |
| 模板方法模式 | ⭐⭐⭐⭐ | BaseAgent 用这个模式解决了什么问题？ |
| 策略模式 | ⭐⭐⭐ | ReActAgent 的 think/act 分离有什么好处？ |
| JSON | ⭐⭐⭐ | 为什么 AI 用 JSON 格式描述工具调用？ |
| Maven 多模块 | ⭐⭐⭐ | pom.xml 的 `<dependency>` 怎么管理？ |

---

## 十二、🧠 自检清单

完成后打勾：

- [ ] 能不看文档，手写一个带 `@Tool` + `@ToolParam` 的工具类
- [ ] 能写出 `ToolCallbacks.from()` 注册工具
- [ ] 能写出完整的 `chatClient.prompt()...call()` 调用链
- [ ] 能画出 ReAct 循环的 think→act→think 流程图
- [ ] 能解释 `ToolCallback[]` 和 `ToolCallbackProvider` 的区别
- [ ] 知道 `@Tool` 的 `description` 为什么重要
- [ ] 知道工具方法不能用哪些类型（基本类型、void、List、Map...）
- [ ] 能用自己的话解释 ToolCallAgent.think() 的每一行

---

## 十三、渐进式练习路线

```
🟢 第一周：入门
  ├── 练习 1：写一个无参工具（DateTimeTool）
  ├── 练习 2：写一个带参工具（WebSearchTool）
  └── 练习 3：注册工具到 Spring 容器

🟡 第二周：进阶
  ├── 练习 4：在 ChatClient 中使用工具
  ├── 读 ToolCallAgent 代码，画 think/act 流程图
  └── 对比三种工具使用方式

🔴 第三周+：挑战
  ├── 练习 5：给 ToolCallAgent.think()/act() 写解释注释
  ├── 改进 think() 的异常处理（看代码里的 Task 8 注释）
  └── 挑战：自己继承 ReActAgent，创建一个自定义 Agent
```

---

## 核心类/注解速查表

| 核心类/注解 | 作用 |
|---|---|
| `@Tool` | 标记一个方法是 AI 可调用的工具 |
| `@ToolParam` | 描述工具方法的参数 |
| `ToolCallbacks.from()` | 把 @Tool 对象转成 ToolCallback 数组 |
| `ToolCallback` | 工具的标准化接口 |
| `.toolCallbacks()` | 把工具传给 ChatClient |
| `ToolCallingManager` | 执行工具调用，管理调用循环 |
| `ToolCallbackProvider` | MCP 等外部工具的统一入口 |
| `ToolDefinition` | 工具定义（名称、描述、参数 Schema） |
