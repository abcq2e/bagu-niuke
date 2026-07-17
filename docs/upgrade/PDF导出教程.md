# 📄 对话 PDF 导出教程

> Task 15 | 难度 ⭐⭐⭐ | 涉及文件: `ExportController.java`

---

## 一、为什么要学文件导出？

### 面试场景

面试官："做过文件相关的功能吗？"
你："做过对话记录的 PDF 导出，用 iText 生成 PDF 直接写入 HttpServletResponse 输出流，支持中文。还做了文件下载接口，加了路径穿越防护。"
面试官："导出大数据量时内存溢出怎么办？"
你："用流式生成，边生成边写入 response，不把所有数据加载到内存。"

### 你到底要做什么

1. 完成对话导出为 PDF 的 API
2. 完成文件下载 API（含路径安全校验）

---

## 二、基础知识

### HTTP 文件下载原理

```
普通 JSON 接口:
  response.setContentType("application/json")
  response.getWriter().write(jsonString)

文件下载接口:
  response.setContentType("application/pdf")                    ← 告诉浏览器是 PDF
  response.setHeader("Content-Disposition", "attachment; filename=chat.pdf")  ← 触发下载
  response.getOutputStream().write(pdfBytes)                    ← 写入文件字节
```

**Content-Disposition 的两个值:**
- `attachment` → 浏览器弹出下载对话框（你用的）
- `inline` → 浏览器中预览

### iText 基础回顾

项目中已有 `PDFGenerationTool`，你需要**参考它**（不是复制它）来理解 API。

> 🧠 **引导**：打开 `PDFGenerationTool.java`，读懂以下问题：
> 1. `PdfWriter` 的构造函数可以接收什么参数？（文件路径？OutputStream？）
> 2. 中文怎么显示？（找 `PdfFontFactory` 相关代码）
> 3. `Document` 加了内容后需要调什么方法？（`close()` 做了什么？）

### 对话格式设计

> 🧠 **思考**：每条消息怎么在 PDF 中区分用户和 AI？
> - 简单方案：不同颜色的 Paragraph
> - 更好方案：用 Table 包裹，左边框带颜色（像聊天界面）

---                                         

## 三、你的任务                           

### 子任务 1: 导出对话为 PDF

**文件**: `controller/ExportController.java` Part A

> 🧠 **第 1 步：获取对话数据**
> - `chatMemory.getConversation(chatId)` 返回 `List<Message>`
> - 如果返回空列表 → 怎么处理？

> 🧠 **第 2 步：遍历消息列表，区分用户和 AI**
> - 怎么区分？提示：`message.getMessageType()` 返回什么？
> - 在 PDF 中如何标识？每条消息前加标签

> 🧠 **第 3 步：设置响应头**
> - `response.setContentType(?)` — 告诉浏览器这是 PDF
> - `response.setHeader("Content-Disposition", ?)` — 触发下载
> - ⚠️ 中文文件名需要 URL 编码！`URLEncoder.encode(文件名, "UTF-8")`

> 🧠 **第 4 步：生成 PDF 写入 response**
> - `PdfWriter` 的构造函数应该传什么？（提示：不是文件路径！）
> - 怎么设中文字体？
> - 每条消息用什么元素？（Paragraph？Table？）
> - 标题怎么写？
> - 最后调什么方法？（`document.close()` 做了什么？）
>
> 📚 **基础补充**：`document.close()` 不仅关闭 Document，还会自动 flush 缓冲区到 OutputStream。所以 close 之后 PDF 内容才会真正写入 response。

---

### 子任务 2: 文件下载

**文件**: `controller/ExportController.java` Part B

> 🧠 **路径穿越防护**（重要！面试加分项）

**什么是路径穿越攻击？**
用户传 `filePath=../../../etc/passwd` → 读取系统敏感文件。

> 🧠 **引导**：你打算怎么防御？
> 1. 检查路径中是否包含 `..`
> 2. 只允许下载特定目录下的文件（如 `FileConstant.FILE_SAVE_DIR`）
> 3. 用 `Paths.get(baseDir).resolve(filePath).normalize()` 然后检查是否仍在 baseDir 下
>
> 📚 **基础补充**：
> - `Paths.get()` — 创建 Path 对象
> - `.resolve()` — 拼接路径
> - `.normalize()` — 规范化（去掉 `..` 和 `.`）
> - `.startsWith(baseDirPath)` — 检查是否还在安全目录内

> 🧠 **文件下载实现**
> - 检查文件是否存在 → 不存在返回什么？
> - `Files.copy(file.toPath(), response.getOutputStream())`
> - `response.flushBuffer()`

---

## 四、验证方法

1. 先和 AI 对话几轮（获取一个 chatId）
2. `GET /api/export/pdf?chatId=xxx` → 浏览器自动下载 PDF
3. 打开 PDF → 看到格式化的对话记录
4. 测试路径穿越防护：`GET /api/export/download?filePath=../../application.yml` → 被拦截

---

## 五、面试追问

- 导出大数据量时怎么避免 OOM？（流式写入，边查边写，不全加载到内存）
- 如果同时有 100 个人导出 PDF，CPU 扛得住吗？（异步处理 + 排队 + 限流）
- PDF 中的中文乱码怎么解决？（嵌入中文字体，如 STSongStd-Light 或 simsun.ttf）

---

## 🧠 自检清单

- [ ] 能画出 HTTP 文件下载的完整流程（请求头 → 响应头 → 响应体）
- [ ] 能解释 `Content-Disposition` 两个值的区别
- [ ] 能解释路径穿越攻击的原理和防御方法
- [ ] 知道 iText 中怎么处理中文
- [ ] 知道 `document.close()` 的必要性
