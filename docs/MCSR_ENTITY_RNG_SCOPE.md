# MCSR Like entity-drop RNG scope — Ruleset R1

This document fixes the explicit scope of stage E (E046-E060).

## Standardized

### Blaze Rod (`BLAZE_DROP`)
- Player-attributed Blaze deaths only.
- One Blaze death consumes exactly one `BLAZE_DROP` event.
- Base 0..1 rod count and Looting bonus are deterministic subdraws of the same event.
- Blaze UUID, coordinates, kill time and unrelated RNG do not affect the sequence.

### Enderman Pearl (`ENDERMAN_DROP`)
- Player-attributed Enderman deaths only.
- One global sequence across Overworld, Nether and End.
- One Enderman death consumes exactly one `ENDERMAN_DROP` event.
- Base 0..1 pearl count and Looting bonus are deterministic subdraws of the same event.
- Environmental/other-entity Enderman deaths remain vanilla and do not consume the runner stream.

### Food (`FOOD_DROP`)
- Player-attributed food drops from Cow, Mooshroom, Pig, Sheep, Chicken, Rabbit and Hoglin.
- One relevant animal death consumes exactly one `FOOD_DROP` event.
- Base food count and Looting bonus are subdraws of the same event.
- Vanilla raw/cooked conversion (fire / smelts-loot enchantments) is preserved.
- Hoglin AI, spawn, combat, leather and every other non-food mechanic remain vanilla.

### Iron Golem
- In MCSR Like every Iron Golem iron-ingot drop is forced to exactly 4 iron.
- This rule does not consume a competitive RNG stream.
- Poppies remain vanilla.

## Intentionally NOT standardized in R1

- Leather from Cow/Mooshroom/Hoglin.
- Feathers from Chicken.
- Wool from Sheep death (shearing has its own later RNG stage).
- Rabbit hide and rabbit foot.
- Any drops from entities not explicitly listed above.
- Environmental / other-entity deaths for Blaze/Enderman/food sequences when no player attribution exists.
- Entity spawning, AI, UUIDs, positions and combat behavior.

## Composite-event rule

Looting must never shift the next kill index. Every standardized entity death consumes one outer stream event. Any number of internal loot rolls for that death are derived deterministically from the event raw value using subdraw indexes.
