#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
面渣逆袭 PDF → 干净 Markdown
两步走：① 正则逐行去广告（快） ② LLM 整篇精修（好）
"""
import sys, os, re, json, time
from pathlib import Path
os.environ["PYTHONIOENCODING"] = "utf-8"
try: sys.stdout.reconfigure(encoding='utf-8')
except: pass

import fitz
import requests

ROOT = Path(__file__).resolve().parent.parent
DOC_DIR = ROOT / "src" / "main" / "resources" / "document"

# PDF 源：优先从 document 读，没有的话从 pdf-backup 读
PDF_SOURCE = DOC_DIR
pdf_list = list(DOC_DIR.glob("面渣逆袭*.pdf"))
if not pdf_list:
    PDF_SOURCE = DOC_DIR / "pdf-backup"

# ===== 读取 API 配置 =====
yml = ROOT / "src" / "main" / "resources" / "application.yml"
with open(yml, 'r', encoding='utf-8') as f:
    cfg_t = f.read()
DS_KEY = re.search(r'openai:\s*\n\s+api-key:\s*\$\{SPRING_AI_OPENAI_API_KEY:([^}]+)\}', cfg_t)
DS_KEY = DS_KEY.group(1) if DS_KEY else None
DS_URL = re.search(r'base-url:\s*\$\{SPRING_AI_OPENAI_BASE_URL:([^}]+)\}', cfg_t)
DS_URL = (DS_URL.group(1).rstrip('/')) if DS_URL else "https://api.deepseek.com"
DS_KEY = DS_KEY

# ===== 广告行模式（逐行匹配，安全）=====
# 注意：PDF 提取的文字常含异体 Unicode（如 ⼆ vs 二, ⾯ vs 面）
# 所以所有模式都用 .*? 模糊匹配
AD_LINE_PATTERNS = [
    r"图文详解.*?道.*?面试高频题.*?吊打.*?官",
    r"这次面试.*?一定吊打.*?官",
    r"整理.*?沉默王.*?",
    r"戳.*?转载.*?链接",
    r"戳.*?原文.*?链接",
    r"作者.*?三分恶",
    r"最近整理了.*?牛逼.*?资料",
    r"可以说是.*?最全.*?学习.*?PDF",
    r"微信搜.*?沉默王.*?",
    r"扫描下方二维码",
    r"关注.*?公众号",
    r"回复.*?\d+.*?即可.*?领取",
    r"戳.*?详情",
    r"分享.*?点赞.*?在看.*?星标",
    r"让天下没有难背",
    r"请允许我.*?私心",
    r"万字.*?手绘图.*?面试高频题",
    r"诚意满满",
    r"球[友朋].*?反馈",
    r"付费.*?福利",
    r"星球.*?用户",
    r"这次.*?亮白版.*?打印",
    r"暗黑版本",
    r"面渣逆袭.*?篇$",
    r"^\d+$",
    r"^-\s*\d+\s*-$",
    r"^\d+\s*/\s*\d+$",
]


def is_ad_line(line: str) -> bool:
    s = line.strip()
    if not s or len(s) < 3:
        return False
    for pat in AD_LINE_PATTERNS:
        if re.search(pat, s):
            return True
    return False


def regex_clean(text: str) -> str:
    """Phase 1: 逐行去广告"""
    lines = text.split('\n')
    keep = []
    for line in lines:
        if not is_ad_line(line):
            keep.append(line)
    result = '\n'.join(keep)
    # 合并多余空行
    result = re.sub(r'\n{4,}', '\n\n', result)
    return result.strip()


def llm_polish(text: str, title: str) -> str:
    """Phase 2: LLM 整篇精修（单次调用）"""
    if not text or len(text) < 50 or not DS_KEY:
        return text
    # 如果太长就截断（保留开头和关键部分）
    if len(text) > 10000:
        text = text[:10000] + "\n\n[···后续内容略过···]"

    try:
        r = requests.post(f"{DS_URL}/v1/chat/completions",
            headers={"Authorization": f"Bearer {DS_KEY}", "Content-Type": "application/json"},
            json={
                "model": "deepseek-chat",
                "messages": [
                    {"role": "system", "content": "你是PDF清洗专家。只输出清洗后的结果，不要解释。"},
                    {"role": "user", "content": f"""清洗以下《{title}》的PDF提取文本。

