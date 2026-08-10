#!/usr/bin/env python3
"""
面渣逆袭 PDF → Markdown 转换工具
====================================
使用 PyMuPDF 提取 PDF 文本和图片
使用 DeepSeek Chat 清洗文本（去除非知识内容）
使用 DashScope VL 分析有价值图片

输出: 干净的 .md 知识库文件，放入 document/ 目录
"""

import os
import sys
import json
import base64
import time
import re
from pathlib import Path
from typing import Optional

import fitz  # PyMuPDF
import requests

# ============================================================
# 配置
# ============================================================
PROJECT_ROOT = Path(__file__).resolve().parent.parent
DOCUMENT_DIR = PROJECT_ROOT / "src" / "main" / "resources" / "document"
OUTPUT_DIR = PROJECT_ROOT / "src" / "main" / "resources" / "document"

# ============================================================
# API 配置 — 从 application.yml 读取（不硬编码在脚本中）
# ============================================================
def load_api_config() -> dict:
    """从 application.yml 读取 API 配置"""
    yml_path = PROJECT_ROOT / "src" / "main" / "resources" / "application.yml"
    config = {"deepseek_key": None, "deepseek_base_url": "https://api.deepseek.com",
              "dashscope_key": None}

    try:
        with open(yml_path, "r", encoding="utf-8") as f:
            content = f.read()

        # 提取 DeepSeek 配置
        m = re.search(r'openai:\s*\n\s+api-key:\s*\$\{SPRING_AI_OPENAI_API_KEY:([^}]+)\}', content)
        if m:
            config["deepseek_key"] = m.group(1)
        m = re.search(r'base-url:\s*\$\{SPRING_AI_OPENAI_BASE_URL:([^}]+)\}', content)
        if m:
            config["deepseek_base_url"] = m.group(1).rstrip('/')

        # 提取 DashScope 配置
        m = re.search(r'dashscope:\s*\n\s+api-key:\s*\$\{SPRING_AI_DASHSCOPE_API_KEY:([^}]+)\}', content)
        if m:
            config["dashscope_key"] = m.group(1)

    except Exception as e:
        print(f"[WARN] 读取 application.yml 失败: {e}")

    return config


API_CONFIG = load_api_config()
DEEPSEEK_API_KEY = API_CONFIG["deepseek_key"]
DEEPSEEK_BASE_URL = API_CONFIG["deepseek_base_url"]
DASHSCOPE_API_KEY = API_CONFIG["dashscope_key"]

# ============================================================
# LLM 调用
# ============================================================

def call_deepseek(prompt: str, system_prompt: str = None, max_tokens: int = 4096) -> Optional[str]:
    """调用 DeepSeek Chat API"""
    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    messages.append({"role": "user", "content": prompt})

    try:
        resp = requests.post(
            f"{DEEPSEEK_BASE_URL}/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
                "Content-Type": "application/json",
            },
            json={
                "model": "deepseek-chat",
                "messages": messages,
                "max_tokens": max_tokens,
                "temperature": 0.1,
            },
            timeout=60,
        )
        resp.raise_for_status()
        data = resp.json()
        return data["choices"][0]["message"]["content"].strip()
    except Exception as e:
        print(f"  [WARN] DeepSeek 调用失败: {e}")
        return None


def call_dashscope_vl(image_base64: str) -> Optional[str]:
    """调用 DashScope 通义千问 VL 分析图片"""
    try:
        resp = requests.post(
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {DASHSCOPE_API_KEY}",
                "Content-Type": "application/json",
            },
            json={
                "model": "qwen-vl-max",
                "messages": [
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "image_url",
                                "image_url": {"url": f"data:image/png;base64,{image_base64}"},
                            },
                            {
                                "type": "text",
                                "text": "请判断这张图片是否包含技术知识点（如代码、架构图、流程图、"
                                        "数据表、示意图、UML图、数据结构图等）。\n"
                                        "如果包含：请详细描述其中的所有技术内容，包括文字、结构、关系。\n"
                                        "如果不包含（如仅为装饰、Logo、背景、照片、页眉页脚等）：请回答「无知识内容」。\n"
                                        "注意：只输出描述内容或「无知识内容」，不要输出其他文字。",
                            },
                        ],
                    }
                ],
                "max_tokens": 2000,
            },
            timeout=60,
        )
        resp.raise_for_status()
        data = resp.json()
        return data["choices"][0]["message"]["content"].strip()
    except Exception as e:
        print(f"  [WARN] DashScope VL 调用失败: {e}")
        return None


# ============================================================
# PDF 处理
# ============================================================

