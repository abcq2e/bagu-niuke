#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""并行提取PDF图片 + DashScope VL分析（5倍速）"""
import sys, os, re, base64, json
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed

import fitz, requests

ROOT = Path(__file__).resolve().parent.parent
DOC_DIR = ROOT / "src" / "main" / "resources" / "document"
PDF_DIR = DOC_DIR / "pdf-backup"

yml = open(ROOT / "src" / "main" / "resources" / "application.yml", 'r', encoding='utf-8').read()
KEY = re.search(r'DASHSCOPE_API_KEY:([^}\s]+)', yml)
KEY = KEY.group(1) if KEY else None

def analyze_one(b64):
    for _ in range(2):
        try:
            r = requests.post("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                headers={"Authorization": f"Bearer {KEY}", "Content-Type": "application/json"},
                json={"model": "qwen-vl-max", "messages": [{"role": "user", "content": [
                    {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{b64}"}},
                    {"type": "text", "text": "这张图有技术知识点吗？如有用一句话描述。如装饰/Logo/二维码：无"}
                ]}], "max_tokens": 300}, timeout=30)
            d = r.json()["choices"][0]["message"]["content"].strip()
            if d and not d.startswith("无") and len(d) > 5: return d
        except: pass
    return None

def extract_pdf_images(pdf_path):
    """提取PDF中所有图片并分析，返回[(page, desc)]"""
    items = []
    doc = fitz.open(pdf_path)
    total = len(doc)
    results = []

    for i in range(total):
        page = doc[i]
        for img in page.get_images(full=True):
            try:
                pix = fitz.Pixmap(doc, img[0])
                if pix.width < 80 or pix.height < 80: continue
                try:
                    b = page.get_image_bbox(pix)
                    if b and (b[1] < doc[0].rect.height*0.04 or b[1] > doc[0].rect.height*0.95): continue
                except: pass
                results.append((i+1, base64.b64encode(pix.tobytes("png")).decode()))
            except: pass
    doc.close()
    print(f"     提取了 {len(results)} 张图片, 正在分析...", flush=True)

    # 并行分析
    analyzed = []
    with ThreadPoolExecutor(max_workers=5) as pool:
        futures = {pool.submit(analyze_one, b64): p for p, b64 in results}
        for f in as_completed(futures):
            p = futures[f]
            d = f.result()
            if d: analyzed.append((p, d))

    analyzed.sort()
    return analyzed

def process_one(pdf_path):
    name = pdf_path.stem
    t = re.sub(r'^面渣逆袭[\s\-]*', '', name)
    t = re.split(r'篇', t)[0] if '篇' in t else t
    t = re.sub(r'V\d+\.\d+|亮白版|精装版|电子版', '', t).strip()
    md_name = f"面渣逆袭-{t}.md"
    md_path = DOC_DIR / md_name
    if not md_path.exists(): return 0

    print(f"  [PDF] {pdf_path.name} ({pdf_path.stat().st_size//1024}KB)")
    items = extract_pdf_images(pdf_path)
    if not items: return print("    -> 无知识图片") or 0

    # 去重插入
    seen = set()
    extras = []
    for p, d in items:
        if d not in seen: seen.add(d); extras.append(f"> [图] 第{p}页：{d}")

    content = md_path.read_text("utf-8")
    content += "\n\n---\n\n## 图表说明\n\n" + "\n\n".join(extras)
    md_path.write_text(content, "utf-8")
    print(f"    -> {len(extras)} 张图已插入", flush=True)
    return len(extras)

def main():
    print(f"[API] DashScope: {'YES' if KEY else 'NO'}")
    pdfs = sorted(PDF_DIR.glob("*.pdf"))
    total = 0
    for pdf in pdfs:
        try: total += process_one(pdf) or 0
        except Exception as e: print(f"  [ERROR] {pdf.name}: {e}")
    print(f"\n[DONE] 共 {total} 张知识图片")

if __name__ == "__main__":
    main()
