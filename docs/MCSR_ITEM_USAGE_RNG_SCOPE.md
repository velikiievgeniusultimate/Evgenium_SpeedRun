# MCSR Like item-usage RNG scope — Ruleset R1

This document fixes the explicit scope of stage F (E061-E075).

## Eye of Ender (`EYE_BREAK`)
- Only actual Eye throws consume the stream.
- Placing an Eye into an End Portal Frame does not consume `EYE_BREAK`.
- Vanilla survive chance remains 80% (the vanilla `nextInt(5) > 0` rule).
- Throw #2 is forced to survive, matching MCSR Ranked.
- The forced second throw still consumes index #1 so later throw numbering remains stable.

## Rotten Flesh (`ROTTEN_FLESH_HUNGER`)
- Every completed Rotten Flesh consumption consumes one event.
- The vanilla 80% Hunger-effect chance is retained, but its random decision is competitive.
- Other foods using `ApplyStatusEffectsConsumeEffect` remain vanilla.

## Suspicious Stew (`SUSPICIOUS_STEW`)
- Only stews whose effect is randomly selected by vanilla `SetStewEffectFunction` consume this stream.
- Crafted stew and brown-Mooshroom stew already have deterministic flower-defined effects and are left untouched.
- One generated random stew consumes one outer event; effect choice and random duration are deterministic subdraws.
- Every current/future effect supplied by the vanilla loot pool remains eligible except Poison.
- Poison is never selected in MCSR Like, matching Ranked.

## Endermites from Ender Pearls
- Vanilla 5% Endermite spawn chance is retained.
- The random roll is standardized independently per dimension:
  - `ENDERMITE_OVERWORLD`
  - `ENDERMITE_NETHER`
  - `ENDERMITE_END`
- Throwing pearls in one dimension never advances the sequence in another.
- The legacy `ENDERMITE` enum label is retained only for ruleset/debug compatibility and is not consumed by stage F.

## Sheep shearing (`SHEEP_SHEAR`)
- The vanilla adult-sheep shearing count of 1..3 wool is standardized.
- Sheep color remains vanilla/current-world state; only the wool count is standardized.
- Shearing Mooshrooms, Bogged, Snow Golems and other shearable entities remains vanilla.

## Intentionally not standardized in this stage
- Eye launch sound pitch and particles.
- Ender Pearl trajectory/teleport physics and Endermite AI.
- Random effects of non-Rotten-Flesh foods.
- Crafted Suspicious Stew flower choice (not random at use time).
- Sheep spawn color, breeding color and regrowth timing.
