"""轻量 OCR HTTP 服务：基于 PaddleOCR，提供图片文字识别接口。

启动：
    python main.py
    （或 uvicorn main:app --host 127.0.0.1 --port 8100）

接口：
    GET  /health  健康检查，返回引擎与模型状态
    POST /ocr     上传图片（multipart/form-data，字段名 file），返回识别出的文本

限制：
    - 图片体积上限  OCR_MAX_IMAGE_MB（默认 10MB），超限直接拒绝
    - 仅接受 jpg / jpeg / png / bmp / webp
    - 长边超过 OCR_MAX_SIDE（默认 4000）时先等比缩放，避免大图拖慢识别

表格还原：
    不额外加载模型，直接复用文本检测给出的文本框坐标，按 y 中心聚类成行、
    按 x 重叠聚类成列，把连续的多列行判定为表格并渲染成 Markdown。
    对规整的带框表格有效；无线框表格、跨行跨列合并单元格无法可靠还原。
"""

from __future__ import annotations

import io
import logging
import os
import time
from contextlib import asynccontextmanager
from dataclasses import dataclass
from statistics import median

from fastapi import FastAPI, File, Request, UploadFile
from fastapi.responses import JSONResponse
from PIL import Image, ImageOps


# ------------------------------------------------------------------ 配置

def _env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, "").strip() or default)
    except ValueError:
        return default


def _env_float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, "").strip() or default)
    except ValueError:
        return default


MAX_IMAGE_MB = _env_float("OCR_MAX_IMAGE_MB", 10.0)
MAX_IMAGE_BYTES = int(MAX_IMAGE_MB * 1024 * 1024)
MAX_SIDE = _env_int("OCR_MAX_SIDE", 4000)
OCR_LANG = os.getenv("OCR_LANG", "ch")
HOST = os.getenv("OCR_HOST", "127.0.0.1")
PORT = _env_int("OCR_PORT", 8100)
# 默认关闭 MKL-DNN：部分 CPU 上 PaddlePaddle 3.x 的 PIR 执行器会抛
# ConvertPirAttribute2RuntimeAttribute not support，关掉即可正常推理
ENABLE_MKLDNN = os.getenv("OCR_ENABLE_MKLDNN", "false").strip().lower() in ("1", "true", "yes", "on")
ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}

# PaddleOCR 3.x 默认从 HuggingFace 拉模型，国内网络下改用 ModelScope 更稳
os.environ.setdefault("PADDLE_PDX_MODEL_SOURCE", os.getenv("PADDLE_PDX_MODEL_SOURCE", "modelscope"))
# 跳过联网的模型源可用性探测，避免启动期做无谓的外部请求
os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")
if not ENABLE_MKLDNN:
    os.environ.setdefault("FLAGS_use_mkldnn", "0")

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("ocr-service")

_RESAMPLE = getattr(Image, "Resampling", Image).LANCZOS


# ------------------------------------------------------------------ 引擎

_engine = None
_engine_error: str | None = None


def get_engine():
    """惰性初始化 PaddleOCR 引擎：模型加载较重，全局只做一次。"""
    global _engine
    if _engine is None:
        from paddleocr import PaddleOCR

        logger.info("正在初始化 PaddleOCR（lang=%s），首次运行需要下载模型 ...", OCR_LANG)
        started = time.time()
        _engine = PaddleOCR(
            lang=OCR_LANG,
            # 关闭版面方向分类与矫正，纯文字识别场景更快、依赖的模型更少
            use_doc_orientation_classify=False,
            use_doc_unwarping=False,
            use_textline_orientation=False,
            enable_mkldnn=ENABLE_MKLDNN,
        )
        logger.info("PaddleOCR 初始化完成，耗时 %.1fs", time.time() - started)
    return _engine


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _engine_error
    try:
        get_engine()
    except Exception as exc:  # 启动失败不退出，交由 /health 与 /ocr 反馈原因
        _engine_error = str(exc)
        logger.exception("PaddleOCR 初始化失败：%s", exc)
    yield


app = FastAPI(title="PaddleOCR Service", version="1.0.0", lifespan=lifespan)


# ------------------------------------------------------------------ 工具

def _fail(status: int, message: str) -> JSONResponse:
    logger.warning("请求被拒绝：status=%d, message=%s", status, message)
    return JSONResponse(status_code=status, content={"code": status, "message": message, "text": ""})


def _normalize(image: Image.Image) -> Image.Image:
    """按 EXIF 摆正方向、去掉透明通道、按长边上限等比缩放。"""
    image = ImageOps.exif_transpose(image)
    if image.mode not in ("RGB", "L"):
        image = image.convert("RGB")

    width, height = image.size
    longest = max(width, height)
    if longest > MAX_SIDE:
        scale = MAX_SIDE / longest
        image = image.resize((max(1, int(width * scale)), max(1, int(height * scale))), _RESAMPLE)
        logger.info("图片超出长边上限已缩放：%dx%d -> %dx%d", width, height, image.width, image.height)
    return image


