"""
PaddleOCR HTTP 服务
用法：pip install paddleocr fastapi uvicorn && python paddle_ocr_server.py
端口：8866
"""
from fastapi import FastAPI, File, UploadFile
from paddleocr import PaddleOCR
import uvicorn
import os

app = FastAPI(title="PaddleOCR Service")

# 初始化 PaddleOCR（首次加载稍慢，后续调用很快）
ocr = PaddleOCR(use_angle_cls=True, lang='ch', show_log=False)


@app.post("/ocr")
async def ocr_image(file: UploadFile = File(...)):
    """识别图片中的文字，返回所有检测到的文本"""
    # 读取上传的图片
    image_bytes = await file.read()
    temp_path = f"/tmp/ocr_{hash(image_bytes)}.png"
    with open(temp_path, "wb") as f:
        f.write(image_bytes)

    # OCR 识别
    result = ocr.ocr(temp_path, cls=True)
    os.remove(temp_path)  # 清理临时文件

    # 提取所有识别到的文字
    texts = []
    if result and result[0]:
        for line in result[0]:
            text = line[1][0]       # 识别文字
            confidence = line[1][1]  # 置信度
            texts.append(f"{text} [{confidence:.1%}]")

    return {"text": "\n".join(texts), "lines": len(texts)}


@app.get("/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8866)
