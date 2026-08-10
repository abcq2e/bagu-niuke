#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
面渣逆袭 PDF → 干净 Markdown
策略: 提取全文 → 按段分批 LLM 清洗 → 去广告/页眉/封面 → 输出 .md
图片: DashScope VL 分析含知识点的图片
"""
import sys, os, re, json, base64, time
from pathlib import Path

os.environ["PYTHONIOENCODING"] = "utf-8"
sys.stdout.reconfigure(encoding='utf-8')

import fitz
import requests

ROOT = Path(__file__).resolve().parent.parent
DOC_DIR = ROOT / "src" / "main" / "resources" / "document"

# 读取 API Key
yml = ROOT / "src" / "main" / "resources" / "application.yml"
with open(yml, 'r', encoding='utf-8') as f: cfg_text = f.read()

deepseek_key = re.search(r'openai:\s*\n\s+api-key:\s*\$\{SPRING_AI_OPENAI_API_KEY:([^}]+)\}', cfg_text)
deepseek_key = deepseek_key.group(1) if deepseek_key else None
deepseek_url = re.search(r'base-url:\s*\$\{SPRING_AI_OPENAI_BASE_URL:([^}]+)\}', cfg_text)
deepseek_url = (deepseek_url.group(1).rstrip('/')) if deepseek_url else "https://api.deepseek.com"
dash_key = re.search(r'dashscope:\s*\n\s+api-key:\s*\$\{SPRING_AI_DASHSCOPE_API_KEY:([^}]+)\}', cfg_text)
dash_key = dash_key.group(1) if dash_key else None

print(f"🔑 DeepSeek: {'✅' if deepseek_key else '❌'} | DashScope: {'✅' if dash_key else '❌'}")

# ============== LLM 调用 ==============
def llm_clean(batch_text: str, title: str, is_first: bool = False) -> str:
    """调用 DeepSeek 清洗一段文本"""
    if not batch_text or len(batch_text.strip()) < 30:
        return ""

    extra = ""
    if is_first:
        extra = "特别注意：删除封面广告语（如'吊打面试官'、'沉默王二'等）、目录行"

    try:
        r = requests.post(f"{deepseek_url}/v1/chat/completions",
            headers={"Authorization": f"Bearer {deepseek_key}", "Content-Type": "application/json"},
            json={
                "model": "deepseek-chat",
                "messages": [
                    {"role": "system", "content": "你是一个PDF技术文档清洗专家。只输出清洗结果，不要解释。"},
                    {"role": "user", "content": f"""清洗以下《{title}》PDF文本。{extra}

规则：
- 删除：封面语、广告（"吊打面试官"、"沉默王二"、"牛逼资料"、"扫码关注"等）、页眉页脚、页码、目录行、无关链接
- 保留：技术面试问答（保留原始序号）、代码、技术解释、知识点
- 修复：合并断行、去掉多余空格
- 输出纯知识点内容，行与行之间保留适当间距

文本：
{batch_text[:12000]}

