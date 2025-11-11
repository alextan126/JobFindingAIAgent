import io
from typing import Optional

from fpdf import FPDF


def text_to_pdf_bytes(text: str, *, title: Optional[str] = None) -> bytes:
    """Render simple text content into a PDF and return raw bytes."""
    pdf = FPDF()
    pdf.set_auto_page_break(auto=True, margin=15)
    pdf.add_page()
    pdf.set_font("Helvetica", size=12)

    if title:
        pdf.set_font("Helvetica", style="B", size=14)
        pdf.multi_cell(0, 10, title)
        pdf.ln(4)
        pdf.set_font("Helvetica", size=12)

    for line in text.splitlines():
        pdf.multi_cell(0, 8, line if line.strip() else " ")

    buffer = io.BytesIO()
    pdf.output(buffer)
    return buffer.getvalue()


__all__ = ["text_to_pdf_bytes"]

