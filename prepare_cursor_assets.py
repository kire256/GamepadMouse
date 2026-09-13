#!/usr/bin/env python3
from PIL import Image
import os

p = 'app/src/main/res/drawable-nodpi'
files = ['cursor_blue_arrow.png', 'cursor_target.png', 'cursor_3d_pointer.png']

for f in files:
    path = os.path.join(p, f)
    img = Image.open(path).convert('RGBA')
    r, g, b, a = img.split()
    gray = Image.blend(r, g, 0.5)
    white = Image.blend(gray, gray, 0.0)
    alpha_only = white.point(lambda v: 255 if v > 0 else 0)
    out = Image.merge('RGBA', (alpha_only, alpha_only, alpha_only, a))
    out.save(path)
    print('converted', f, out.getextrema())
