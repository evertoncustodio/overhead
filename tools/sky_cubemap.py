"""Turns NASA's Deep Star Map into the six cube map faces of the sky behind the globe.

Input: starmap_2020_4k.exr from https://svs.gsfc.nasa.gov/4851, an equirectangular map in
equatorial coordinates (right ascension and declination), centered on RA 0h with RA growing
to the left, in linear light from 0 to 1.

Output: sky_px.jpg, sky_nx.jpg, sky_py.jpg, sky_ny.jpg, sky_pz.jpg, sky_nz.jpg, in OpenGL's
cube map layout. The sky's axes match Earth-fixed coordinates at sidereal time 0: +Y is the
north celestial pole, +Z is RA 0h and +X is RA 6h.

Needs numpy, OpenEXR and Pillow:
    python3 -m venv venv && venv/bin/pip install numpy OpenEXR pillow
    venv/bin/python tools/sky_cubemap.py starmap_2020_4k.exr app/src/main/assets/textures
"""
import sys
from pathlib import Path

import numpy as np
import OpenEXR
from PIL import Image

FACE_SIZE = 1024
SUPERSAMPLE = 3  # samples per texel side, so stars don't alias away
# Most of the map is a faint haze of unresolved stars around 0.0075. Behind a sunlit planet
# that would read as grey, not space, so it is taken off as a black level.
BLACK_LEVEL = 0.008
# What's left is dark: brighten it before the gamma curve, so the faint glow keeps its
# gradations in 8 bits; the shader scales it back down.
EXPOSURE = 1.5
GAMMA = 2.2


def face_directions(face, n):
    """Directions for each texel of a face, following the OpenGL cube map table."""
    k = (np.arange(n * SUPERSAMPLE) + 0.5) / (n * SUPERSAMPLE) * 2.0 - 1.0
    sc, tc = np.meshgrid(k, k)  # sc grows to the right, tc downward (row 0 is uploaded first)
    one = np.ones_like(sc)
    x, y, z = {
        'px': (one, -tc, -sc),
        'nx': (-one, -tc, sc),
        'py': (sc, one, tc),
        'ny': (sc, -one, -tc),
        'pz': (sc, -tc, one),
        'nz': (-sc, -tc, -one),
    }[face]
    length = np.sqrt(x * x + y * y + z * z)
    return x / length, y / length, z / length


def sample(sky, x, y, z):
    """Bilinear lookup in the equirectangular map for unit directions."""
    height, width, _ = sky.shape
    right_ascension = np.arctan2(x, z)  # +Z is 0h, +X is 6h
    declination = np.arcsin(np.clip(y, -1.0, 1.0))
    u = (0.5 - right_ascension / (2 * np.pi)) % 1.0 * width - 0.5
    v = (0.5 - declination / np.pi) * height - 0.5
    u0 = np.floor(u).astype(int)
    v0 = np.floor(v).astype(int)
    fu = (u - u0)[..., None]
    fv = (v - v0)[..., None]
    v0c = np.clip(v0, 0, height - 1)
    v1c = np.clip(v0 + 1, 0, height - 1)
    u0w = u0 % width
    u1w = (u0 + 1) % width
    top = sky[v0c, u0w] * (1 - fu) + sky[v0c, u1w] * fu
    bottom = sky[v1c, u0w] * (1 - fu) + sky[v1c, u1w] * fu
    return top * (1 - fv) + bottom * fv


def main(source, out_dir):
    sky = np.asarray(OpenEXR.File(source).channels()['RGB'].pixels, dtype=np.float32)
    out = Path(out_dir)
    for face in ('px', 'nx', 'py', 'ny', 'pz', 'nz'):
        color = sample(sky, *face_directions(face, FACE_SIZE))
        # Average the supersamples in linear light.
        color = color.reshape(FACE_SIZE, SUPERSAMPLE, FACE_SIZE, SUPERSAMPLE, 3).mean(axis=(1, 3))
        encoded = np.clip((color - BLACK_LEVEL) * EXPOSURE, 0.0, 1.0) ** (1.0 / GAMMA)
        image = Image.fromarray((encoded * 255.0 + 0.5).astype(np.uint8))
        image.save(out / f'sky_{face}.jpg', quality=88, optimize=True)
        print(face, 'done')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
