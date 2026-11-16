#!/usr/bin/env python3
"""Generate Copper Spigot's block models and blockstate.

Two models, both drawn facing north and turned by the blockstate:

- wall: a plate on the wall (the south face, z=16), a pipe running out of it, an
  elbow turning the flow down, a wider nozzle at the bottom, and a T handle on top.
- hung: a plate on the ceiling, a short pipe straight down, the nozzle, and a
  handle sticking out to the front.

The boxes here are the same numbers as the outline shapes in
CopperSpigotBlock.java; change one, change the other.

Each model comes in four turns of its handle, 0, 30, 60 and 90 degrees, which the
blockstate shows by the block's "turn" property as the tap opens and closes. Only
the handle moves: the T on a wall spigot spins about its stem, the bar on a hanging
one about the stem that holds it out.

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

# (from, to, face culled against the support, if any, whether it turns with the handle)
WALL = (
    ((5, 9, 15), (11, 15, 16), "south", False),      # wall plate
    ((6.5, 10.5, 6), (9.5, 13.5, 15), None, False),  # pipe out of the wall
    ((6.5, 5, 6), (9.5, 10.5, 9), None, False),      # elbow, turning down
    ((6, 3, 5.5), (10, 5, 9.5), None, False),        # nozzle
    ((7.5, 13.5, 10), (8.5, 15, 11), None, True),    # handle stem
    ((5.5, 15, 10), (10.5, 16, 11), None, True),     # handle bar
)
# The T spins about its stem.
WALL_HANDLE = {"origin": [8, 15, 10.5], "axis": "y"}

HUNG = (
    ((5, 15, 5), (11, 16, 11), "up", False),         # ceiling plate
    ((6.5, 7, 6.5), (9.5, 15, 9.5), None, False),    # pipe down
    ((6, 5, 6), (10, 7, 10), None, False),           # nozzle
    ((7.5, 11, 5), (8.5, 12, 6.5), None, True),      # handle stem, out the front
    ((7.5, 9.5, 4), (8.5, 13.5, 5), None, True),     # handle bar
)
# The bar turns about the stem that holds it out the front.
HUNG_HANDLE = {"origin": [8, 11.5, 4.5], "axis": "z"}

# Degrees for each value of the block's "turn" property; CopperSpigotBlock.TURN's range.
TURNS = (0, 30, 60, 90)


def element(lo, hi, cull, turns, handle, angle):
    faces = {}
    for face in FACES:
        entry = {"texture": "#copper"}
        if face == cull:
            entry["cullface"] = face
        faces[face] = entry
    out = {"from": list(lo), "to": list(hi), "faces": faces}
    if turns and angle:
        out["rotation"] = dict(handle, angle=angle)
    return out


def model(boxes, handle, angle):
    return {
        "ambientocclusion": False,
        "textures": {"copper": TEXTURE, "particle": TEXTURE},
        "elements": [element(*box, handle, angle) for box in boxes],
    }


def model_name(kind, turn):
    return "copper_spigot_%s" % kind + ("_turn%d" % turn if turn else "")


def blockstate():
    turns = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    # "powered" changes nothing drawn, so it is left out of the keys and matches either way.
    for facing, y in turns.items():
        for hanging in ("false", "true"):
            for turn in range(len(TURNS)):
                kind = "hung" if hanging == "true" else "wall"
                variant = {"model": "%s:block/%s" % (NS, model_name(kind, turn))}
                if y:
                    variant["y"] = y
                variants["facing=%s,hanging=%s,turn=%d" % (facing, hanging, turn)] = variant
    return {"variants": variants}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print("wrote", path)


if __name__ == "__main__":
    for turn, angle in enumerate(TURNS):
        write(os.path.join(ASSETS, "models/block/%s.json" % model_name("wall", turn)), model(WALL, WALL_HANDLE, angle))
        write(os.path.join(ASSETS, "models/block/%s.json" % model_name("hung", turn)), model(HUNG, HUNG_HANDLE, angle))
    write(os.path.join(ASSETS, "blockstates/copper_spigot.json"), blockstate())
