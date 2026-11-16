"""
La structure vide des tests en jeu (1.21.1) : data/orbitalis/structure/gametest/empty.nbt.

48 blocs de côté et aucun bloc : le jeu vide ce volume avant chaque test, et place les tests voisins au-delà. En 26.2, la
même chose se disait par l'écart (« padding ») des tests. Le script se relance sans risque.
"""
import gzip
import io
import os
import struct

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "common", "src", "main", "resources", "data", "orbitalis", "structure", "gametest", "empty.nbt")
SIZE = 48
DATA_VERSION = 3955  # Minecraft 1.21.1

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def name(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def int_tag(key, value):
    return bytes([TAG_INT]) + name(key) + struct.pack(">i", value)


def int_list(key, values):
    return bytes([TAG_LIST]) + name(key) + bytes([TAG_INT]) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def compound_list(key, compounds):
    body = b"".join(c + bytes([TAG_END]) for c in compounds)
    kind = TAG_COMPOUND if compounds else TAG_END
    return bytes([TAG_LIST]) + name(key) + bytes([kind]) + struct.pack(">i", len(compounds)) + body


def string_tag(key, value):
    return bytes([TAG_STRING]) + name(key) + name(value)


root = (int_tag("DataVersion", DATA_VERSION)
        + int_list("size", [SIZE, SIZE, SIZE])
        + compound_list("palette", [string_tag("Name", "minecraft:air")])
        + compound_list("blocks", [])
        + compound_list("entities", []))
data = bytes([TAG_COMPOUND]) + name("") + root + bytes([TAG_END])
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with gzip.open(OUT, "wb") as f:
    f.write(data)
print("structure écrite :", OUT)
