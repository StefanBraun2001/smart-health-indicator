# Smart Health Indicator - Detailed Guide

Client-side Fabric mod for MC 26.2. Shows entity health as floating text
above/below nearby entities and as a line under your crosshair - built as a
maintained, non-freezing replacement after older health-indicator mods on
Modrinth either stopped getting updated or started showing stale/wrong
health on multiplayer servers.

## Installation

1. Install Fabric Loader for MC 26.2.
2. Install **Fabric API**.
3. Install **Cloth Config API** (required - the config screen depends on it).
4. Optionally install **Mod Menu** for an in-game config entry point;
   without it, edit `config/smarthealthindicator.json` directly.
5. Drop the mod jar from [Releases](../../../releases) into your `mods/` folder.

## Master on/off - Display enabled + toggle key

**Display enabled** (default: on) is a manual master switch for the whole
mod's output - world overlay and crosshair line both. Toggle it with the
**G** key (default) or flip it directly in the config screen; either way
it's saved, so it persists across restarts. Pressing the key shows an
"ON"/"OFF" message above the hotbar. The hold-to-peek key (see below) still
works even while this is off - it's meant as a quick "check anyway"
override independent of the master switch.

## Passive world overlay

Floating health text drawn near nearby entities, filtered by **Passive
display mode**:

- **OFF**: no floating health text at all (the peek key still works).
- **DAMAGED_ONLY**: only entities that are not at full health.
- **ALL**: every living entity in range.

**Show above head** / **Show at feet** (independent toggles, above head is
on by default) pick where the text is drawn relative to the entity. With
both on, an entity with clear space at both anchors would normally show its
health twice - see **Prioritize head over feet** below to avoid that.

**Prioritize head over feet** (default: off): only matters with both
anchors on. On: shows only the head text when it has a clear line of
sight, falling back to the feet text only when the head position itself is
blocked (e.g. a 2-block-high space where the ceiling blocks the head anchor
but the feet anchor is clear).

**Show entity name** (default: off): draws the entity's name on its own
line above the health text, for whichever anchor(s) are active. One toggle
for both.

**Horizontal radius** / **Vertical radius** (blocks, float): how far from
you an entity can be and still show. Checked independently of each other -
an entity has to be within both to appear.

**Health decimal places** (0-2, default 1): health is a float, e.g.
"12.3 / 20.0" at 1 decimal place.

## Hearts display

**Use hearts instead of numbers** (default: off): swaps the text for a row
of vanilla heart icons (same textures/sizing as your own health bar).
Applies to both the world overlay and the crosshair line.

Hearts round to the nearest half-heart, so e.g. 19.7/20.0 looks identical
to a genuine 20.0/20.0. **Full-health checkmark** (default: on, only
matters with hearts on) stamps a small checkmark on the last heart, but
only when health truly equals max health - resolving that ambiguity at a
glance.

## Text scale

**Text scale** (default 1.0): shrinks or grows all of the above. Applies
to both the world overlay and the crosshair line. Clamped to a small
positive minimum at render time so a stray 0 or negative value can't break
the text matrix.

## Combined jockey indicator

A jockey is a living entity riding another living entity - boats and
minecarts don't count, since they aren't living entities, so this never
triggers for those.

**Combined jockey indicator** (default: off): off, a jockey stack shows one
line per member like any other entity. On, it shows exactly one indicator
for the whole stack, cycling through each member's health/name every 2
seconds, with a distinct color per stack position (bottom = blue-ish) so
you can tell at a glance which member is "live" right now.

**Jockey position prefix** (default: on, only matters with the combined
indicator on): prefixes the health line with which stack member is
currently shown - `B`(ottom)/`T`(op) for a 2-member stack; for 3 or more,
bottom is still `B`, top is still `T`, and everything between is `M1`,
`M2`, ... counting up.

Test command for a 4-member jockey stack (nested `Passengers` NBT is the
general technique for summoning a jockey stack of arbitrary depth):

```
/summon minecraft:iron_golem ~ ~ ~ {Passengers:[{id:"minecraft:villager",Passengers:[{id:"minecraft:villager",Passengers:[{id:"minecraft:villager"}]}]}]}
```

