"""
La couche de rendu de chaque bloc du mod, pour Minecraft 1.21.1.

En 26.2, le jeu choisit la couche d'un bloc d'après ses textures (pixels transparents : découpe ; demi-transparents :
translucide). En 1.21.1, il faut la lui dire. Ce script lit les modèles de chaque état de bloc, les textures qu'ils
utilisent, et écrit common/src/main/resources/assets/orbitalis/render_layers.json :
{"cutout": [blocs...], "translucent": [blocs...]}. Le client l'inscrit au démarrage.

Le script se relance sans risque.
"""
import io
import json
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "common", "src", "main", "resources", "assets")
NS = "orbitalis"


def load(path):
    with io.open(path, encoding="utf-8") as f:
        return json.load(f)


def png_alpha(path):
    """(a des pixels transparents, a des pixels demi-transparents) pour une image PNG."""
    with open(path, "rb") as f:
        data = f.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return False, False
    pos = 8
    width = height = depth = ctype = 0
    idat = b""
    palette_alpha = None
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, ctype = struct.unpack(">IIBB", body[:10])
        elif kind == b"tRNS":
            palette_alpha = body
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            break
    if ctype == 3:
        if palette_alpha is None:
            return False, False
        alphas = set(palette_alpha)
        return 0 in alphas, any(0 < a < 255 for a in alphas)
    if ctype not in (4, 6) or depth != 8:
        return False, False
    channels = 4 if ctype == 6 else 2
    raw = zlib.decompress(idat)
    stride = width * channels
    prev = bytearray(stride)
    clear = partial = False
    at = 0
    for _ in range(height):
        f = raw[at]
        line = bytearray(raw[at + 1:at + 1 + stride])
        at += 1 + stride
        for i in range(stride):
            a = line[i - channels] if i >= channels else 0
            b = prev[i]
            c = prev[i - channels] if i >= channels else 0
            if f == 1:
                line[i] = (line[i] + a) & 0xFF
            elif f == 2:
                line[i] = (line[i] + b) & 0xFF
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 0xFF
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if pa <= pb and pa <= pc else b if pb <= pc else c
                line[i] = (line[i] + pred) & 0xFF
        for i in range(channels - 1, stride, channels):
            alpha = line[i]
            if alpha == 0:
                clear = True
            elif alpha < 255:
                partial = True
        prev = line
    return clear, partial


def resolve(model_id, seen=None):
    """Les textures (après résolution des # et des parents) d'un modèle, et vrai s'il a des éléments à lui."""
    seen = seen or set()
    if model_id in seen:
        return {}
    seen.add(model_id)
    ns, path = model_id.split(":", 1) if ":" in model_id else ("minecraft", model_id)
    file = os.path.join(ASSETS, ns, "models", path + ".json")
    if not os.path.exists(file):
        return {}
    model = load(file)
    textures = {}
    if "parent" in model:
        textures.update(resolve(model["parent"], seen))
    textures.update(model.get("textures", {}))
    return textures


def texture_files(textures):
    out = []
    for key, value in textures.items():
        hops = 0
        while isinstance(value, str) and value.startswith("#") and hops < 10:
            value = textures.get(value[1:], "")
            hops += 1
        if not value or value.startswith("#"):
            continue
        ns, path = value.split(":", 1) if ":" in value else ("minecraft", value)
        out.append(os.path.join(ASSETS, ns, "textures", path + ".png"))
    return out


def models_of(blockstate):
    found = []
    for variant in (blockstate.get("variants") or {}).values():
        for entry in variant if isinstance(variant, list) else [variant]:
            found.append(entry["model"])
    for part in blockstate.get("multipart") or []:
        apply = part["apply"]
        for entry in apply if isinstance(apply, list) else [apply]:
            found.append(entry["model"])
    return found


def main():
    folder = os.path.join(ASSETS, NS, "blockstates")
    cutout, translucent = [], []
    cache = {}
    for f in sorted(os.listdir(folder)):
        block = f[:-5]
        clear = partial = False
        for model in set(models_of(load(os.path.join(folder, f)))):
            for png in texture_files(resolve(model)):
                if png not in cache:
                    cache[png] = png_alpha(png) if os.path.exists(png) else (False, False)
                c, p = cache[png]
                clear |= c
                partial |= p
        if partial:
            translucent.append(NS + ":" + block)
        elif clear:
            cutout.append(NS + ":" + block)
    out = os.path.join(ASSETS, NS, "render_layers.json")
    with io.open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"cutout": cutout, "translucent": translucent}, f, indent=2)
        f.write("\n")
    print("découpe :", len(cutout), "; translucide :", len(translucent))


if __name__ == "__main__":
    sys.exit(main())
