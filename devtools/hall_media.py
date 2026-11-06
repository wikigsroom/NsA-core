"""Create README-sized PNG/GIF/MP4 from reviewed actual USB captures, with provenance."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--name', required=True)
    parser.add_argument('--start', type=float, default=0)
    parser.add_argument('--duration', type=float, default=15)
    parser.add_argument('--width', type=int, default=420)
    args = parser.parse_args()
    if not 240 <= args.width <= 960 or not 0 <= args.start or not 0 < args.duration <= 60:
        raise ValueError('Use width 240..960, a nonnegative start and duration 0..60 seconds')
    source = args.source.resolve(); evidence = (ROOT / '.gamedump/Hall/device-evidence').resolve()
    if evidence not in source.parents: raise ValueError('Use an actual capture under .gamedump/Hall/device-evidence')
    provenance = source.with_name(source.stem + '-provenance.json')
    record = json.loads(provenance.read_text(encoding='utf-8'))
    if record.get('synthetic') is not False: raise ValueError('Synthetic media cannot be presented as application evidence')
    name = ''.join(c for c in args.name if c.isalnum() or c in '-_')
    target = ROOT / 'docs/media'; target.mkdir(parents=True, exist_ok=True)
    if source.suffix.lower() == '.png':
        destination = target / (name + '.png'); shutil.copy2(source, destination)
        artifacts = [destination]
    else:
        destination = target / (name + '.gif')
        # Limit output time: Android screen recordings have variable cadence,
        # so limiting input packets can retain an oversized final frame.
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-ss', str(args.start), '-i', str(source),
                        '-filter_complex', f'[0:v]fps=10,scale={args.width}:-2:flags=lanczos,split[a][b];[a]palettegen=max_colors=96[p];[b][p]paletteuse=dither=bayer:bayer_scale=3', '-t', str(args.duration), '-loop', '0', str(destination)], check=True)
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-ss', str(args.start), '-i', str(source), '-map', '0:v:0', '-vf', 'scale=720:-2', '-c:v', 'libx264', '-crf', '26', '-pix_fmt', 'yuv420p', '-fps_mode', 'vfr', '-t', str(args.duration), '-an', '-movflags', '+faststart', str(target / (name + '.mp4'))], check=True)
        artifacts = [destination, target / (name + '.mp4')]
    with source.open('rb') as incoming: sha = hashlib.file_digest(incoming, 'sha256').hexdigest()
    exported = []
    for artifact in artifacts:
        with artifact.open('rb') as incoming: checksum = hashlib.file_digest(incoming, 'sha256').hexdigest()
        info = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(artifact)]))
        video = next(stream for stream in info['streams'] if stream['codec_type'] == 'video')
        exported.append({'name': artifact.name, 'sha256': checksum, 'width': video['width'], 'height': video['height'], 'duration_seconds': info['format'].get('duration')})
    (target / (name + '.json')).write_text(json.dumps({**record, 'source_sha256': sha, 'artifacts': exported, 'transform': 'copy' if source.suffix.lower() == '.png' else 'resize and GIF palette encoding; no synthetic frames; MP4 has no audio track', 'start_seconds': args.start, 'requested_duration_seconds': args.duration}, indent=2), encoding='utf-8')
    print('Reviewed device media exported to docs/media with provenance.')

if __name__ == '__main__': main()
