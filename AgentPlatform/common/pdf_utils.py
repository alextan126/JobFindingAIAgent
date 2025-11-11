import io
import unicodedata
from typing import Optional

from fpdf import FPDF
import textwrap


_REPLACEMENTS = str.maketrans(
    {
        "—": "-",
        "–": "-",
        "‒": "-",
        "―": "-",
        "“": '"',
        "”": '"',
        "„": '"',
        "‟": '"',
        "’": "'",
        "‘": "'",
        "‚": ",",
        "‹": "<",
        "›": ">",
        "…": "...",
        "•": "-",
    }
)


def _sanitize(text: str) -> str:
    normalized = unicodedata.normalize("NFKC", text)
    replaced = normalized.translate(_REPLACEMENTS)
    return replaced.encode("latin-1", "ignore").decode("latin-1")


def text_to_pdf_bytes(text: str, *, title: Optional[str] = None) -> bytes:
    """Render simple text content into a PDF and return raw bytes."""
    pdf = FPDF()
    pdf.set_auto_page_break(auto=True, margin=15)
    pdf.add_page()
    pdf.set_font("Helvetica", size=12)

    if title:
        safe_title = _sanitize(title)
        pdf.set_font("Helvetica", style="B", size=14)
        pdf.multi_cell(0, 10, safe_title)
        pdf.ln(4)
        pdf.set_font("Helvetica", size=12)

    for raw_line in text.splitlines():
        line = _sanitize(raw_line)
        if not line.strip():
            pdf.multi_cell(pdf.epw, 8, " ")
            continue

        chunks = textwrap.wrap(
            line,
            width=90,
            break_long_words=True,
            break_on_hyphens=True,
        )
        if not chunks:
            chunks = [line]

        for chunk in chunks:
            try:
                pdf.multi_cell(pdf.epw, 8, chunk)
            except Exception:
                # Last resort: write character by character to avoid layout errors.
                for character in chunk:
                    pdf.multi_cell(pdf.epw, 8, character)

    buffer = io.BytesIO()
    pdf.output(buffer)
    return buffer.getvalue()


__all__ = ["text_to_pdf_bytes"]

