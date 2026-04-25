import json
import os
import sys

os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")
os.environ.setdefault("OMP_NUM_THREADS", "1")
os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")


def fail(message):
    print(json.dumps({"error": message}, ensure_ascii=False))
    sys.exit(2)


try:
    from paddleocr import PaddleOCR
except Exception as exc:
    fail("未安装 PaddleOCR：" + str(exc))


def create_ocr():
    candidates = [
        {"lang": "ch", "use_textline_orientation": True},
        {"lang": "ch"},
    ]
    last_error = None
    for kwargs in candidates:
        try:
            return PaddleOCR(**kwargs)
        except Exception as exc:
            last_error = exc
    raise RuntimeError("无法初始化 PaddleOCR：" + str(last_error))


def parse_ocr_result(result):
    page_texts = []
    confidences = []
    for block in result or []:
        if isinstance(block, dict):
            rec_texts = block.get("rec_texts") or []
            rec_scores = block.get("rec_scores") or []
            for index, text in enumerate(rec_texts):
                if text:
                    page_texts.append(str(text))
                    if index < len(rec_scores):
                        try:
                            confidences.append(float(rec_scores[index]))
                        except Exception:
                            pass
            continue
        if isinstance(block, list):
            for line in block:
                if not isinstance(line, list) or len(line) < 2:
                    continue
                payload = line[1]
                if not isinstance(payload, (list, tuple)) or not payload:
                    continue
                text = payload[0]
                if text:
                    page_texts.append(str(text))
                if len(payload) > 1:
                    try:
                        confidences.append(float(payload[1]))
                    except Exception:
                        pass
    return page_texts, confidences


def main():
    if len(sys.argv) < 2:
        fail("缺少 OCR 图片输入")
    try:
        ocr = create_ocr()
    except Exception as exc:
        fail("OCR 引擎初始化失败：" + str(exc))
    all_pages = []
    all_confidences = []
    for image_path in sys.argv[1:]:
        try:
            try:
                result = ocr.ocr(image_path, cls=True)
            except TypeError:
                result = ocr.ocr(image_path)
        except Exception as exc:
            fail("OCR 识别失败：" + str(exc))
        page_texts, confidences = parse_ocr_result(result)
        if page_texts:
            all_pages.append("\n".join(page_texts))
        all_confidences.extend(confidences)
    confidence = sum(all_confidences) / len(all_confidences) if all_confidences else 0
    print(json.dumps({"text": "\n\n".join(all_pages), "confidence": confidence}, ensure_ascii=False))


if __name__ == "__main__":
    main()