规则：
1. 删掉所有非技术知识点内容，包括但不限于：
   - 广告推广语（"吊打面试官"、"沉默王二"、"扫码关注"、"回复111"）
   - 前言/书籍推广（"多少万字多少张图"、"让天下没有难背"、"请允许我的一点私心"、"诚意满满"、"球友/星球用户/付费"等）
   - 页眉页脚、封面语、目录行、页码、文末推广
2. 保留：技术面试问答（Q/问/答序号保留）、代码、技术解释
3. 修复：断行合并、多余空格
4. 如果全段无知识点，整段删除

文本：
{text}

清洗后："""}
                ],
                "max_tokens": 4096,
                "temperature": 0.05,
            },
            timeout=90)
        r.raise_for_status()
        result = r.json()["choices"][0]["message"]["content"].strip()
        # 如果 LLM 返回空或过短，回退到正则结果
        if len(result) < len(text) * 0.2:
            return text
        return result
    except Exception as e:
        print(f"      [WARN] LLM精修失败: {e}")
        return text


def extract_topic(fname):
    name = fname.replace('.pdf', '')
    name = re.sub(r'^面渣逆袭[\s\-]*', '', name)
    name = re.split(r'篇', name)[0] if '篇' in name else name
    name = re.sub(r'V\d+\.\d+|亮白版|精装版|电子版', '', name).strip()
    return name if name else name


def process(pdf_path: Path):
    name = pdf_path.stem
    topic = extract_topic(name)
    out_name = f"面渣逆袭-{topic}.md"
    out_path = DOC_DIR / out_name

    print(f"  📄 {pdf_path.name}")

    doc = fitz.open(pdf_path)
    total = len(doc)

    # 提取所有页文本
    all_pages = []
    for i in range(total):
        text = doc[i].get_text().strip()
        if len(text) >= 15:
            all_pages.append(text)
    doc.close()

    # Phase 1: 正则清洗
    print(f"     ⚡ 正则清洗...", end="")
    cleaned_pages = [regex_clean(p) for p in all_pages]
    cleaned_pages = [p for p in cleaned_pages if len(p) > 20]
    print(f" {len(cleaned_pages)} 页")

    if not cleaned_pages:
        print("     ⚠️ 无内容")
        return

    # Phase 2: LLM 精修前段（封面/目录广告集中区）+ 尾段（底部广告）
    merged = "\n\n---\n\n".join(cleaned_pages)

    if DS_KEY:
        # 前 8000 字符
        first_part = merged[:8000]
        middle_part = merged[8000:-5000] if len(merged) > 13000 else ""
        last_part = merged[-5000:] if len(merged) > 8000 else ""

        print(f"     🧠 LLM精修前段({len(first_part)}字符)...", end="", flush=True)
        polished_first = llm_polish(first_part, topic)
        if polished_first and len(polished_first) > 50:
            merged = polished_first + middle_part + last_part
            print(f" ✅", end="")
        else:
            print(f" ➖", end="")

        # 尾段（如果和前段不重叠）
        if last_part and len(merged) > 8000:
            print(f" 尾段({len(last_part)}字符)...", end="", flush=True)
            polished_last = llm_polish(last_part, topic + "（末尾部分）")
            if polished_last and len(polished_last) > 50:
                merged = merged[:-5000] + polished_last
                print(f" ✅")
            else:
                print(f" ➖")
        else:
            print()
    else:
        print(f"     ⏩ 无API Key，跳过LLM精修")

    # 写文件
    md = f"# 面渣逆袭 —— {topic}\n\n> 来源：{pdf_path.name}\n\n" + merged
    out_path.write_text(md, encoding="utf-8")
    print(f"     ✅ {out_name} ({len(md)} 字符)")


def main():
    pdfs = sorted(PDF_SOURCE.glob("面渣逆袭*.pdf"))
    print(f"📚 {len(pdfs)} 个PDF\n")

    t0 = time.time()
    for pdf in pdfs:
        try:
            process(pdf)
        except Exception as e:
            print(f"  ❌ {pdf.name}: {e}")

    print(f"\n⚡ 总耗时 {time.time()-t0:.1f} 秒")

    # 统计
    mds = sorted(DOC_DIR.glob("面渣逆袭-*.md"))
    total_size = sum(m.stat().st_size for m in mds)
    print(f"📝 {len(mds)} 个 .md 文件, 共 {total_size/1024:.0f}KB")


if __name__ == "__main__":
    main()
