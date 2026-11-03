"""Combines the ocean mask with land slopes into terrain.png, which the shader reads in one lookup.

Inputs:
- ETOPO5.DOS from https://www.ngdc.noaa.gov/mgg/global/relief/ETOPO5/TOPO/ETOPO5/, NOAA's
  global elevation grid at 5 arc-minutes: 4320x2160 little-endian 16-bit meters, row 0 at
  90N, column 0 at longitude 0 growing eastward.
- The ocean mask: white water, black land (ocean_mask.png, or the red channel of a previous
  terrain.png).

Output: terrain.png, the same size as the mask, in the app's map layout (u = 0 at longitude
-180, v = 0 at the north pole):
- red: the ocean mask, unchanged.
- green, blue: how steeply the land rises toward the east and toward the north (meters per
  meter, over one texel), stored as SLOPE_SCALE * sign * sqrt(|slope| / SLOPE_SCALE) around
  mid-grey. The square root keeps gentle slopes, which are most of the land, from rounding
  to zero in 8 bits; the shader undoes it with one multiplication.

The sea floor is left flat: under water the relief wouldn't be visible from space.

Needs numpy and Pillow:
    python3 -m venv venv && venv/bin/pip install numpy pillow
    venv/bin/python tools/terrain_map.py ETOPO5.DOS ocean_mask.png app/src/main/assets/textures/terrain.png
"""
import sys

import numpy as np
from PIL import Image

EARTH_RADIUS = 6371000.0
# Steepest slope stored; steeper ones are clipped. Must match MAX_SLOPE in globe.frag.
# Averaged over a texel (about 20 km) only the sides of the Himalayas come close.
SLOPE_SCALE = 0.1


def load_elevation(path):
    """ETOPO5 as a 2D array, rotated so column 0 is longitude -180, with the sea floor at 0."""
    elevation = np.fromfile(path, dtype='<i2').reshape(2160, 4320).astype(np.float64)
    return np.maximum(np.roll(elevation, 2160, axis=1), 0.0)


def box_resize(grid, height, width):
    """Averages the grid down to height x width, so each texel holds its area's mean height."""
    rows = np.minimum((np.arange(grid.shape[0]) * height) // grid.shape[0], height - 1)
    cols = np.minimum((np.arange(grid.shape[1]) * width) // grid.shape[1], width - 1)
    total = np.zeros((height, width))
    count = np.zeros((height, width))
    np.add.at(total, (rows[:, None], cols[None, :]), grid)
    np.add.at(count, (rows[:, None], cols[None, :]), 1.0)
    return total / count


def slopes(height_map):
    """Rise toward the east and toward the north, from central differences."""
    rows, cols = height_map.shape
    latitude = (0.5 - (np.arange(rows) + 0.5) / rows) * np.pi
    dx = 2 * np.pi * EARTH_RADIUS * np.maximum(np.cos(latitude), 0.05) / cols
    dy = np.pi * EARTH_RADIUS / rows
    east = (np.roll(height_map, -1, axis=1) - np.roll(height_map, 1, axis=1)) / (2 * dx[:, None])
    above = np.vstack([height_map[:1], height_map[:-1]])
    below = np.vstack([height_map[1:], height_map[-1:]])
    span = np.full(rows, 2.0)
    span[0] = span[-1] = 1.0  # one-sided at the poles
    north = (above - below) / (span[:, None] * dy)
    return east, north


def encode(slope):
    s = np.sign(slope) * np.sqrt(np.minimum(np.abs(slope), SLOPE_SCALE) / SLOPE_SCALE)
    return np.round(127.5 + 127.5 * s).astype(np.uint8)


def main(elevation_path, mask_path, out_path):
    mask = Image.open(mask_path).convert('RGB').getchannel('R')
    width, height = mask.size
    east, north = slopes(box_resize(load_elevation(elevation_path), height, width))
    # Where the mask says open water, keep the glint's surface flat even if the coarser
    # elevation grid puts a coast there.
    water = np.asarray(mask) == 255
    east[water] = 0.0
    north[water] = 0.0
    channels = [mask, Image.fromarray(encode(east)), Image.fromarray(encode(north))]
    Image.merge('RGB', channels).save(out_path, optimize=True)
    print(f'{out_path}: {width}x{height}, land slope 99th percentile '
          f'{np.percentile(np.hypot(east, north)[~water], 99):.4f}')


if __name__ == '__main__':
    main(*sys.argv[1:4])
