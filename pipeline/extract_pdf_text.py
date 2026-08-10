#!/usr/bin/env python3
"""提取PDF文本，保存为临时文件，供手动清洗"""
import sys, os, json, re
from pathlib import Path

os.environ["PYTHONIOENCODING"] = "utf-8"
sys.stdout.reconfigure(encoding='utf-8')

import fitz

DOC_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "document"
OUT_DIR = Path(__file__).resolve().parent.parent / "pipeline" / "extracted"

OUT_DIR.mkdir(parents=True, exist_ok=True)

# 只处理一个小的PDF做示范
pdf_files = sorted(DOC_DIR.glob("面渣逆袭*.pdf"))
print(f"找到 {len(pdf_files)} 个PDF文件")

# 先处理最小的PDF
pdf_files.sort(key=lambda p: p.stat().st_size)
for pdf in pdf_files:
    size_mb = pdf.stat().st_size / (1024*1024)
    print(f"  {pdf.name} ({size_mb:.1f}MB)")

# 处理最小的
target = pdf_files[0]  # 面渣逆袭-分布式篇.pdf 最小
print(f"\n处理: {target.name}")
doc = fitz.open(target)
print(f"共 {len(doc)} 页")

all_pages = []
for i in range(len(doc)):
    text = doc[i].get_text()
    all_pages.append({"page": i+1, "text": text[:2000]})  # 每页前2000字符

# 保存
out_path = OUT_DIR / f"{target.stem}_extracted.json"
with open(out_path, "w", encoding="utf-8") as f:
    json.dump({"filename": target.name, "total_pages": len(doc), "pages": all_pages}, f, ensure_ascii=False, indent=2)

print(f"已保存到 {out_path}")

# 同时也保存纯文本
txt_path = OUT_DIR / f"{target.stem}_full.txt"
with open(txt_path, "w", encoding="utf-8") as f:
    for i in range(len(doc)):
        f.write(f"\n{'='*60}\n")
        f.write(f"第 {i+1} 页\n")
        f.write(f"{'='*60}\n\n")
        f.write(doc[i].get_text())

print(f"纯文本已保存到 {txt_path}")
print(f"\n文件大小: {target.stat().st_size / 1024:.1f}KB")
doc.close()
