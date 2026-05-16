from pathlib import Path
import shutil

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Cm, Pt


DOC_PATH = Path(r"A:\Proiecte\SwiftBank\documentatie\Licenta Cirimpei Luca.docx")
BACKUP_PATH = DOC_PATH.with_name("Licenta Cirimpei Luca - backup inainte formatare introducere.docx")
OUT_PATH = DOC_PATH.with_name("Licenta Cirimpei Luca - introducere formatata.docx")


def set_font(run, size=12, bold=False, italic=False):
    run.font.name = "Times New Roman"
    run._element.rPr.rFonts.set(qn("w:eastAsia"), "Times New Roman")
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic


def format_body_paragraph(paragraph):
    paragraph.style = "Normal"
    paragraph.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    paragraph.paragraph_format.first_line_indent = Cm(1.25)
    paragraph.paragraph_format.left_indent = Cm(0)
    paragraph.paragraph_format.right_indent = Cm(0)
    paragraph.paragraph_format.space_before = Pt(0)
    paragraph.paragraph_format.space_after = Pt(6)
    paragraph.paragraph_format.line_spacing = 1.15
    for run in paragraph.runs:
        set_font(run, size=12, bold=False, italic=False)


def format_intro_heading(paragraph):
    paragraph.style = "Heading 1"
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph.paragraph_format.first_line_indent = Cm(0)
    paragraph.paragraph_format.space_before = Pt(0)
    paragraph.paragraph_format.space_after = Pt(12)
    for run in paragraph.runs:
        set_font(run, size=14, bold=True)


def remove_paragraph(paragraph):
    element = paragraph._element
    element.getparent().remove(element)
    paragraph._p = paragraph._element = None


def main():
    if not DOC_PATH.exists():
        raise FileNotFoundError(DOC_PATH)
    if not BACKUP_PATH.exists():
        shutil.copy2(DOC_PATH, BACKUP_PATH)

    document = Document(DOC_PATH)
    paragraphs = list(document.paragraphs)

    intro_index = next(
        (i for i, p in enumerate(paragraphs) if p.text.strip() == "INTRODUCERE"),
        None,
    )
    if intro_index is None:
        raise RuntimeError("INTRODUCERE heading not found")

    end_index = next(
        (
            i
            for i in range(intro_index + 1, len(paragraphs))
            if paragraphs[i].text.strip().startswith("CAPITOLUL 1.")
        ),
        None,
    )
    if end_index is None:
        raise RuntimeError("CAPITOLUL 1 heading not found after introduction")

    format_intro_heading(paragraphs[intro_index])

    for paragraph in paragraphs[intro_index + 1 : end_index]:
        text = paragraph.text.strip()
        if not text:
            remove_paragraph(paragraph)
        elif text == "[Conținut în lucru]":
            remove_paragraph(paragraph)
        else:
            format_body_paragraph(paragraph)

    try:
        document.save(DOC_PATH)
        saved_path = DOC_PATH
    except PermissionError:
        document.save(OUT_PATH)
        saved_path = OUT_PATH

    print(saved_path)
    print(BACKUP_PATH)


if __name__ == "__main__":
    main()
