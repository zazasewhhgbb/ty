"""
Combines per-chunk WAV files into one final audio file, with small
configurable pauses between chunks and loudness normalization.
Uses pydub (backed by ffmpeg) so it can export both WAV and MP3.
"""
from pathlib import Path
from typing import List

from pydub import AudioSegment
from pydub.effects import normalize


def combine_chunks(
    chunk_paths: List[str],
    output_path: str,
    pause_ms: int = 300,
    output_format: str = "mp3",
) -> str:
    if not chunk_paths:
        raise ValueError("No chunks to combine")

    combined = AudioSegment.empty()
    silence = AudioSegment.silent(duration=pause_ms)

    for i, path in enumerate(chunk_paths):
        segment = AudioSegment.from_file(path)
        segment = normalize(segment)
        combined += segment
        if i < len(chunk_paths) - 1:
            combined += silence

    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    export_format = "mp3" if output_format == "mp3" else "wav"
    combined.export(output_path, format=export_format, bitrate="192k" if export_format == "mp3" else None)
    return output_path


def get_duration_seconds(path: str) -> float:
    audio = AudioSegment.from_file(path)
    return len(audio) / 1000.0
