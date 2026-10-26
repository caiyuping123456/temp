"""Generate the sample DOCX files used by the merge demo.

Usage:
    python samples/generate_samples.py

Requires: python-docx, Pillow.
"""

from __future__ import annotations

from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "assets"

BRAND = RGBColor(0x1F, 0x5B, 0x99)
DEEP = RGBColor(0x17, 0x46, 0x6F)
GREY = RGBColor(0x5B, 0x64, 0x74)


def set_cell_shading(cell, color: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = tc_pr.makeelement(qn("w:shd"), {})
        tc_pr.append(shd)
    shd.set(qn("w:fill"), color)


def set_cell_text(cell, text: str, *, bold: bool = False, color: RGBColor | None = None) -> None:
    cell.text = ""
    paragraph = cell.paragraphs[0]
    run = paragraph.add_run(text)
    run.bold = bold
    run.font.size = Pt(9)
    if color is not None:
        run.font.color.rgb = color


def add_table(document: Document, headers: list[str], rows: list[list[str]]) -> None:
    table = document.add_table(rows=1, cols=len(headers))
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    for index, header in enumerate(headers):
        set_cell_text(table.rows[0].cells[index], header, bold=True, color=RGBColor(0xFF, 0xFF, 0xFF))
        set_cell_shading(table.rows[0].cells[index], "2457D8")
    for row in rows:
        cells = table.add_row().cells
        for index, value in enumerate(row):
            set_cell_text(cells[index], value)
    document.add_paragraph()


def add_bullets(document: Document, items: list[str]) -> None:
    for item in items:
        document.add_paragraph(item, style="List Bullet")


def add_numbers(document: Document, items: list[str]) -> None:
    for item in items:
        document.add_paragraph(item, style="List Number")


def build_logo() -> Path:
    ASSETS.mkdir(parents=True, exist_ok=True)
    path = ASSETS / "brand-logo.png"
    image = Image.new("RGB", (480, 240), "white")
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((24, 24, 456, 216), radius=8, fill="#1f5b99")
    draw.ellipse((52, 62, 176, 186), fill="#ffffff")
    draw.rectangle((150, 96, 420, 152), fill="#eaf1f8")
    draw.rectangle((186, 112, 360, 136), fill="#1f5b99")
    image.save(path)
    return path


def configure_document(document: Document) -> None:
    style = document.styles["Normal"]
    style.font.name = "微软雅黑"
    style.font.size = Pt(10.5)
    style.element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")
    for name in ("Title", "Heading 1", "Heading 2", "Heading 3"):
        heading = document.styles[name]
        heading.font.name = "微软雅黑"
        heading.element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")
        heading.font.color.rgb = DEEP
    document.styles["Title"].font.color.rgb = BRAND


def build_main() -> Path:
    document = Document()
    configure_document(document)

    title = document.add_paragraph("设备检测项目交付文档", style="Title")
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    subtitle = document.add_paragraph("主文档与副文档合并示例")
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    subtitle.runs[0].font.color.rgb = GREY
    subtitle.runs[0].font.size = Pt(10)

    document.add_paragraph("本文档汇总设备检测项目的检测范围、实施安排和结果说明。目录与页码可在 Word 中更新。")
    document.add_paragraph("${toc}")

    document.add_heading("项目概述", level=1)
    document.add_paragraph(
        "本项目对生产车间内的电气与暖通设备开展例行检测，重点记录运行参数、异常情况"
        "和处理建议。项目交付内容包括检测范围、现场计划、原始记录和最终报告。"
    )
    add_bullets(
        document,
        [
            "主文档包含项目概述、目录和附录。",
            "副文档提供检测范围与实施安排。",
            "合并结果保留原文档中的段落、表格、图片和样式。",
        ],
    )

    document.add_paragraph("${merge-point}")

    document.add_heading("附录：使用说明", level=1)
    document.add_paragraph(
        "在 Word 中打开合并结果后，按 Ctrl+A 再按 F9 即可刷新目录页码。"
        "如需重新生成，请返回上传页面替换文件后再次合并。"
    )

    target = ROOT / "主文档.docx"
    document.save(target)
    return target


def build_product(logo: Path) -> Path:
    document = Document()
    configure_document(document)

    document.add_heading("检测范围", level=1)
    document.add_paragraph("${product-start}")

    document.add_heading("检测对象", level=2)
    document.add_paragraph(
        "检测范围包括配电柜、控制柜、循环风机和空调机组。现场检测以运行状态下的"
        "参数记录为主，必要时补充停机检查。"
    )
    document.add_paragraph("下图为本项目标识：")
    document.add_picture(str(logo), width=Inches(2.6))

    add_table(
        document,
        ["检测对象", "检测内容", "状态"],
        [
            ["配电柜", "电压、电流、温升与绝缘状态", "待检测"],
            ["控制柜", "控制回路、指示和保护功能", "待检测"],
            ["空调机组", "送风量、回风温度和运行噪声", "待检测"],
        ],
    )

    document.add_heading("检测要求", level=2)
    add_bullets(
        document,
        [
            "记录设备铭牌、额定参数和现场编号。",
            "测量电压、电流、温度、风量等运行参数。",
            "异常项需附照片、处理建议和复测结果。",
        ],
    )
    document.add_paragraph("${product-end}")
    document.add_paragraph("这段位于标记之外，不应出现在合并结果中。")

    target = ROOT / "副文档-产品介绍.docx"
    document.save(target)
    return target


def build_plan() -> Path:
    document = Document()
    configure_document(document)

    document.add_heading("现场实施计划", level=1)
    document.add_paragraph("${plan-start}")

    document.add_heading("进度安排", level=2)
    add_numbers(
        document,
        [
            "第 1 周：收集设备清单、图纸和运行记录。",
            "第 2 周：确认停电窗口、检测人员和仪器。",
            "第 3 周：完成现场检测与原始数据记录。",
            "第 4 周：复核异常项并提交正式报告。",
        ],
    )

    add_table(
        document,
        ["阶段", "主要工作", "负责人"],
        [
            ["准备", "设备清单、检测方案、仪器校准", "项目组"],
            ["现场", "参数测量、状态记录、异常拍照", "检测组"],
            ["复核", "数据校核、异常项确认", "技术组"],
            ["交付", "检测报告、原始记录归档", "项目组"],
        ],
    )

    document.add_heading("现场注意事项", level=2)
    add_bullets(
        document,
        [
            "检测前确认设备停运或切换至安全状态。",
            "进入现场须佩戴防护用品并遵守作业许可。",
            "原始记录不得涂改，异常数据需双人确认。",
        ],
    )
    document.add_paragraph("${plan-end}")
    document.add_paragraph("这段位于标记之外，不应出现在合并结果中。")

    target = ROOT / "副文档-实施计划.docx"
    document.save(target)
    return target


def build_invalid() -> Path:
    document = Document()
    configure_document(document)
    document.add_heading("异常标记示例", level=1)
    document.add_paragraph("${broken-start}")
    document.add_paragraph("这个文档只有开始标记，没有结束标记，用于演示警告提示。")
    document.add_paragraph("${orphan-end}")
    target = ROOT / "副文档-异常标记.docx"
    document.save(target)
    return target


def main() -> None:
    logo = build_logo()
    created = [
        build_main(),
        build_product(logo),
        build_plan(),
        build_invalid(),
    ]
    for path in created:
        print(f"created: {path.relative_to(ROOT.parent)}")


if __name__ == "__main__":
    main()
