# MCSR Like block RNG scope — Ruleset R1

This document is the explicit scope contract for competitive block-drop RNG. Anything not listed as standardized remains vanilla unless a later ruleset revision says otherwise.

## Standardized

### Gravel -> Flint (`FLINT`)
- Normal/non-explosion gravel loot only.
- Vanilla Fortune chance table is preserved.
- Silk Touch remains vanilla and does not consume `FLINT`.
- Each eligible gravel flint decision consumes exactly one `FLINT` event.

### Dead Bush -> Stick count (`DEAD_BUSH_STICK`)
- Normal/non-explosion dead-bush destruction only.
- The vanilla 26.2 count range 0..2 is preserved.
- Each eligible dead-bush stick-count roll consumes exactly one `DEAD_BUSH_STICK` event.
- Shears remain on the vanilla dead-bush item branch and do not consume the stream.

### Oak/Dark Oak leaves -> Apple (`APPLE_DROP`)
- `oak_leaves` and `dark_oak_leaves` only.
- Player breaking and natural leaf decay are both standardized.
- Vanilla Fortune chance table is preserved.
- Shears and Silk Touch remain vanilla and do not consume `APPLE_DROP` because the apple pool is skipped by vanilla.
- Each eligible apple decision consumes exactly one `APPLE_DROP` event.

## Explicitly NOT standardized in R1

- Any block loot generated with an explosion context (`survives_explosion`, `explosion_decay`, etc.). Explosion-driven loot remains fully vanilla and consumes no competitive block-drop stream.
- Sapling drops from any leaves.
- Stick drops from leaves.
- Leaves/items dropped directly by Shears or Silk Touch.
- Apples from chests, entities, trades, recipes, commands, or any non-leaf source.
- Birch, Spruce, Jungle, Acacia, Mangrove, Cherry, Pale Oak, Azalea and Flowering Azalea leaf drops. Their modern vanilla behavior is preserved; they do not consume `APPLE_DROP`.
- Any other block-drop RNG not explicitly named above.

## Independence rule

`FLINT`, `DEAD_BUSH_STICK` and `APPLE_DROP` are independent streams. Consuming any number of events from one stream must never alter the result or event index of another stream.

## Why explosion loot is excluded

Vanilla checks such as `survives_explosion` can occur before the targeted drop roll. If competitive streams advanced only when a preceding vanilla explosion roll happened to pass, unrelated vanilla RNG could shift future competitive results. R1 therefore treats all explosion-driven block loot as an explicit vanilla boundary: no competitive block-drop stream is consumed in an explosion loot context.
