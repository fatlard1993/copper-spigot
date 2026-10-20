# Copper Spigot

A Fabric mod that adds one block: a copper tap that pours a whole source block on demand.

## Where It Goes

- **Hanging** from the underside of a block, a short pipe straight down.
- **On a wall**, a pipe out of the wall with the spout turned down.

Never on a floor: a spigot pointing up pours nothing. Take away the block it is fixed to and it comes off with it, and drops.

## What It Pours

Think of pointed dripstone, made deliberate. A spigot draws from whatever is on the **far side of the block it is fixed to**: the block above the ceiling it hangs from, or the block behind the wall it juts out of. That can be:

- a water source, or any waterlogged block (which is water)
- a lava source
- a full water cauldron, or a lava cauldron

Flowing water and flowing lava are not sources.

What it draws lands **directly beneath the spout**:

- An empty cauldron is filled to the brim, with water or with lava; a part-filled water cauldron is topped up. A full cauldron, or one holding something else, is left alone.
- Anywhere a bucket could pour, a source block goes down, by a bucket's rules: a waterloggable block is waterlogged, and water poured in the Nether boils away with a hiss.
- Anywhere else nothing comes out, and the tap gives a dry click to say it turned.

## Water Is Free, Lava Is Not

Water is never used up, as dripstone never uses it up and two buckets already make it endless. **Lava is**: the source block, or the lava cauldron, is emptied by every pour. A spigot moves lava; it never makes it.

## Turning It

- **Right-click** it with an empty hand, or with anything that is not a block or a bucket. A held block is placed and a held bucket is used as normal.
- **Redstone**: it pours once each time a signal arrives. Holding the signal does nothing more; the next pour waits for the next pulse, the way a dispenser does.

## Crafting

Three copper ingots in a hook, which makes one:

```
C C
  C
```

## Pandorical

Copper Spigot runs server-side, and Pandorical is required there: the server will not load this mod without it. It ships the spigot's block, item, models and textures through Pandorical's content sync.

**The Pandorical mod must be installed client-side** to see it. The spigot is a real registered block, so a client without Pandorical cannot render it and cannot receive chunks containing one.

## Development

Installing, the map of the source and the art pipeline are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
