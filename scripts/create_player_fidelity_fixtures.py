"""Synthetic FLAC covers for player contrast checks. No external music or imagery."""
from pathlib import Path
from io import BytesIO
import struct
import av
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'artifacts' / 'player-fidelity-2026-10-06' / 'fixtures'


def create(name, image, title=None, artist=None, album=None, seconds=120):
    path = OUT / f'cover_{name}.flac'
    container = av.open(str(path), 'w')
    container.metadata.update(title=title or f'Cover Test {name}', artist=artist or 'Convx visual test', album=album or f'Extreme artwork {name}')
    stream = container.add_stream('flac', rate=48000)
    stream.layout = 'stereo'
    t = np.arange(48000) / 48000
    samples = np.stack([np.sin(2 * np.pi * 440 * t) * 1000] * 2).astype(np.int16)
    for second in range(seconds):
        frame = av.AudioFrame.from_ndarray(samples, format='s16p', layout='stereo')
        frame.sample_rate = 48000
        frame.pts = second * 48000
        for packet in stream.encode(frame):
            container.mux(packet)
    for packet in stream.encode(None):
        container.mux(packet)
    container.close()
    png = BytesIO()
    image.save(png, 'PNG')
    data = png.getvalue()
    mime = b'image/png'
    picture = (struct.pack('>II', 3, len(mime)) + mime + struct.pack('>I', 0)
               + struct.pack('>IIIII', image.width, image.height, 24, 0, len(data)) + data)
    original = path.read_bytes()
    assert original[:4] == b'fLaC'
    offset = 4
    blocks = []
    while True:
        header = original[offset]
        size = int.from_bytes(original[offset + 1:offset + 4], 'big')
        blocks.append(bytes([header & 127]) + original[offset + 1:offset + 4 + size])
        offset += 4 + size
        if header & 128:
            break
    path.write_bytes(b'fLaC' + b''.join(blocks) + bytes([128 | 6]) + len(picture).to_bytes(3, 'big')
                     + picture + original[offset:])
    image.save(OUT / f'{name}.png')
    (OUT / f'cover_{name}.lrc').write_text('[00:00.00]Bright artwork, readable controls\n[00:05.00]Queue, lyrics and menus stay clear\n[00:10.00]Every theme and orientation\n', encoding='utf-8')


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    for name, color in [('white', 'white'), ('black', 'black'), ('yellow', '#ffff00'), ('cyan', '#00ffff')]:
        create(name, Image.new('RGB', (600, 600), color))
    image = Image.new('RGB', (600, 600))
    draw = ImageDraw.Draw(image)
    colors = ['#ff00ff', '#00ff00', '#0000ff', '#ffff00']
    for y in range(0, 600, 30):
        for x in range(0, 600, 30):
            draw.rectangle((x, y, x + 29, y + 29), fill=colors[(x // 30 + y // 30) % 4])
    create('checker', image)
    print(f'Created 5 extreme cover fixtures in {OUT}')