## Line-of-sight occlusion

The overlay always respects real block occlusion: opaque blocks (stone,
dirt, doors, ...) hide it for every entity, no exceptions, no toggle.

**Show through transparent blocks** (default: on) only controls the
borderline case of non-occluding blocks (glass, leaves, water, ...), and
only for whatever entity is currently under your crosshair - modeled after
Health Indicators' (github.com/AdyTech99/HealthIndicators) "show through
walls" scope. On: you can see through such blocks at your current target.
Off: any block, transparent or not, hides the indicator there too. This is
a targeted peek at one entity, never a radius-wide x-ray - every other
entity in range still obeys full opaque-only occlusion regardless of this
setting.

## Hold-to-peek key

Default **H**. While held, temporarily overrides the passive filter,
governed by **Peek key behavior**:

- **SHOW_ALL** (default): always shows every living entity while held,
  ignoring the configured Passive display mode entirely.
- **DAMAGED_ONLY**: always shows only damaged entities while held,
  ignoring the configured mode.
- **USE_CONFIGURED_MODE**: uses Passive display mode as-is while held (so
  a configured DAMAGED_ONLY stays DAMAGED_ONLY instead of being upgraded to
  ALL), falling back to ALL only if the configured mode is OFF - so the
  peek key always shows something.

## Crosshair line

**Show crosshair line** (default: on): shows the name and health (text or
hearts, per the setting above) of whatever living entity you're currently
aiming at, within your actual interaction reach - not the overlay's
radius. Drawn as two lines near the crosshair: name above, health below.
Gated by the master **Display enabled** switch, same as the world overlay.

## Entity filtering

Applies identically to both the world overlay and the crosshair line -
anything filtered out here never gets an indicator either way.

- **Show hostile mobs** (default: on): `MobCategory.isFriendly() == false`
  - zombies, skeletons, etc.
- **Show passive mobs** (default: on): `MobCategory.isFriendly() == true`
  - cows, villagers, etc.
- **Show other players** (default: on): other players, not yourself.
- **Show self** (default: off): also shows an indicator for your own
  client player - useful e.g. in third-person view.

**Entity type list mode** / **Entity type list**: `BLACKLIST` (default)
shows everything except the listed registry IDs (e.g. `minecraft:zombie`,
case-insensitive); `WHITELIST` shows only what's listed - an empty
whitelist then shows nothing. Applied on top of the hostile/passive
toggles above.

**Player name list mode** / **Player name list**: same BLACKLIST/WHITELIST
choice, but for exact usernames (case-insensitive) instead of entity types.

## Health text caching

Off by default - recomputing health text every frame is already cheap for
normal entity counts.

**Cache health text** (default: off): when on, reuses the formatted "x / y"
text for up to **Cache duration** (0-1000ms, default 200) instead of
recomputing it every frame. This is purely a performance knob for scenes
with many nearby entities - entity presence/range is always checked live
regardless of this setting, and the cache expires by real wall-clock time
(not game ticks), so a stalled or laggy server connection can never leave
stale numbers stuck on screen. It's also fully cleared on every
disconnect/rejoin, for the same anti-staleness reason.

## Performance

Live per-entity work is a projection call plus (for the crosshair target
only, when transparent-block leniency is in play) a short repeated
raycast - both cheap relative to normal rendering, and nothing here scales
with server tick rate or connection quality, which was the whole point:
older health-indicator mods this replaces would freeze or show wrong
numbers exactly when the connection got rough. Enable **Cache health text**
if you're running with very large detection radii and many entities on
screen at once.

## Troubleshooting

- **Nothing shows up at all.** Check **Display enabled** is on (or hold the
  peek key), and that **Passive display mode** isn't set to OFF while also
  not peeking.
- **An entity shows through a wall I didn't expect.** Check **Show through
  transparent blocks** - it only ever applies to your current crosshair
  target, and only for non-opaque blocks like glass; opaque blocks always
  block it for everyone.
- **A jockey stack's health looks like it's flickering between values.**
  That's the combined jockey indicator cycling through stack members every
  2 seconds by design - turn off **Combined jockey indicator** to instead
  see one line per member at once.

## License

MIT - see [LICENSE](../LICENSE).
