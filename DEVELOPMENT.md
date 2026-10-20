# Copper Spigot - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients
need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API)
and `fabric.mod.json` (Java).

## Key Files

| File | Responsibility |
|------|---------------|
| `Main.java` | Entry point; the block, its item, and its Pandorical content sync |
| `CopperSpigotBlock.java` | Placement and support, the click and the redstone edge, and the pour itself |
| `integration/SpigotTipRegistration.java` | The block-tip line, read from the same source and plan the pour uses; loaded only when block-tip is |
| `gametest/SmokeTest.java` | Builds rigs in a real world and checks every pour comes out as it should |

## Optional Integration

block-tip is a compile-only dependency resolved from `../block-tip` (its `gradle.properties` version and
built jar), declared under `suggests` and registered behind `FabricLoader.isModLoaded("block-tip")`.
Build block-tip first if the build says its jar is missing.

## Testing

`./gradlew runClientGameTest` starts a client, joins a world, and runs the rigs in `SmokeTest`:
hanging and wall spigots, water and lava, cauldrons in and out, and the redstone edge.

## Art

`generate_models.py` writes the two block models and the blockstate; its boxes are the same
numbers as the outline shapes in `CopperSpigotBlock.java`, so change both together.
`generate_textures.py` and `generate_icon.py` cut the block texture, item sprite and icon out of
the vanilla jar's copper. All three are deterministic; re-run them after a Minecraft version bump.
