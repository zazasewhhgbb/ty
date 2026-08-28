"""
Splits arbitrarily large input text into TTS-sized chunks.

Pipeline (matches spec section 14):
  clean -> detect chapters -> split into paragraphs -> split into sentences
  -> greedily pack sentences into chunks under CHUNK_MAX_CHARS, never
  cutting a sentence in the middle.
"""
import re
from dataclasses import dataclass, field
from typing import List

_CHAPTER_RE = re.compile(r"^\s*(chapter|part)\s+[\divxlcIVXLC]+\b.*$", re.IGNORECASE | re.MULTILINE)
_SENTENCE_SPLIT_RE = re.compile(r"(?<=[.!?])\s+(?=[A-Z0-9\"'])")


@dataclass
class Chapter:
    title: str
    text: str


@dataclass
class ChunkPlan:
    chapters: List[Chapter]
    chunks: List[str] = field(default_factory=list)


def clean_text(raw: str) -> str:
    text = raw.replace("\r\n", "\n").replace("\r", "\n")
    # collapse 3+ blank lines to a single blank line, strip trailing whitespace
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def detect_chapters(text: str) -> List[Chapter]:
    """
    Best-effort chapter detection. If no chapter headings are found, the
    whole document is treated as a single unnamed chapter — this keeps the
    rest of the pipeline (and the "simplest reliable version first" goal
    from spec section 20) working either way.
    """
    matches = list(_CHAPTER_RE.finditer(text))
    if not matches:
        return [Chapter(title="Full Text", text=text)]

    chapters = []
    for i, m in enumerate(matches):
        start = m.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        chapters.append(Chapter(title=m.group().strip(), text=text[start:end].strip()))
    return chapters


def split_sentences(paragraph: str) -> List[str]:
    paragraph = paragraph.strip()
    if not paragraph:
        return []
    sentences = _SENTENCE_SPLIT_RE.split(paragraph)
    return [s.strip() for s in sentences if s.strip()]


def split_paragraphs(text: str) -> List[str]:
    return [p.strip() for p in text.split("\n\n") if p.strip()]


def pack_into_chunks(paragraphs: List[str], max_chars: int) -> List[str]:
    """
    Greedily fills each chunk with whole sentences up to max_chars.
    A single sentence longer than max_chars is kept intact anyway (never
    cut mid-sentence), even if it exceeds the nominal budget.
    """
    chunks: List[str] = []
    current = ""

    for para in paragraphs:
        for sentence in split_sentences(para):
            candidate = f"{current} {sentence}".strip() if current else sentence
            if len(candidate) <= max_chars or not current:
                current = candidate
            else:
                chunks.append(current)
                current = sentence
        # prefer paragraph breaks as natural chunk boundaries when there's room
        if current and len(current) > max_chars * 0.6:
            chunks.append(current)
            current = ""

    if current:
        chunks.append(current)
    return chunks


def plan_chunks(raw_text: str, max_chars: int = 400) -> ChunkPlan:
    text = clean_text(raw_text)
    chapters = detect_chapters(text)
    all_chunks: List[str] = []
    for chapter in chapters:
        paragraphs = split_paragraphs(chapter.text)
        all_chunks.extend(pack_into_chunks(paragraphs, max_chars))
    return ChunkPlan(chapters=chapters, chunks=all_chunks)
