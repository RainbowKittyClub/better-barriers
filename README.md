# Better Barriers

Five barrier blocks that each let *some* things through. A vanilla barrier stops everything; these
name who passes, and a permission node lets anyone else through. Server-side: vanilla clients need
nothing installed.

## The barriers

| Block | Who passes | Bypass node |
| --- | --- | --- |
| `betterbarriers:players_only_barrier` | every player; mobs, items and projectiles stop | none |
| `betterbarriers:one_way_barrier[facing=…]` | anything travelling toward `facing`, never back | `betterbarriers.bypass.oneway` |
| `betterbarriers:admin_barrier` | nobody | `betterbarriers.bypass.admin` |
| `betterbarriers:gamemode_barrier[mode=…,rule=…]` | `rule=allow`: players whose game mode is `mode` (`survival`, `creative`, `adventure`); `rule=block`: every player but those | `betterbarriers.bypass.gamemode.<mode>` |
| `betterbarriers:keyed_barrier` | players holding the key item in either hand | `betterbarriers.bypass.keyed.<namespace>.<path>` |

A rider passes where their mount would not: the player steering a horse or boat is the one asked.
All five are waterloggable, unbreakable in survival, immune to the wither and dragon, and never
suffocate anything standing inside them.

**Permission nodes** are asked through fabric-permissions-api when a provider such as LuckPerms is
installed. Operators (level 2) pass admin barriers unless the admin node is denied. Every other
barrier holds operators to its rule like anyone else until its node is granted, so an op can test
a one-way gate, a gamemode zone or a locked door as a player would. Without a permissions mod,
only the admin barrier has a way through. The keyed node spells the item id with dots:
`betterbarriers.bypass.keyed.minecraft.tripwire_hook`.

## Placing them

There are no recipes. For clients running Polymer, the items are in their own "Better Barriers"
creative tab, shown with the gamemode barrier, and in vanilla's Operator Utilities tab, the
gamemode barrier once per mode and rule. Each one has an item that looks like a vanilla barrier:

```
/give @s betterbarriers:players_only_barrier
/give @s betterbarriers:gamemode_barrier[betterbarriers:gamemode="adventure",betterbarriers:gamemode_rule="block"]
/give @s betterbarriers:keyed_barrier[betterbarriers:key="minecraft:tripwire_hook"]
```

- A **one-way barrier** is passable the way you face when placing it. In commands use `facing`:
  `/fill … betterbarriers:one_way_barrier[facing=east]`.
- A **gamemode barrier** item is named for what it places, such as "Allow Creative Barrier";
  it starts as allow, creative. Sneak and right-click in the air to cycle survival, creative,
  adventure; sneak and left-click anywhere to switch between allow and block (a block or creature
  you were aiming at is left alone, and holding the button switches once). In commands:
  `[mode=adventure,rule=block]`. Mobs and items never pass a gamemode barrier.
- A **keyed barrier** item has no key until you set one: hold the key in your off hand and
  sneak-use the barrier item on nothing. In commands the key is block-entity data:
  `/setblock ~ ~ ~ betterbarriers:keyed_barrier{key:"minecraft:tripwire_hook"}`. A keyed barrier
  with no key opens for nobody. The key matches the item type only, not its name or data.

## Editing

Hold any Better Barriers item in your main hand in creative mode. Every barrier within 16 blocks
shows its sprite, and you can target, break and pick-block any of them, while they still let you
through or stop you exactly as they would without the item, so you can build and test together.

A one-way barrier's arrow points the way it lets you through, its broad side toward your left and
right as you placed it, readable from either side; an up or down arrow turns to face you. The
items show their sprites in your inventory too.

This needs the server's resource pack. It hides the marker of level-0 light blocks, since your
client is shown open barriers as those while you edit, so a real level-0 light block shows no
marker to anyone with the pack. Without the pack you get vanilla barrier items and markers, and
only the barriers that stop you can be targeted.

## Notes

- A player who walks through a one-way barrier and turns straight back within one network round
  trip is bounced once by the server before their client learns the barrier closed behind them.
- Picking any barrier with middle-click gives an item with its settings: a keyed barrier's key,
  a gamemode barrier's mode and rule.
- A keyed barrier's key is per block. To change a placed one, `/data merge block … {key:"…"}` or
  replace it.
- Every block, item, block-entity type and data component this mod registers is a Polymer
  server-side entry, hidden from Fabric's registry sync, so a client without the mod connects
  whether it runs Fabric or not.

## Installing

Drop `betterbarriers-<version>.jar` into `mods/`. Requires Fabric API and Polymer (`polymer-core`).
Vanilla clients work unmodified and are offered no resource pack by this mod.

## Building

See [`docs/BUILDING.md`](docs/BUILDING.md).
