"""Create short synthetic audio fixtures for the emulator feedback smoke test."""
import math
from pathlib import Path
import wave

import av
import numpy as np


OUTPUT = Path(__file__).resolve().parents[1] / "artifacts" / "emulator-feedback-2026-10-05"
RATE = 48_000
SECONDS = 30


def frames():
    time = np.arange(RATE, dtype=np.float32) / RATE
    left = 0.12 * np.sin(2 * math.pi * 440 * time)
    right = 0.12 * np.sin(2 * math.pi * 660 * time)
    stereo = np.stack((left, right)).astype(np.float32)
    return [stereo] * SECONDS


def encode(name, codec, extension):
    path = OUTPUT / f"feedback_{name}.{extension}"
    container = av.open(str(path), mode="w", format="ogg" if name == "opus" else None)
    stream = container.add_stream(codec, rate=RATE)
    stream.layout = "stereo"
    if name == "opus":
        stream.bit_rate = 96_000
        sample_format = "fltp"
    else:
        sample_format = "s16p"
    samples = frames()
    pts = 0
    for audio in samples:
        if sample_format == "s16p":
            audio = (audio * 32767).astype(np.int16)
        frame = av.AudioFrame.from_ndarray(audio, format=sample_format, layout="stereo")
        frame.sample_rate = RATE
        frame.pts = pts
        pts += frame.samples
        for packet in stream.encode(frame):
            container.mux(packet)
    for packet in stream.encode(None):
        container.mux(packet)
    container.close()
    print(f"{path.name}: {path.stat().st_size} bytes; {av.open(str(path)).streams.audio[0].codec_context.name}")


def write_wav():
    path = OUTPUT / "feedback_pcm.wav"
    time = np.arange(RATE * SECONDS, dtype=np.float32) / RATE
    stereo = np.stack((0.12 * np.sin(2 * math.pi * 440 * time),
                       0.12 * np.sin(2 * math.pi * 660 * time)), axis=1)
    pcm = (stereo * 32767).astype("<i2")
    with wave.open(str(path), "wb") as output:
        output.setnchannels(2)
        output.setsampwidth(2)
        output.setframerate(RATE)
        output.writeframes(pcm.tobytes())
    print(f"{path.name}: {path.stat().st_size} bytes; PCM S16LE {RATE} Hz stereo")


if __name__ == "__main__":
    OUTPUT.mkdir(parents=True, exist_ok=True)
    encode("opus", "libopus", "opus")
    encode("flac", "flac", "flac")
    write_wav()
