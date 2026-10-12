# Better Barriers

 ### Players-only Barrier

 Only lets players through, stops all other entities including mobs, items, projectiles and mounts.

  Can be configured with gamerules:
   - `betterbarriers:players_only.allow.mobs`
   - `betterbarriers:players_only.allow.items`
   - `betterbarriers:players_only.allow.projectiles`
   - `betterbarriers:players_only.allow.mounts`

  ### One-Way Barrier

  Only allows entities to pass through in the direction it's facing.

  Can be bypassed with `betterbarriers.bypass.oneway`.

  ### Admin Barrier

  Only allows players with OP level 2+ or `betterbarriers.bypass.admin`.

  ### Gamemode Barrier

  Allows or denies all players of a specific gamemode.

  The block barrier blocks only players of the specified gamemode, allows all other entities.
  The allow barrier only allows players of the specified gamemode and blocks all other entities.

  Players with `betterbarriers.bypass.gamemode.[adventure/creative/survival]` permissions node
  are able to bypass.

  ### Keyed Barrier

  Anyone holding the associated key item in either hand is able to pass.

  Bypass for a specific lock ID can be configured with the permission node: `betterbarriers.bypass.keyed.<lock_name>`
  Keyed barrier lock IDs should be unique, any item with the same lock value will allow passge through the barrier.

## Editing

Editing requires the resource pack. Passable barriers in edit mode are replaced with the level-0
light block for selectability. Each barrier has its own icon in edit mode that distinguishes it
from the others. Editing can be done by holding a barrier item.

- A **one-way barrier**: In commands use `betterbarriers:one_way_barrier[facing=east]`.

- A **gamemode barrier** item is named for what it places, such as "Allow Creative Barrier";
  it starts as allow, creative. Sneak and right-click in the air to cycle survival, creative,
  adventure; sneak and left-click in the air to switch between allow and block.
  In commands: `betterbarriers:gamemode_barrier[mode=adventure,rule=block]`

- A **keyed barrier** requires a key to be set before placing: hold the key in your off hand and
  right-click the air while sneaking. The key is any item that has the `custom_data={betterbarriers:{locks:[]}}`
  NBT data. `locks:[]` can be more than one value like `locks:["front_door", "castle.gate.east", "castle.gate.west"]`.
  Keys cannot be copyable items like writable/written books, maps, or banners.
  In commands: `betterbarriers:keyed_barrier{lock:"front_door"}`.

## Installing

Drop `betterbarriers-<version>.jar` into `mods/`. Requires Fabric API and Polymer (`polymer-core`).

## Building

See [`docs/BUILDING.md`](docs/BUILDING.md).
