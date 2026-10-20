#!/usr/bin/env python3
"""Generate Copper Spigot's block models and blockstate.

Two models, both drawn facing north and turned by the blockstate:

- wall: a plate on the wall (the south face, z=16), a pipe running out of it, an
  elbow turning the flow down, a wider nozzle at the bottom, and a T handle on top.
- hung: a plate on the ceiling, a short pipe straight down, the nozzle, and a
  handle sticking out to the front.

The boxes here are the same numbers as the outline shapes in
CopperSpigotBlock.java; change one, change the other.

Every face takes its UV from where it sits in the block (no "uv" given), which is
what a generated surface texture wants: the pipe reads as one continuous piece of
copper rather than a stack of separately mapped parts.

Usage: python3 generate_models.py
"""

import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "copper-spigot-justfatlard"
ASSETS = os.path.join(HERE, "src/main/resources/assets", NS)
TEXTURE = NS + ":block/copper_spigot"

FACES = ("north", "east", "south", "west", "up", "down")

# (from, to, face culled against the support, if any)
WALL = (
    ((5, 9, 15), (11, 15, 16), "south"),      # wall plate
    ((6.5, 10.5, 6), (9.5, 13.5, 15), None),  # pipe out of the wall
    ((6.5, 5, 6), (9.5, 10.5, 9), None),      # elbow, turning down
    ((6, 3, 5.5), (10, 5, 9.5), None),        # nozzle
    ((7.5, 13.5, 10), (8.5, 15, 11), None),   # handle stem
    ((5.5, 15, 10), (10.5, 16, 11), None),    # handle bar
)

HUNG = (
    ((5, 15, 5), (11, 16, 11), "up"),         # ceiling plate
    ((6.5, 7, 6.5), (9.5, 15, 9.5), None),    # pipe down
    ((6, 5, 6), (10, 7, 10), None),           # nozzle
    ((7.5, 11, 5), (8.5, 12, 6.5), None),     # handle stem, out the front
    ((7.5, 9.5, 4), (8.5, 13.5, 5), None),    # handle bar
)


def element(lo, hi, cull):
    faces = {}
    for face in FACES:
        entry = {"texture": "#copper"}
        if face == cull:
            entry["cullface"] = face
        faces[face] = entry
    return {"from": list(lo), "to": list(hi), "faces": faces}


def model(boxes):
    return {
        "ambientocclusion": False,
        "textures": {"copper": TEXTURE, "particle": TEXTURE},
        "elements": [element(*box) for box in boxes],
    }


def blockstate():
    turns = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y in turns.items():
        for hanging in ("false", "true"):
            for powered in ("false", "true"):
                variant = {"model": "%s:block/copper_spigot_%s" % (NS, "hung" if hanging == "true" else "wall")}
                if y:
                    variant["y"] = y
                variants["facing=%s,hanging=%s,powered=%s" % (facing, hanging, powered)] = variant
    return {"variants": variants}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print("wrote", path)


if __name__ == "__main__":
    write(os.path.join(ASSETS, "models/block/copper_spigot_wall.json"), model(WALL))
    write(os.path.join(ASSETS, "models/block/copper_spigot_hung.json"), model(HUNG))
    write(os.path.join(ASSETS, "blockstates/copper_spigot.json"), blockstate())
