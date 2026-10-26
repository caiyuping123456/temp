"""生成测试用的 DOCX 文件。"""

from __future__ import annotations

from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Inches, Pt
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "assets"


def set_cell_shading(cell, color: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = tc_pr.makeelement(qn("w:shd"), {})
        tc_pr.append(shd)
    shd.set(qn("w:fill"), color)


def set_cell_text(cell, text: str, *, bold: bool = False) -> None:
    cell.text = ""
    paragraph = cell.paragraphs[0]
    run = paragraph.add_run(text)
    run.bold = bold
    run.font.size = Pt(9)


def add_table(document: Document, headers: list[str], rows: list[list[str]]) -> None:
    table = document.add_table(rows=1, cols=len(headers))
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    for index, header in enumerate(headers):
        set_cell_text(table.rows[0].cells[index], header, bold=True)
        set_cell_shading(table.rows[0].cells[index], "D9E2F3")
    for row in rows:
        cells = table.add_row().cells
        for index, value in enumerate(row):
            set_cell_text(cells[index], value)
    document.add_paragraph()


def add_bullets(document: Document, items: list[str]) -> None:
    for item in items:
        document.add_paragraph(item, style="List Bullet")


def build_diagram() -> Path:
    ASSETS.mkdir(parents=True, exist_ok=True)
    path = ASSETS / "equipment-diagram.png"
    image = Image.new("RGB", (760, 360), "white")
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(r"C:\Windows\Fonts\msyh.ttc", 18)
    small = ImageFont.truetype(r"C:\Windows\Fonts\msyh.ttc", 14)

    draw.rectangle((36, 44, 724, 304), outline="#9aa3ad", width=2)
    draw.text((52, 54), "1 号车间东侧", fill="#333333", font=font)

    boxes = {
        "配电柜": (72, 104, 202, 168),
        "控制柜": (238, 104, 368, 168),
        "空调机组": (504, 92, 674, 156),
        "循环风机": (504, 202, 674, 266),
    }
    for label, box in boxes.items():
        draw.rectangle(box, fill="#f7f8f9", outline="#555f6a", width=2)
        x1, y1, x2, y2 = box
        text_box = draw.textbbox((0, 0), label, font=small)
        width = text_box[2] - text_box[0]
        height = text_box[3] - text_box[1]
        draw.text(
            ((x1 + x2 - width) / 2, (y1 + y2 - height) / 2 - 2),
            label,
            fill="#222222",
            font=small,
        )

    draw.line((202, 136, 238, 136), fill="#555f6a", width=2)
    draw.line((368, 136, 504, 124), fill="#555f6a", width=2)
    draw.line((368, 150, 504, 234), fill="#555f6a", width=2)
    draw.text((52, 318), "图 1  设备布置示意", fill="#444444", font=small)
    image.save(path)
    return path


def configure_document(document: Document) -> None:
    normal = document.styles["Normal"]
    normal.font.name = "宋体"
    normal.font.size = Pt(10.5)
    normal.element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")

    for name in ("Title", "Heading 1", "Heading 2", "Heading 3"):
        heading = document.styles[name]
        heading.font.name = "黑体"
        heading.font.size = Pt(14 if name != "Title" else 18)
        heading.element.rPr.rFonts.set(qn("w:eastAsia"), "黑体")
        heading.font.color.rgb = None

    document.styles["Normal"].paragraph_format.space_after = Pt(6)
    document.styles["Normal"].paragraph_format.line_spacing = 1.35


def build_main() -> Path:
    document = Document()
    configure_document(document)

    title = document.add_paragraph("一号车间设备检测报告", style="Title")
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    add_table(
        document,
        ["受检单位", "生产保障部", "检测日期", "2026-10-19 至 2026-10-21"],
        [
            ["编制", "张明", "审核", "李工"],
            ["检测地点", "一号车间", "记录人", "王磊"],
        ],
    )

    document.add_paragraph("${toc}")
    document.add_heading("检测概况", level=1)
    document.add_paragraph(
        "本次检测对象为一号车间东侧的配电柜、控制柜、循环风机和空调机组。"
        "检测安排在交接班期间进行，现场设备按作业票要求停电或切换至安全状态。"
    )
    document.add_paragraph(
        "报告中的运行参数来自现场仪表读数和设备自带显示值。原始记录由检测人和复核人签字，"
        "报告结论仅对本次受检设备有效。"
    )

    add_bullets(
        document,
        [
            "检测依据：设备说明书、作业许可单和公司例行巡检规定。",
            "检测内容：外观、运行参数、保护功能及异常项复核。",
            "记录方式：现场填写原始记录表，异常位置补充照片。",
        ],
    )

    document.add_paragraph("${merge-point}")
    document.add_heading("附录：记录说明", level=1)
    document.add_paragraph(
        "原始记录和现场照片存放在一号车间资料柜，复核时按设备名称查找。"
        "报告内容需要调整时，替换副文档后重新合并即可。"
    )
    document.add_paragraph("合并完成后，目录页码需要在 Word 中按 Ctrl+A、F9 更新。")

    target = ROOT / "主文档.docx"
    document.save(target)
    return target


def build_scope(diagram: Path) -> Path:
    document = Document()
    configure_document(document)

    document.add_heading("检测范围", level=1)
    document.add_paragraph("${scope-start}")

    document.add_heading("检测对象", level=2)
    document.add_paragraph(
        "受检设备位于一号车间东侧，包括 3 面配电柜、2 面控制柜、4 台循环风机和 2 台空调机组。"
        "设备编号以现场铭牌为准，检测前与设备清单逐台核对。"
    )
    document.add_picture(str(diagram), width=Inches(5.6))
    caption = document.add_paragraph("图 1  设备布置示意")
    caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
    caption.runs[0].font.size = Pt(9)

    add_table(
        document,
        ["设备", "主要检测项目", "记录要求"],
        [
            ["配电柜", "电压、电流、温升和绝缘状态", "记录各回路运行值"],
            ["控制柜", "控制回路、指示和保护功能", "逐项操作确认"],
            ["空调机组", "送风量、回风温度和运行噪声", "连续运行 30 分钟后记录"],
            ["循环风机", "电流、振动和轴承温度", "异常时补充停机检查"],
        ],
    )

    document.add_heading("检测项目与记录要求", level=2)
    add_bullets(
        document,
        [
            "检测前核对设备编号、额定参数和现场状态。",
            "运行状态下记录电压、电流、温升、风量等参数。",
            "发现异常时先停机确认，再补充照片和处理建议。",
        ],
    )
    document.add_paragraph("${scope-end}")
    document.add_paragraph("这段说明在标记之外，合并时不应写入报告。")

    target = ROOT / "检测范围.docx"
    document.save(target)
    return target


def build_plan() -> Path:
    document = Document()
    configure_document(document)

    document.add_heading("现场实施计划", level=1)
    document.add_paragraph("${plan-start}")

    document.add_heading("时间安排", level=2)
    add_table(
        document,
        ["日期", "工作内容", "参与人员"],
        [
            ["10 月 19 日", "核对设备清单，办理作业许可", "张明、王磊"],
            ["10 月 20 日", "停电检查配电柜和控制柜", "王磊、电气班"],
            ["10 月 21 日", "记录空调机组和循环风机运行参数", "张明、王磊"],
            ["10 月 22 日", "复核异常项，整理原始记录", "李工、张明"],
        ],
    )

    document.add_heading("现场要求", level=2)
    add_bullets(
        document,
        [
            "配电柜检查前由电气班确认断电并挂牌。",
            "屋面作业系安全带，工具放入工具袋。",
            "原始记录当天录入，修改处由记录人签字确认。",
        ],
    )
    document.add_paragraph("${plan-end}")
    document.add_paragraph("这段说明在标记之外，合并时不应写入报告。")

    target = ROOT / "实施计划.docx"
    document.save(target)
    return target


def build_invalid() -> Path:
    document = Document()
    configure_document(document)
    document.add_heading("标记错误说明", level=1)
    document.add_paragraph("${broken-start}")
    document.add_paragraph("该文档只写了开始标记，没有结束标记。合并时应跳过并给出警告。")
    document.add_paragraph("${orphan-end}")
    target = ROOT / "异常标记.docx"
    document.save(target)
    return target


def main() -> None:
    diagram = build_diagram()
    created = [build_main(), build_scope(diagram), build_plan(), build_invalid()]
    for path in created:
        print(f"created: {path.relative_to(ROOT.parent)}")


if __name__ == "__main__":
    main()