def _run_ocr(image: Image.Image) -> list[_Line]:
    """执行识别，兼容 PaddleOCR 3.x 的 predict 结果结构，保留每行文本框坐标"""
    import numpy as np

    result = get_engine().predict(np.asarray(image))
    lines: list[_Line] = []
    for item in result or []:
        data = item
        if not isinstance(data, dict):
            payload = getattr(item, "json", None)
            data = payload.get("res", {}) if isinstance(payload, dict) else {}
        rec_texts = data.get("rec_texts") or []
        rec_scores = data.get("rec_scores") or []
        # 文本框坐标是表格还原的唯一依据，缺失时退化成纯文本
        dt_polys = data.get("dt_polys") or []
        for index, text in enumerate(rec_texts):
            text = (text or "").strip()
            if not text:
                continue
            score = float(rec_scores[index]) if index < len(rec_scores) else 0.0
            poly = dt_polys[index] if index < len(dt_polys) else None
            if poly is None:
                top = float(index * 20)
                lines.append(_Line(text=text, score=score, x_min=0.0, y_min=top, x_max=1e6, y_max=top + 10))
                continue
            x_min, y_min, x_max, y_max = _poly_bbox(poly)
            lines.append(_Line(text=text, score=score, x_min=x_min, y_min=y_min, x_max=x_max, y_max=y_max))
    return lines


# ------------------------------------------------------------------ 表格还原

# 两个文本框的 x 区间重叠比例达到该值才认为属于同一列
_COLUMN_OVERLAP = 0.5
# 判定为表格所需的最少连续行数（表头 + 至少一行数据）
_TABLE_MIN_ROWS = 2


@dataclass
class _Line:
    """一行识别结果：文本、置信度与文本框（像素坐标）"""

    text: str
    score: float
    x_min: float
    y_min: float
    x_max: float
    y_max: float
    column: int = -1

    @property
    def y_center(self) -> float:
        return (self.y_min + self.y_max) / 2

    @property
    def width(self) -> float:
        return max(1.0, self.x_max - self.x_min)

    @property
    def height(self) -> float:
        return max(1.0, self.y_max - self.y_min)


@dataclass
class _Layout:
    """整页重建结果"""

    markdown: str
    table_detected: bool = False
    table_rows: int = 0
    table_cols: int = 0


def _poly_bbox(poly) -> tuple[float, float, float, float]:
    """把四点多边形压成外接矩形 (x_min, y_min, x_max, y_max)"""
    xs = [float(point[0]) for point in poly]
    ys = [float(point[1]) for point in poly]
    return min(xs), min(ys), max(xs), max(ys)


def _cluster_rows(lines: list[_Line]) -> list[list[_Line]]:
    """按 y 中心聚类成行：竖直位置相近的文本框视为同一行"""
    if not lines:
        return []
    tolerance = 0.6 * median(line.height for line in lines)
    rows: list[list[_Line]] = []
    for line in sorted(lines, key=lambda item: item.y_center):
        if rows:
            current = rows[-1]
            if abs(line.y_center - median(item.y_center for item in current)) <= tolerance:
                current.append(line)
                continue
        rows.append([line])
    for row in rows:
        row.sort(key=lambda item: item.x_min)
    return rows


def _assign_columns(lines: list[_Line]) -> int:
    """按 x 区间重叠程度把文本框归入某一列，返回列数。

    先处理较窄的文本框来确立列锚点，且锚点一经建立不再向外扩张：
    标题、备注这类横跨整幅图的宽文本框若参与锚点扩张，会把相邻列一并吞并，
    导致整张表格塌缩成一列。
    """
    anchors: list[tuple[float, float]] = []
    for line in sorted(lines, key=lambda item: (item.width, item.x_min)):
        for index, (left_edge, right_edge) in enumerate(anchors):
            left = max(line.x_min, left_edge)
            right = min(line.x_max, right_edge)
            narrower = min(line.width, max(1.0, right_edge - left_edge))
            if narrower > 0 and (right - left) / narrower >= _COLUMN_OVERLAP:
                line.column = index
                break
        else:
            anchors.append((line.x_min, line.x_max))
            line.column = len(anchors) - 1
    return len(anchors)


def _row_cells(row: list[_Line], column_count: int) -> list[str]:
    """把一行里的文本框填进列槽位，同一槽位的多段文本用空格拼接"""
    cells = [""] * column_count
    for line in row:
        existing = cells[line.column]
        cells[line.column] = f"{existing} {line.text}".strip() if existing else line.text
    return cells


def _find_table_block(cells: list[list[str]]) -> tuple[int, int] | None:
    """找出最长的、每行都至少填满两列的连续区间，即表格区域"""
    best: tuple[int, int] | None = None
    start: int | None = None

    def consider(begin: int, finish: int) -> None:
        nonlocal best
        if finish - begin < _TABLE_MIN_ROWS:
            return
        if best is None or (finish - begin) > (best[1] - best[0]):
            best = (begin, finish)

    for index, row in enumerate(cells):
        if sum(1 for cell in row if cell) >= 2:
            if start is None:
                start = index
        elif start is not None:
            consider(start, index)
            start = None
    if start is not None:
        consider(start, len(cells))
    return best