# 已知的无知识点关键词模式（用于预过滤）
HEADER_FOOTER_PATTERNS = [
    r"^面渣逆袭.*篇.*$",
    r"^Java[^，。]*面试题[^，。]*$",
    r"^\d+\s*$",  # 纯数字（页码）
    r"^-\s*\d+\s*-$",  # - N -
    r"^\d+\s*/\s*\d+$",  # N/M
    r"^面渣逆袭$",
]


def is_decorative_image(page, image_bbox, page_width, page_height) -> bool:
    """判断图片是否为装饰性元素"""
    x0, y0, x1, y1 = image_bbox
    img_w = x1 - x0
    img_h = y1 - y0

    # 太小
    if img_w < 50 or img_h < 50:
        return True

    # 处于页面边缘（页眉/页脚区域）
    if y0 < page_height * 0.05 or y1 > page_height * 0.95:
        return True

    # 极端的宽高比（装饰条）
    if img_w / img_h > 10 or img_h / img_w > 10:
        return True

    return False


def extract_images_from_page(page, pdf_name: str, page_num: int) -> list:
    """提取页面中的图片，分析知识相关性"""
    results = []
    page_width = page.rect.width
    page_height = page.rect.height

    image_list = page.get_images(full=True)
    for img_idx, img_info in enumerate(image_list):
        xref = img_info[0]
        base_image = page.parent.extract_image(xref)
        if not base_image:
            continue

        image_bytes = base_image["image"]
        img_ext = base_image["ext"]

        # 获取图片在页面上的位置
        try:
            pix = fitz.Pixmap(page.parent, xref)
            bbox = page.get_image_bbox(pix)
        except:
            bbox = None

        # 跳过装饰图片
        if bbox and is_decorative_image(page, bbox, page_width, page_height):
            continue

        # 对图片进行 base64 编码，调用 DashScope VL
        image_b64 = base64.b64encode(image_bytes).decode("utf-8")
        description = call_dashscope_vl(image_b64)

        if description and "无知识内容" not in description:
            results.append({
                "page": page_num,
                "description": description,
            })
            print(f"  📷 第{page_num}页 图片{img_idx}: 含知识点描述已提取")
        else:
            print(f"  📷 第{page_num}页 图片{img_idx}: 跳过（装饰/无知识内容）")

    return results


def clean_page_text_with_llm(page_text: str, page_num: int, pdf_name: str) -> Optional[str]:
    """使用 LLM 清洗单页文本，返回干净的知识点内容"""
    if not page_text or len(page_text.strip()) < 20:
        return None

    system_prompt = "你是一个PDF文本清洗专家。你的任务是从PDF提取的文本中筛选出技术知识点相关内容。"

    user_prompt = f"""以下是从技术面试PDF「{pdf_name}」第{page_num}页提取的原始文本。请执行：

1. 删除所有非知识内容：封面、目录、页眉（如"面渣逆袭"）、页脚、页码、品牌标语、空白行
2. 保留技术面试问答知识点内容（面试问题、答案、代码示例、技术解释、关键概念）
3. 修复段落中多余空格和断行，保持连贯
4. 保留原始问答中的标记如 Q/A、问/答 等格式
5. 如果该页没有技术知识点相关内容，请输出空字符串

=== 原始文本开始 ===
{page_text}
=== 原始文本结束 ===

清洗后的文本（只含知识点）："""

    cleaned = call_deepseek(user_prompt, system_prompt, max_tokens=4096)
    if not cleaned:
        return page_text  # 兜底：返回原文

    return cleaned


def process_pdf(pdf_path: Path) -> bool:
    """处理单个 PDF 文件"""
    pdf_name = pdf_path.stem
    output_name = extract_output_name(pdf_name)
    output_path = OUTPUT_DIR / f"{output_name}.md"

    print(f"\n{'='*60}")
    print(f"📄 处理: {pdf_path.name}")
    print(f"📝 输出: {output_path.name}")
    print(f"{'='*60}")

    # 提取 topic 用于分类
    topic = extract_topic(pdf_name)
    print(f"🏷️  分类: 面渣逆袭 | 主题: {topic}")

    try:
        doc = fitz.open(pdf_path)
    except Exception as e:
        print(f"  [ERROR] 无法打开 PDF: {e}")
        return False

    total_pages = len(doc)
    print(f"📖 共 {total_pages} 页")

    all_cleaned_content = []
    all_image_descriptions = []

    for page_num in range(total_pages):
        page = doc[page_num]
        page_text = page.get_text()

        # 预过滤：空页跳过
        if not page_text or len(page_text.strip()) < 20:
            continue

        # 打印进度
        if (page_num + 1) % 10 == 0 or page_num == 0:
            print(f"  正在处理第 {page_num+1}/{total_pages} 页...")

        # 第1步：LLM 清洗文本
        cleaned = clean_page_text_with_llm(page_text, page_num + 1, pdf_path.stem)
        if cleaned and cleaned.strip():
            all_cleaned_content.append(f"<!-- page {page_num + 1} -->\n\n{cleaned}")

        # 第2步：提取和分析图片
        images = extract_images_from_page(page, pdf_path.stem, page_num + 1)
        for img in images:
            if img["description"] not in all_image_descriptions:
                all_image_descriptions.append(f"> 📎 第{img['page']}页图表：{img['description']}")

        # 避免 API 限流，稍作延迟
        time.sleep(0.3)

    doc.close()

    # 组装 Markdown
    md_content = build_markdown(pdf_path.stem, topic, all_cleaned_content, all_image_descriptions)

    # 写入文件
    output_path.write_text(md_content, encoding="utf-8")
    print(f"\n✅ 完成！共 {len(all_cleaned_content)} 页内容 + {len(all_image_descriptions)} 张图片描述")
    print(f"   保存至: {output_path}")
    return True