清洗后："""}
                ],
                "max_tokens": 4096,
                "temperature": 0.05,
            },
            timeout=90)
        r.raise_for_status()
        return r.json()["choices"][0]["message"]["content"].strip()
    except Exception as e:
        print(f"    [WARN] LLM调用失败: {e}")
        return batch_text


def analyze_img(img_bytes: bytes) -> str:
    """DashScope VL 判断图片"""
    if not dash_key: return ""
    try:
        b64 = base64.b64encode(img_bytes).decode()
        r = requests.post("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            headers={"Authorization": f"Bearer {dash_key}", "Content-Type": "application/json"},
            json={
                "model": "qwen-vl-max",
                "messages": [{"role": "user", "content": [
                    {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{b64}"}},
                    {"type": "text", "text": "这张图片包含技术知识点吗？如有请描述具体内容。如只是装饰/Logo/背景请回答：无"}
                ]}],
                "max_tokens": 1000,
            },
            timeout=30)
        r.raise_for_status()
        desc = r.json()["choices"][0]["message"]["content"].strip()
        return "" if "无" in desc[:5] else desc
    except:
        return ""


def extract_topic(fname):
    name = fname.replace('.pdf','')
    name = re.sub(r'^面渣逆袭[\s\-]*','',name)
    name = re.split(r'篇',name)[0] if '篇' in name else name
    name = re.sub(r'V\d+\.\d+|亮白版|精装版', '', name).strip()
    return name if name else "默认"


# ============== 主处理 ==============
def process(pdf_path: Path) -> bool:
    name = pdf_path.stem
    topic = extract_topic(name)
    out_name = f"面渣逆袭-{topic}.md"
    out_path = DOC_DIR / out_name

    print(f"\n{'='*60}")
    print(f"  📄 {pdf_path.name}  →  {out_name}")
    print(f"{'='*60}")

    doc = fitz.open(pdf_path)
    total = len(doc)
    print(f"  📖 {total} 页")

    # 提取所有文本
    all_pages_text = []
    for i in range(total):
        t = doc[i].get_text().strip()
        if t and len(t) > 15:
            all_pages_text.append((i+1, t))

    # 分批清洗（5~10页一批，共分 N 批）
    BATCH_SIZE = 8
    batches = []
    for i in range(0, len(all_pages_text), BATCH_SIZE):
        batch = all_pages_text[i:i+BATCH_SIZE]
        combined = "\n\n".join([f"[PAGE {p}]\n{t}" for p, t in batch])
        batches.append((batch[0][0], combined))  # (start_page, text)

    print(f"  📦 {len(batches)} 批待清洗")

    clean_contents = []
    for idx, (start_page, text) in enumerate(batches):
        is_first = (start_page <= 3)
        print(f"    ⏳ 第{start_page}页起 清洗中...", end="", flush=True)
        cleaned = llm_clean(text, name, is_first)
        time.sleep(0.3)
        if cleaned and len(cleaned) > 20:
            clean_contents.append(cleaned)
            print(f" ✅ {len(cleaned)}字")
        else:
            print(" ➖ 跳过（无知识点）")

    # 图片处理（仅对≤ 30页的PDF做图片分析，大PDF跳过因为效率低）
    image_descs = []
    if total <= 50:
        print(f"  🖼️  图片分析中...")
        for i in range(total):
            page = doc[i]
            images = page.get_images(full=True)
            for img in images:
                try:
                    xref = img[0]
                    pix = fitz.Pixmap(doc, xref)
                    if pix.width < 80 or pix.height < 80:
                        continue
                    try:
                        bbox = page.get_image_bbox(pix)
                        if bbox and (bbox[1] < doc[0].rect.height * 0.04 or bbox[1] > doc[0].rect.height * 0.95):
                            continue
                    except: pass

                    img_bytes = pix.tobytes("png")
                    desc = analyze_img(img_bytes)
                    time.sleep(0.3)
                    if desc:
                        image_descs.append(f"> 📊 第{i+1}页图示：{desc}")
                        print(f"    📷 第{i+1}页: ✅ 含知识点")
                except: pass

    doc.close()

    if not clean_contents:
        print("  ⚠️  无内容")
        return False

    # 组装 Markdown
    md = f"# 面渣逆袭 —— {topic}\n\n> 来源：{pdf_path.name}\n\n"
    md += "\n\n---\n\n".join(clean_contents)
    if image_descs:
        md += "\n\n---\n\n## 图表说明\n\n" + "\n\n".join(image_descs)

    out_path.write_text(md, encoding="utf-8")
    print(f"\n  ✅ 完成！{len(clean_contents)} 块知识点 + {len(image_descs)} 张图表")
    return True


def main():
    pdfs = sorted(DOC_DIR.glob("面渣逆袭*.pdf"), key=lambda p: p.stat().st_size)
    print(f"📚 {len(pdfs)} 个PDF")

    ok = fail = 0
    for pdf in pdfs:
        try:
            if process(pdf): ok += 1
            else: fail += 1
        except KeyboardInterrupt: print("\n⚠️ 中断"); break
        except Exception as e: print(f"\n[ERROR] {pdf.name}: {e}"); fail += 1

    print(f"\n📊 {ok} 成功, {fail} 失败")

if __name__ == "__main__":
    main()