def _escape_cell(text: str) -> str:
    """转义会破坏 Markdown 表格结构的字符"""
    return text.replace("\\", "\\\\").replace("|", "\\|").replace("\n", " ")


def _markdown_table(cells: list[list[str]], block: tuple[int, int]) -> tuple[str, int, int]:
    """把表格区域渲染成 Markdown，返回 (markdown, 行数, 列数)"""
    start, end = block
    used = sorted({index for row in cells[start:end] for index, cell in enumerate(row) if cell})
    body = [
        "| " + " | ".join(_escape_cell(row[index]) for index in used) + " |"
        for row in cells[start:end]
    ]
    separator = "| " + " | ".join("---" for _ in used) + " |"
    return "\n".join([body[0], separator, *body[1:]]), end - start, len(used)


def _rebuild(lines: list[_Line]) -> _Layout:
    """按坐标把识别结果重建成整页 Markdown，表格区域渲染为 Markdown 表格"""
    if not lines:
        return _Layout(markdown="")

    rows = _cluster_rows(lines)
    column_count = _assign_columns(lines)
    cells = [_row_cells(row, column_count) for row in rows]

    block = _find_table_block(cells)
    if block is None:
        return _Layout(markdown="\n".join(" ".join(line.text for line in row) for row in rows))

    table_markdown, table_rows, table_cols = _markdown_table(cells, block)
    parts: list[str] = []
    for index, row in enumerate(rows):
        if index == block[0]:
            parts.append(table_markdown)
        if block[0] <= index < block[1]:
            continue
        parts.append(" ".join(line.text for line in row))
    return _Layout(
        markdown="\n".join(parts),
        table_detected=True,
        table_rows=table_rows,
        table_cols=table_cols,
    )


# ------------------------------------------------------------------ 中间件

@app.middleware("http")
async def reject_oversized_body(request: Request, call_next):
    """在解析 multipart 之前按 Content-Length 快速拒绝超大请求。"""
    if request.method == "POST" and request.url.path == "/ocr":
        raw_length = request.headers.get("content-length")
        if raw_length:
            try:
                # multipart 会额外带上边界与头信息，这里留 8KB 余量
                if int(raw_length) > MAX_IMAGE_BYTES + 8192:
                    return _fail(413, f"图片体积超出上限 {MAX_IMAGE_MB:g}MB")
            except ValueError:
                pass
    return await call_next(request)


# ------------------------------------------------------------------ 接口

@app.get("/health")
async def health():
    return {
        "code": 200,
        "status": "UP" if _engine is not None else "DOWN",
        "engine": "PaddleOCR",
        "lang": OCR_LANG,
        "modelSource": os.getenv("PADDLE_PDX_MODEL_SOURCE"),
        "maxImageMb": MAX_IMAGE_MB,
        "maxSide": MAX_SIDE,
        "error": _engine_error,
    }


@app.post("/ocr")
async def ocr(file: UploadFile = File(...)):
    filename = file.filename or "image"
    extension = os.path.splitext(filename)[1].lower()
    if extension not in ALLOWED_EXTENSIONS:
        return _fail(400, f"不支持的图片格式：{extension or '未知'}，仅支持 {'、'.join(sorted(ALLOWED_EXTENSIONS))}")

    data = await file.read()
    if not data:
        return _fail(400, "图片内容为空")
    if len(data) > MAX_IMAGE_BYTES:
        return _fail(413, f"图片体积 {len(data) / 1024 / 1024:.2f}MB 超出上限 {MAX_IMAGE_MB:g}MB")

    try:
        image = Image.open(io.BytesIO(data))
        image.load()
    except Exception:
        return _fail(400, "无法解析该文件，请确认是否为有效的图片")

    if _engine is None:
        return _fail(503, f"OCR 引擎尚未就绪：{_engine_error or '正在初始化，请稍后重试'}")

    image = _normalize(image)
    started = time.time()
    try:
        lines = _run_ocr(image)
    except Exception as exc:
        logger.exception("OCR 识别失败")
        return _fail(500, f"OCR 识别失败：{exc}")

    layout = _rebuild(lines)
    elapsed_ms = int((time.time() - started) * 1000)
    score_values = [line.score for line in lines]
    logger.info(
        "识别完成：file=%s, 行数=%d, 表格=%s, 耗时=%dms",
        filename,
        len(lines),
        f"{layout.table_rows}行x{layout.table_cols}列" if layout.table_detected else "无",
        elapsed_ms,
    )
    return {
        "code": 200,
        "message": "识别成功",
        "text": "\n".join(line.text for line in lines),
        "markdown": layout.markdown,
        "tableDetected": layout.table_detected,
        "tableRows": layout.table_rows,
        "tableCols": layout.table_cols,
        "lineCount": len(lines),
        "avgScore": round(sum(score_values) / len(score_values), 4) if score_values else 0.0,
        "elapsedMs": elapsed_ms,
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=HOST, port=PORT, log_level="info")