def extract_output_name(pdf_name: str) -> str:
    """从 PDF 文件名提取输出的 Markdown 文件名"""
    # 规范化格式：面渣逆袭-{topic}.md
    name = pdf_name

    # 移除版本号
    name = re.sub(r'V\d+\.\d+', '', name)
    name = re.sub(r'亮白版|精装版|电子版', '', name)

    # 提取主题
    topic = extract_topic(pdf_name)
    if topic:
        return f"面渣逆袭-{topic}"
    else:
        return f"面渣逆袭-{name.replace('.pdf', '')}"


def extract_topic(pdf_name: str) -> str:
    """从 PDF 文件名提取主题名称"""
    name = pdf_name.replace('.pdf', '')

    # 移除前缀 "面渣逆袭"
    topic = re.sub(r'^面渣逆袭[\s\-]*', '', name)

    # 移除"篇"字及其后的内容（包含篇后缀）
    topic = re.split(r'篇', topic)[0] if '篇' in topic else topic

    # 移除版本号
    topic = re.sub(r'V\d+\.\d+', '', topic).strip()

    # 移除其他后缀
    topic = re.sub(r'亮白版|精装版|电子版', '', topic).strip()

    return topic if topic else "default"


def build_markdown(pdf_name: str, topic: str, contents: list, image_descs: list) -> str:
    """组装最终的 Markdown 内容"""
    lines = []
    lines.append(f"# 面渣逆袭 —— {topic}")
    lines.append("")
    lines.append(f"> 来源：{pdf_name}.pdf")
    lines.append("")

    # 合并内容和图片描述
    # 策略：将图片描述插入到对应的内容页之后
    content_by_page = {}
    for content in contents:
        # 提取页码
        page_match = re.search(r'<!-- page (\d+) -->', content)
        if page_match:
            page_num = int(page_match.group(1))
            content_by_page[page_num] = content

    # 按页码排序合并
    img_by_page = {}
    for desc in image_descs:
        page_match = re.search(r'第(\d+)页', desc)
        if page_match:
            page_num = int(page_match.group(1))
            if page_num not in img_by_page:
                img_by_page[page_num] = []
            img_by_page[page_num].append(desc)

    # 按页码输出
    for page_num in sorted(set(list(content_by_page.keys()) + list(img_by_page.keys()))):
        if page_num in content_by_page:
            # 移除注释标记，只保留内容
            clean_content = re.sub(r'<!-- page \d+ -->\n\n', '', content_by_page[page_num])
            lines.append(clean_content)
            lines.append("")

        if page_num in img_by_page:
            for desc in img_by_page[page_num]:
                lines.append(desc)
                lines.append("")

        lines.append("---")
        lines.append("")

    return "\n".join(lines)


def main():
    """主函数"""
    print("=" * 60)
    print("  面渣逆袭 PDF → Markdown 转换工具")
    print(f"  文档目录: {DOCUMENT_DIR}")
    print("=" * 60)

    pdf_files = sorted(DOCUMENT_DIR.glob("面渣逆袭*.pdf"))
    if not pdf_files:
        print("未找到任何「面渣逆袭」PDF 文件！")
        # 打印所有 PDF 文件
        all_pdfs = list(DOCUMENT_DIR.glob("*.pdf"))
        if all_pdfs:
            print(f"找到其他 PDF: {[p.name for p in all_pdfs]}")
        return

    print(f"\n共找到 {len(pdf_files)} 个 PDF 文件:")
    for p in pdf_files:
        print(f"  - {p.name}")

    success = 0
    failed = 0

    for pdf_path in pdf_files:
        try:
            if process_pdf(pdf_path):
                success += 1
            else:
                failed += 1
        except KeyboardInterrupt:
            print("\n\n⚠️  用户中断！")
            break
        except Exception as e:
            print(f"\n[ERROR] 处理失败: {e}")
            failed += 1

    print(f"\n{'='*60}")
    print(f"📊 处理完成: {success} 成功, {failed} 失败")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
