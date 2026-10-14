# Conditional barriers

A new first-party server-side Fabric mod, `better-barriers` (mod id `betterbarriers`, package
`club.rainbowkitty.betterbarriers`, folder `rkc/mods/better-barriers`), adds five barrier blocks
whose passability depends on who is trying to pass. Vanilla barriers block everything; these each
name a condition, and the matching permission node passes regardless:

| Block | Passes | Bypass node |
|---|---|---|
| `players_only_barrier` | any player; mobs, items, projectiles and mounts each by game rule (S16) | none |
| `one_way_barrier[facing]` | anything entering from behind, travelling toward `facing` | `betterbarriers.bypass.oneway` |
| `admin_barrier` | nobody | `betterbarriers.bypass.admin` |
| `gamemode_barrier[mode,rule]` | `rule=allow`: players in `mode`; `rule=block`: everything but them (S12, S16) | `betterbarriers.bypass.gamemode.<mode>` |
| `keyed_barrier` (block entity `lock`) | players holding a key listing its lock in either hand (S17) | `betterbarriers.bypass.keyed.<lock>` |

This table is the design as it now stands; each stage below records the plan of its day. A mount or
vehicle carrying a player goes as that player everywhere but players-only (S16).

Every node falls back to op level 2 (`PermissionLevel.GAMEMASTERS`; S10 narrows that to the admin
node) when no permission provider is loaded, through kalic0re `CommandPermissions.holds`. Vanilla
clients need no install: Polymer tells each client either `minecraft:barrier` or `minecraft:air`
per block, per player.

Every API claim below was checked against `libs/decompiledjava/minecraft-26.2/`, the Polymer
`dev/26.2` source checkout (0.17.5) and the kalic0re / Fabric API 0.157.0 jars on 2026-10-10;
citations are `File.java:line` in those trees. Mixin target strings are still runtime-verified
only, per `docs/PORTING.md`. Unverified: the exact reconcile cost under load (S8 measures it).

Stage order: S1 (scaffold) → S2 (block framework, players-only) → S3 (per-player correction layer,
admin barrier) → S4 (editing view). S5 (one-way), S6 (gamemode) and S7 (keyed) each need S3 and are
independent of each other. S7 completes the feature; S8 is the load test and the As-built record.

Three readings of the request were put to the user before approval; all three landed on the form
written above: node root `betterbarriers.bypass.*` (mod id, not the hyphenated spelling), the
gamemode barrier passes players *in* its mode, and the keyed barrier opens for whoever holds the
key. The inverse of either polarity is one predicate flip if ever wanted.

---

## Findings that reshape the work

**Polymer already swaps a Polymer block's collision shape for the client state's, but only for
`ServerPlayer` contexts, and the block can opt out per call.** `BlockStateBaseMixin:29-39`
(polymer-core) injects at the head of `BlockStateBase.getCollisionShape(BlockGetter, BlockPos,
CollisionContext)` and returns the client state's shape when the context entity is a `ServerPlayer`
and `PolymerBlock.overridePlayerCollisionsWithPolymer(level, pos, state, player)` is true
(`PolymerBlock.java:89-91`, default true). Mobs, items, projectiles and the empty context skip it.
So the block overrides that method to return `false` and owns `getCollisionShape` for everyone.
`BlockBehaviourMixin:23,34` hooks `BlockBehaviour.getShape`/`getCollisionShape` bodies, which a
subclass that overrides both without `super` never reaches; so override both.

**The client cannot be told a per-position state through Polymer.** `PolymerBlock` has only
`getPolymerBlockState(BlockState, @Nullable PacketContext)` (`PolymerBlock.java:31`), no position,
and it runs on netty threads at encode time for block updates (`ByteBufCodecsEntriesMixin:65-71`)
and under a bound context on the server thread for chunks (`PlayerChunkSenderMixin:23-27`). So it
returns one fixed default per block, and a separate layer sends raw per-player corrections
(`ClientboundBlockUpdatePacket(pos, AIR|BARRIER|WATER)`), which Polymer passes through untouched
(`BlockMapperImpl.java:18`; replace/prevent have no case for it).

**Vanilla and Polymer both resend real block states often enough that raw corrections must be
made sticky, not just sent.** After every right-click vanilla resends the clicked block and its
neighbour, and Polymer resends all six neighbours (`ServerGamePacketListenerImpMixin:268-276`,
`sendBlocksAroundClicked` default true). Chunk resends (`reloadLevel`, teleport-and-return) and
multi-block section updates also carry Polymer's default. Every one of those passes through
`ServerCommonPacketListenerImpl.send(Packet, ChannelFutureListener)`
(`ServerCommonPacketListenerImpl.java:160`; the 1-arg form delegates, :156-158; every block,
section and chunk carrier was traced to `player.connection.send`, none goes through a bundle or raw
`Connection.send`), where Polymer itself hooks (`@ModifyVariable` at HEAD,
`ServerCommonPacketListenerImplMixin:71`). One mixin of our own there rewrites a resend of a
corrected position back to the correction, and re-sends or drops corrections a chunk or section
packet touches. This replaces both a chunk-send hook (`PlayerChunkSender.sendChunk` is private
static, `PlayerChunkSender.java:76`) and the periodic refresh `artifacts` `AltitudeFence` uses.

**A section packet does not mean the state changed.** `ChunkHolder.blockChanged` records any
position handed to it (`ChunkHolder.java:123-135`), and `ServerLevel.sendBlockUpdated`
(`ServerLevel.java:1138`) is called with identical old and new states by `/data merge block`
(`BlockDataAccessor.java:65`), NBT-only `/setblock` and `/fill` (`BlockInput.java:94`),
`ServerPlayerGameMode.java:270` and several block entities. So the hook decides by state identity,
never by packet type: an unchanged state gets the correction re-sent in the same batch, a changed
one drops it. (`Level.setBlock` itself never reaches `sendBlockUpdated` for a no-op,
`LevelChunk.java:275-283`, `Level.java:231-243`.)

**Suffocation ignores the per-entity shape.** `Entity.isInWall` (`Entity.java:2252-2268`) uses
`isSuffocating`, whose default is `blocksMotion && isCollisionShapeFullBlock` evaluated from the
empty-context cache (`BlockBehaviour.java:1006, 921`). A player standing inside an open barrier
would take suffocation damage. The properties set `isSuffocating(Blocks::never)` and
`isViewBlocking(Blocks::never)` (`BlockBehaviour.java:1202,1207`; `Blocks.java:5750`). No
`dynamicShape()`: the empty-context cache (full cube) is exactly what pathfinding,
`isCollisionShapeFullBlock` and redstone conduction should see, and keeping the cache keeps the
hot 2-arg shape queries cheap.

**Barriers are invisible, so only blocks within collision reach need the right per-player state.**
Barrier and air render identically (no model, no occlusion, light 0) and the marker particle is a
client-side effect of a creative player's main-hand item (`ClientLevel.java:181,571-581`). So the
correction layer scans a small region around each player rather than indexing every barrier in
view. The scan is gated per chunk section by `LevelChunkSection.maybeHas(Predicate<BlockState>)`
(`LevelChunkSection.java:195`), a palette walk, so a section with no better-barrier costs a few
dozen `instanceof` checks and no block reads. No block index, no block-change hooks.

**Every type funnels through one mechanism.** Game-mode and held-item changes are read each tick
during the reconcile; permissions are cached per player for 20 ticks. No game-mode event exists in
Fabric API (checked all 43 bundled modules), and none is needed.

---

## How these stages get tested

Three places, in this order for every stage:

1. **Build and lint.** `scripts/in.sh better-barriers ./gradlew build`, then
   `cd scripts/lint && ./gradlew lintAll` (S1 adds the mod to the `mods` map). Proves compile and
   style only (`docs/TESTING.md`).
2. **Bot legs** in a new `scripts/loadtest/better-barriers.js`, patterned on
   `rkcore-commands.js` (RCON setup, LuckPerms node grants, PASS/FAIL legs, exit code) but with
   `physicsEnabled: true`, since a mineflayer bot's physics runs against the block states the server
   sent it; a bot walking into a barrier therefore tests the per-player client state, and a server
   teleport-back (`forcedMove`) or a `moved wrongly!` log line tests server agreement. Bots join as
   26.1 through the ViaFabric + ViaBackwards jars already in `rkc/mods/rkcore-common/run/mods`;
   LuckPerms from the same folder. A "walk leg" faces the bot at a barrier, holds forward for 20
   ticks, and reads displacement across the barrier plane: under 0.1 blocks is blocked, over 1.5 is
   passed, anything with a `forcedMove` is a fail. Mob and item legs use `/summon` and
   `/data get entity … Pos` over RCON.
3. **In-world checks** on the dev server (port 25620, rcon 25622) by the user, briefed per leg
   before each round and with the server left up; needed where a bot cannot see (marker particles,
   editing view, feel of latency).

A stage is not finished until its "Done when" is observed. The mixin added in S3 is verified by
`scripts/devserver.sh rkc/mods/better-barriers mixins` reporting 0 failures on boot, before any
leg runs.

---

## S1 — Scaffold and shared item-name helper — **done**

**Build.**

- kalic0re: add `common/item/ItemNames` — `static <T extends Item> T named(ResourceKey<Item> key,
  Component name, T item)`, the `ArtifactNames` mechanism (`artifacts/.../ArtifactNames.java:28-34`:
  a `BuiltInRegistries.DATA_COMPONENT_INITIALIZERS` entry added after the item's own, so it wins
  over `Item.Properties.finalizeInitializer`, `Item.java:680-687`) without the gold-italic
  styling. Bump to 0.26.0, `publishToMavenLocal`. `artifacts` keeps its own copy for now.
- Clone `rkc/mod-template_fabric` to `rkc/mods/better-barriers`, run `./setup.sh`: id
  `betterbarriers`, entry `BetterBarriers`, name `Better Barriers`, repo
  `https://github.com/RainbowKittyClub/better-barriers`, licence Unlicense. Ports 25620/25622:
  setup.sh's pick, 25612, was already held by a scratch server outside `rkc/mods/`.
- `gradle.properties`: delete `bil_version`/`bil_min_version` and the datagen keys; keep
  `polymer_version`; set `kalic0re_version=0.26.0`, `kalic0re_shared_packages=command,text,item`.
- `fabric.mod.json`: move `polymer-core` from `suggests` to `depends`, drop `bil` and the datagen
  entrypoint. Delete the template's `content/`, `polymer/`, example assets, datagen class.
- `scripts/lint/build.gradle`: add `better-barriers` to the `mods` map.
- Copy this plan into `docs/plan/conditional-barriers.md` in PLANNING.md form.

**Done when.** `./gradlew build` yields `build/libs/betterbarriers-0.1.0.jar`;
`scripts/devserver.sh rkc/mods/better-barriers start` reaches `For help, type` with the mod listed
in `/polymer`-free boot log; `lintAll` passes with the new entry.

### As built

setup.sh needs GitHub over ssh for its template fetch and submodule add, which the sandbox has
no route for, so its steps were run by hand against the local `rkc/mod-template_fabric` checkout
(`origin/template` and the local `gradle` branch, the same `4dcbbaf` every sibling pins), with
the submodule URL in `.gitmodules` then rewritten to the GitHub one. Its port pick, 25612, was
already bound by another job's scratch server, so the mod runs on 25620/25622. kalic0re 0.26.0
with `common/item/ItemNames` is published to mavenLocal and uncommitted in that checkout.
`run/mods` carries LuckPerms, vanilla-permissions, ViaFabric + ViaBackwards and spark, and
`max-players` is 100 for S8. Build, boot and `lintAll` entry all green on 2026-10-11.

## S2 — Block framework and players-only barrier — **done**

**Build.**

`block/BetterBarrierBlock` — `abstract class … extends BarrierBlock implements PolymerBlock`
(`BarrierBlock.java:25`: waterloggable, invisible, `propagatesSkylightDown`; protected ctor).
Properties: `Properties.ofFullCopy(Blocks.BARRIER)` (`BlockBehaviour.java:1022`; the barrier's
list is `Blocks.java:2928-2939`) plus `isSuffocating(Blocks::never)`,
`isViewBlocking(Blocks::never)`, `setId(key)`.

- `abstract boolean admits(BlockState state, BlockPos pos, Entity mover)` — the pass condition.
  Inside, the *subject* for permission, game-mode and held-item checks is
  `mover.getControllingPassenger() instanceof ServerPlayer p ? p : mover`
  (`Entity.java:3506`; horses and boats override it), so a ridden horse passes where its rider
  would. One-way uses `mover.getBoundingBox()`.
- `getCollisionShape(state, level, pos, ctx)`: `ctx instanceof EntityCollisionContext e &&
  e.getEntity() != null && admits(state, pos, e.getEntity()) ? Shapes.empty() : Shapes.block()`
  (`EntityCollisionContext.java:82-84`; the empty context has a null entity, `:95-97`). `getShape`
  the same, so server-side item raycasts (`Item.getPlayerPOVHitResult`, `ClipContext.Block.OUTLINE`)
  agree with what that player's client sees.
- `overridePlayerCollisionsWithPolymer(...)` → `false`.
- `defaultOpen()` — `true` only for players-only. `substitute(state, open)` → open ?
  (waterlogged ? `WATER` : `AIR`) : `BARRIER[waterlogged=…]`. `getPolymerBlockState(state, ctx)` →
  `substitute(state, defaultOpen())`, ignoring the (nullable, sometimes netty-thread) context.
- `admits` also returns `false` while the subject is *editing* (S4), so server and client agree.

`block/PlayersOnlyBarrierBlock` — `admits` = subject is a `Player`. `defaultOpen` = true, so chunk
packets already carry AIR and no correction is ever needed outside the editing view.

`item/BetterBarrierItem extends BlockItem implements PolymerItem` — `getPolymerItem` →
`Items.BARRIER`; name set through `ItemNames.named(key, FallbackText.of("item.betterbarriers.<id>"),
item)`; `appendHoverText` adds one line per type (facing, mode, key) which Polymer turns into lore
(`PolymerItemUtils.java:505-519`). Registered through kalic0re `ItemRegistration.register`.
`canPlace` additionally requires `level.isUnobstructed(null, Shapes.block().move(pos))`: vanilla's
check asks the *placer's* collision shape (`CollisionGetter.java:30-32`, `placementContext(player)`
carries the player), which is empty for an admitted placer, so without this a mob standing in the
cell would be entombed.

Each subclass declares its extra property `static`, since `createBlockStateDefinition` runs from
the `Block` constructor, and re-registers its default state (`BarrierBlock.java:34-37` registers one
with only `WATERLOGGED`). `codec()`: `BarrierBlock` already provides a concrete one (:26-32); a
subclass with a `(Properties)` constructor adds `simpleCodec(X::new)` and the override.

Registration in `block/BarrierBlocks`; `assets/betterbarriers/lang/en_us.json` (the only English,
read by `FallbackText.load(MOD_ID)` in `onInitialize`); static tag files under
`data/minecraft/tags/block/` for the five tags vanilla puts barrier in: `wither_immune`,
`dragon_immune`, `impermeable`, `cannot_support_snow_layer`, `blocks_wind_charge_explosions`.
No creative tab: Polymer tabs never reach a vanilla client, `/give` is the admin path.

`scripts/loadtest/better-barriers.js` skeleton with the walk-leg helper and the first legs.

**Done when.** Legs: (1) bot walks through a players-only wall, no `forcedMove`; (2) a zombie
summoned behind it with the bot as bait stays on its side for 5 s; (3) an item summoned above a
players-only floor rests on it. `grep -c 'moved wrongly' run/logs/latest.log` is 0 after the run.
Pathfinding mobs route around it (observed in leg 2: the zombie paths along the wall, not into it).

### As built

Three things the plan did not know. `BarrierBlock.codec()` is public and typed
`MapCodec<BarrierBlock>`, so each subclass's codec is declared as that type while still
constructing the subclass. `Blocks::never` is private; the properties use lambdas. And Fabric's
registry sync: a `PolymerBlock`, a `PolymerItem` and a type passed to
`PolymerBlockUtils.registerBlockEntity` are marked Polymer "server entries" and left out of the
sync map by `polymer-reg-sync-manipulator` (nested inside the bundle's polymer-core jar, on the
live server too), but a data component type is not until the mod calls
`PolymerComponent.registerDataComponent`. Without that call the two component types kept the
registry in the map, so a Fabric client without the mod was refused for unknown entries and a
vanilla one for not receiving the sync at all. With it, every registry this mod touches reads as
un-modded to Fabric and no sync is sent; `RegistryAttribute.OPTIONAL`, which the chopt plan
prescribes and an earlier build here set, is not needed and was dropped, since it is a
registry-wide relaxation. A vanilla-protocol bot joins either way; a Fabric client without the mod
only with the component call.

The bot also needed `fixResourcePackReplies` from `lib/flyer.js` (Polymer's configuration-phase
pack never sees mineflayer's answer), and the dev world's default mode is creative, so the harness
sets survival. The husk leg was replaced by an arrow: a mob's AI made it wander, where an arrow
tests the projectile clip path deterministically; the plan's pathfinding observation was therefore
not made. Legs 1-3 green: the bot walks through (x 1008.5 to 1017.1, no teleport back), an arrow
sticks on its side (x 1011.05 against x 999.17 beside the wall), a dropped item rests on the floor
(y 106.00).

## S3 — Per-player correction layer and admin barrier — **done**

**Build.**

`sync/BarrierViewer` — one per online player, in a `Map<UUID, BarrierViewer>` (`AltitudeFence`
pattern) created on demand, removed on `ServerPlayConnectionEvents.DISCONNECT`, cleared without
sends on `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL` and
`ServerPlayerEvents.AFTER_RESPAWN` (the client dropped the level). Holds:

- `ServerLevel level`; `Long2ObjectMap<Correction> corrections` where `Correction(BlockState
  evaluated, BlockState sent)`: the client holds `sent` at that position instead of the block's
  default, and `evaluated` is the real state it was computed from.
- a permission cache `Object2BooleanMap<String> nodes` refreshed every 20 ticks, answered through
  kalic0re `CommandPermissions.holds(player, node, PermissionLevel.GAMEMASTERS)`
  (`CommandPermissions.java:73-78`). This is the only place a permission is asked, so
  `getCollisionShape` never calls the provider.
- `clientState(pos, realState)` = correction's `sent` if present and its `evaluated == realState`,
  else the block's default substitute.

`sync/BarrierSync.tick(MinecraftServer)` on `ServerTickEvents.END_SERVER_TICK`, per alive,
non-spectator player (spectators have `noPhysics`, `Player.java:230`):

1. `reach = min(12, 3 + |knownMovement| × (4 + latency_ms / 50))` blocks, the `AltitudeFence`
   lookahead (`AltitudeFence.java:123-127`); 16 while editing (S4).
2. Region = `player.getRootVehicle().getBoundingBox().inflate(reach)`. For each chunk section it
   overlaps: `ServerChunkCache.getChunkNow(x, z)` (`ServerChunkCache.java:178`, never loads, null
   off-thread), skip if null, `hasOnlyAir()`, or `!maybeHas(IS_BETTER_BARRIER)`.
3. For each position in region ∩ section whose block is a `BetterBarrierBlock`: `open =
   admits(state, pos, rootVehicle)`, `want = substitute(state, open)`; if `want !=
   clientState(pos, state)`, send `new ClientboundBlockUpdatePacket(pos, want)` to
   `player.connection` and record (or remove, when `want` equals the default) the correction. Mark
   visited.
4. Every correction not visited this tick: if the position still holds a better-barrier (chunk
   loaded), send the default and remove; else just remove (vanilla already sent the new block).

`mixin/ServerCommonPacketListenerImplMixin` — two injectors on
`send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V`, both
delegating to `BarrierSync` when `this` is a `ServerGamePacketListenerImpl` (its `player` field is
public, `ServerGamePacketListenerImpl.java:240`), only on the server thread
(`server.isSameThread()`), and only when that viewer has corrections:

- `@ModifyVariable(at = @At("HEAD"), argsOnly = true)` on the `Packet<?>` argument,
  `betterbarriers$keepCorrections`: for a `ClientboundBlockUpdatePacket` at a corrected position
  whose state's block is a `BetterBarrierBlock`, return `new ClientboundBlockUpdatePacket(pos,
  correction.sent)` if `packet.getBlockState() == correction.evaluated`, else drop the correction
  and pass it through (the block changed; next tick re-evaluates). A vanilla-state update (our own
  follow-ups below, or a real replacement by air) passes through untouched; a stale correction left
  by the latter is removed by the reconcile's not-visited rule next tick. Returning a new packet
  matters: `ChunkHolder.broadcastChanges` sends one instance to every tracking player
  (`ChunkHolder.java:201-203, 236-238`), so nothing is mutated in place.
- `@Inject(at = @At("TAIL"))`, `betterbarriers$followCorrections`: after the original is queued,
  so follow-ups land in the same network batch and the client never runs a tick on the default.
  `ClientboundSectionBlocksUpdatePacket`: `runUpdates((pos, state) -> …)`
  (`ClientboundSectionBlocksUpdatePacket.java:72`; the arrays are private and the packet instance is
  shared, so no in-place rewrite; key by `pos.asLong()` since the `MutableBlockPos` is reused): a
  corrected position with `state == evaluated` gets `connection.send(new
  ClientboundBlockUpdatePacket(pos, sent))`, any other is dropped.
  `ClientboundLevelChunkWithLightPacket` (`getX()/getZ()`): every correction in that chunk is
  re-evaluated — still in reach and still wanted → re-sent now, else dropped. The chunk arrives
  with defaults and the corrections right behind it. `ClientboundForgetLevelChunkPacket` (`pos()`):
  drop the chunk's corrections.

The follow-up sends re-enter `send` with vanilla states, which the HEAD rule ignores and the TAIL
rule does not match, so there is no recursion. Residual window: none within a batch; a packet batch
split across two client ticks is the only way a default is ever acted on.

`block/AdminBarrierBlock` — `admits` = subject is a `ServerPlayer` holding
`betterbarriers.bypass.admin`. `defaultOpen` = false.

**Done when.** `devserver.sh … mixins` reports 0 failures. Legs: (4) bot without the node is
blocked; (5) with `lp user … permission set betterbarriers.bypass.admin true`, it passes within 1 s
of the grant without rejoining, no `forcedMove`; (6) with the node, the bot right-clicks the floor
block beside an admin barrier (`bot.activateBlock`), then walks through it — still passes (the
resend was rewritten); (7) `/tp` the bot 300 blocks away and back, walk again — passes; (8) node
unset, blocked again within 1 s. `moved wrongly` count stays 0. An op bot with no LuckPerms node
set passes (fallback level); a de-opped one does not.

### As built

Built as planned, and the legs found one bug: the permission cache used `Integer.MIN_VALUE` as
"never asked", so the TTL subtraction overflowed and the first answer stuck forever (an op or a
fresh LuckPerms grant never took). Fixed by starting the ask tick at `-TTL`. Measured with the
bot's own client view: a grant opens the admin barrier after 299 ms, an unset closes it after
601 ms, `/gamemode` opens a gamemode barrier after 99 ms, a hotbar change opens or closes a keyed
barrier after 100 ms. The right-click resend leg is green (the client still saw air after
clicking the floor beneath the barrier). The teleport-away leg failed twice for a harness reason,
the far point being over the void past the platform; it now forceloads and fills a pad there.
The mixin applies with 0 failures; `moved wrongly` stayed at 0 across every run.

## S4 — Editing view — **in progress**

**Build.** A creative player whose main hand holds any `BetterBarrierItem` is *editing*: the client
draws marker particles for every `minecraft:barrier` it holds under exactly that condition
(`ClientLevel.java:571-581`, barrier or light item in the main hand, creative), and a client cannot
target an air block to break or pick it. So while editing, `admits` returns `false` for that
subject and the reconcile uses reach 16: every better-barrier within 16 blocks is sent solid, shows
a marker, is targetable, breakable (creative) and pick-blockable (vanilla builds the pick from the
real state, `ServerGamePacketListenerImpl.java:700-718`). Switching the item away reverts within a
tick. Players-only barriers are corrected to BARRIER here, the one case the default-open type needs
a correction.

Also in this stage: sneak-use with a barrier item in hand on nothing cycles that item's variant
where it has one (one-way has none; gamemode cycles `mode`; keyed takes the off-hand item as key),
written to the item's component (S6, S7 define the components).

**Done when.** In-world: holding a players-only barrier item in creative, markers appear on a
players-only wall within 16 blocks and the player bumps into it; breaking one works; middle-click
yields the right item; switching to a stick, the player walks through the same wall. Reconcile
cost while editing beside a 64×8 wall measured with spark stays under 0.5 ms per tick (if not,
throttle the editing reconcile to every 5 ticks; markers tolerate it).

### As built

Code in: `BetterBarrierItem.isEditing` (creative, main hand holds any barrier item),
`admits` false for an editor, reach 16 while editing, and the sneak-use `cycle` hook that S6 and
S7 fill. The in-world checks (markers, targeting, breaking, pick-block, walking through again
after switching item) need a real client and are still to be observed by the user on port 25620.
The dev client (`runClient`) shares `run/mods` with the server, and ViaFabric's own client mixin on
the multiplayer server-list entry (`MixinOnlineServerEntry`) crashes that screen under the dev
client's MixinExtras, so the two Via jars are parked in `run/mods-for-bots/` for a client session
and moved back for the bot scripts, which refuse to run without them.

## S5 — One-way barrier — **done**

**Build.** `block/OneWayBarrierBlock` with `FACING` (`DirectionProperty`, six values) added
beside the inherited `WATERLOGGED` (`createBlockStateDefinition` calls super then adds). `FACING`
is the permitted travel direction; `getStateForPlacement` sets it from
`BlockPlaceContext.getNearestLookingDirection()` (`BlockPlaceContext.java:63`), so a player
looking east places a barrier they can walk through eastward; `/fill … one_way_barrier[facing=up]`
for bulk.

`admits(state, pos, mover)`: closed iff the mover's bounding box lies entirely on the exit side of
the block, with vanilla's touching epsilon: for a positive `facing` axis direction
`box.min(axis) >= pos.get(axis) + 1 - 1.0E-7`, for a negative one `box.max(axis) <= pos.get(axis)
+ 1.0E-7` (`VoxelShape.java:254-276`, `BlockCollisions.java:56-61` treat 1e-7 as contact; a
server-stopped box can read `0.9999999999999999` past the face, which without the epsilon would flip
the barrier open for an illegal re-entry); otherwise open (behind, inside, or beside — a row of
them forms a one-way plane). Bypass node `betterbarriers.bypass.oneway` opens it for a
`ServerPlayer` subject. The noclip check asks with the real entity after `player.move`
(`ServerGamePacketListenerImpl.java:1131,1153`; `CollisionGetter.java:90-92`,
`CollisionContext.java:52-60`): a legit exit leaves the cell as an edge cell that is skipped or
fails `intersects` (`BlockCollisions.java:93-100`), a legit entry sees an open shape, and a push
back in from the exit side meets a closed full cube the old box never touched, which vanilla
rejects. `handleMoveVehicle` is the same shape (:479-486).

Known soft spot, by design: a player who exits and reverses within one round trip is bounced back
once by the server before the solid state reaches the client.

**Done when.** Legs: (9) bot walks east through `facing=east`, then is blocked walking west back;
(10) with `betterbarriers.bypass.oneway` both directions pass; (11) a zombie summoned behind
crosses to the bait, one summoned in front does not; (12) an item summoned above a `facing=down`
barrier falls through, above `facing=up` it rests. `moved wrongly` count 0.

### As built

As planned, epsilon included. Legs 9-12 green: east through then stopped going back west (ends
x 1037.1 and 1031.3), both ways with the node, an arrow from behind crosses (x 1039.95) while one
from the front sticks (x 1031.05), an item falls through `facing=down` (y 101) and rests on
`facing=up` (y 106). The husk leg became an arrow leg for the reason given under S2.

## S6 — Game-mode barrier — **done**

**Build.** `block/GameModeBarrierBlock` with `MODE = EnumProperty.create("mode", GameType.class,
SURVIVAL, CREATIVE, ADVENTURE)` (`EnumProperty.java:84`; `GameType` is `StringRepresentable`,
`GameType.java:16`; spectators noclip, so no spectator mode). `admits` = subject is a
`ServerPlayer` with `gameMode() == mode` (`ServerPlayer.java:1786`) or holding
`betterbarriers.bypass.gamemode.<mode name>`.

Item component `betterbarriers:gamemode` (`GameType.CODEC`, `DataComponents.register` pattern,
`DataComponents.java:436-438`), default `creative` in the item's properties
(`Item.Properties.component`, `Item.java:661`); `BetterBarrierItem.getPlacementState` applies it to
`MODE`; the S4 sneak-use cycles it;
`/give @s betterbarriers:gamemode_barrier[betterbarriers:gamemode="adventure"]`.

**Done when.** Legs: (13) survival bot blocked at `mode=creative`, creative bot passes; (14)
`/gamemode creative` on the blocked bot while it stands against the barrier, it passes within 1 s;
(15) survival bot with `betterbarriers.bypass.gamemode.creative` passes; (16) a creative bot
standing on a `mode=survival` floor is set to survival and falls through.

### As built

As planned. Legs 13-16 green, including the switch while standing against it (open after 99 ms)
and the floor that drops a bot switched to survival (y 106.00 to 101.16).

## S7 — Keyed barrier — **done**

**Build.** `block/KeyedBarrierBlock` + `block/KeyedBarrierBlockEntity` (`EntityBlock`;
`FabricBlockEntityTypeBuilder`; `PolymerBlockUtils.registerBlockEntity(type)` so the entity is
stripped from chunk and data packets, `PolymerBlockUtils.java:88-92`). The entity holds
`@Nullable Holder<Item> key`, saved as `key: "<id>"` (`loadAdditional(ValueInput)` /
`saveAdditional(ValueOutput)`, `BlockEntity.java:96,108`), so `/setblock … keyed_barrier{key:
"minecraft:tripwire_hook"}` and `/fill` work. Item component `betterbarriers:key`
(`Item.CODEC`, which refuses air), carried into the entity by vanilla's
`BlockItem.place` → `applyComponentsFromItemStack` (`BlockItem.java:72,99-105`) via
`applyImplicitComponents`, back out for pick-block via `collectImplicitComponents` and
`removeComponentsFromTag` (`BlockEntity.java:288,321,325`;
`ServerGamePacketListenerImpl.java:720-728`).

`admits` = subject is a `ServerPlayer` and (`isHolding(key item)` — either hand,
`LivingEntity.java:2232` — or holds `betterbarriers.bypass.keyed.<ns>.<path>`, `/` in the path
mapped to `.`). No key → closed to all but bypass holders. Match is by item type only.

Setting the key in-world (creative): sneak-use a placed keyed barrier with an item → that item
becomes the key; sneak-use the keyed barrier *item* on nothing → the off-hand item becomes its key
(S4's cycle hook).

**Done when.** Legs: (17) bot holding the key passes, holding nothing is blocked; (18) the bot
scrolls the key off its hotbar while standing at the barrier and is blocked within 1 s, scrolls
back and passes; (19) bot with `betterbarriers.bypass.keyed.minecraft.stick` passes empty-handed;
(20) `/setblock` with `{key:…}` then `/data get block` shows it, and pick-block in-world yields an
item with the component. Keyless barrier blocks a bot with the key.

### As built

Block, block entity, item and component as planned; `/data get block` shows
`key: "minecraft:stick"`. Legs 17-20 green (open 101 ms / close 100 ms on a hotbar change; the
node passes empty-handed; a keyless barrier stops a key holder). Not built: setting the key by
sneak-using a *placed* keyed barrier, since a player the barrier admits sees air and cannot target
it, and an editor's main-hand barrier item would place instead; the item-side form (off-hand key,
sneak-use in the air) and `/data merge block` cover it. Pick-block with the component is an
in-world check still to be observed.

## S8 — Load test and As-built — **done**

**Build.** Extend `better-barriers.js` with a `--load N` mode: N bots (workers of 25, as
`orb-fence.js`) pacing a 64-block players-only wall and a keyed door, half holding the key. Profile
60 s with spark `--save-to-file`, decode with `SparkReport.java`; record self time of
`BarrierSync.tick` and the send hook, and packets sent per tick from a counter the reconcile keeps.
Write README (mod-specific: the five blocks, nodes, `/give` and `/setblock` forms, editing view,
the one-way reversal bounce) and the plan's As-built sections.

**Done when.** At 50 bots the reconcile and hook together stay under 1% of a 50 ms tick and under
2 block-update packets per bot per tick while pacing the wall; numbers in the plan. If the scan
shows up above that, the recorded fallback is a per-section position index built lazily from the
same `maybeHas` walk and dropped on `ServerChunkEvents.CHUNK_UNLOAD` — not built unless measured.

### As built

`scripts/loadtest/better-barriers-load.js`: N bots over worker processes, each holding a stick,
pacing across a 39-long players-only wall and a 39-long keyed wall (key: stick), with a spark
profile decoded by `SparkReport.java`. First run, 50 bots, 60 s, before tuning: `BarrierSync.tick`
0.356 ms per tick inclusive (6.6% of the 5.43 ms sampled on the server thread; server MSPT median
2.68 ms), `sent` hook 0.019 ms, `outgoing` below the sampler's floor. Profile-led tuning after
that: the four substitute states became constants (`substitute` had been 0.147 ms of it through
`setValue`), and the out-of-reach sweep no longer reads blocks for corrections inside the region
(`revertOutside` had been 0.143 ms), and the tail hook checks the packet type before looking the
viewer up (that lookup ran for every packet to every player: 0.18 ms per tick at 50 bots).

Final run, 50 bots pacing both walls for 60 s (978 crossings): `BarrierSync.tick` 0.373 ms per
tick inclusive, 6.6% of the 5.62 ms the sampler saw on the server thread, with the tail hook
and `outgoing` below the 1 ms sampler's floor; 0.98 block-update packets per bot per tick;
server MSPT median 2.67 ms, `tick query` average 2.52 ms; the server moved no bot back and
logged no `moved wrongly`. What remains of the reconcile is the packet sends themselves and the
palette-gated scan, so the per-section index stays unbuilt. README and `docs/BUILDING.md`
written.

## S9 — Barrier sprites — **in progress**

**Build.** The user's five 16×16 sprites go to `textures/item/<id>.png` (renamed to the registry
ids from `img/`), each with an item definition and an `item/generated` model, added to Polymer's
pack with `addModAssets` (not required: the live pack is already required by another mod, and a
client without it falls back to vanilla's barrier). `BetterBarrierItem` shows the sprite to pack
clients through `getPolymerItemModel`, on a `structure_void` base so vanilla's barrier markers,
which a client draws for a creative player holding a barrier or light item
(`ClientLevel.java:571-581`), do not double the sprites. In the editing view, `BarrierSprites`
keeps one Polymer `ElementHolder` per editor with a `ManualAttachment` at a fixed origin and one
`ItemDisplayElement` per barrier the reconcile scans, sent to that player alone, full-bright,
`FIXED` display context. An item display renders a flat item unrotated in that context (the
renderer's half turn, `DisplayRenderer.java:197`, cancels `item/generated`'s `fixed` 180°), so the
sprite's right edge faces east. Every type billboards to the camera except the one-way barrier:
horizontal facings use a fixed rotation about Y that turns the arrow onto `facing` with the broad
side to the placer's left and right, and up and down turn the arrow about Z and billboard about
the vertical.

**Done when.** A pack client editing within 16 blocks of a wall gets one item display per barrier,
a `facing=east` sprite arrives fixed and unrotated, a `facing=down` one vertical-billboarded with
a −90° turn about Z, and switching item removes them all; and in-world the arrows point the way a
one-way barrier lets you through, from both sides. The display effect is the requirement here.

### As built

Built as described. The server side is bot-verified: a pack client editing beside the walls gets
one item display per barrier in reach (379 at the dev rig, which still carries the load test's
walls), held on a `structure_void` base, and all are removed within a second of switching item.
A `facing=down` sprite arrives vertical-billboarded with rotation (0, 0, −0.707, 0.707); a
`facing=east` one arrives with no billboard or rotation data at all, because display entities only
send values that differ from the defaults, which are fixed and unrotated. A first version of that
leg read the sprites before the whole tick's batch arrived and before allowing for absent data;
it now waits for the two it inspects. Still to observe in-world: the sprites' look, the arrows'
direction from both sides, and the inventory icons.

## S10 — Operators held to the non-admin barriers — **done**

**Build.** `BarrierPermissions.ask` keeps op level 2 as the admin node's fallback and asks every
other node with a fallback of false, through a new kalic0re
`CommandPermissions.holds(player, node, boolean)` (`Permissions.check(Entity, String, boolean)`
under a provider, the fallback without one).

**Done when.** An op with no node set is stopped walking back through a one-way barrier, by a
`mode=creative` barrier in survival, and by a keyed barrier without the key, while still passing
an admin barrier.

### As built

As planned; all three op legs pass (an op is stopped walking back through a one-way barrier, by a
`mode=creative` barrier in survival, and by a keyless approach to a keyed barrier), and the op
still passes the admin barrier. This came out of the user's report that the one-way barrier did
not work: their session was an op in creative throughout, so the op fallback passed it both ways
and, while holding the item, the editing view made it solid both ways. Placement was already the
observer's: `ObserverBlock.getStateForPlacement` (`ObserverBlock.java:134`) takes the same
`getNearestLookingDirection()`.

---

## S11 — Editing view without solid barriers — **in progress**

**Build.** An editor with the pack now follows every barrier's real rule: `admits` no longer
forces a barrier closed for them. What changes is only the client state of an open barrier
within their 16-block reach, which becomes `light[level=0]` (keeping `waterlogged`) instead of
air or water. A light block has no collision, and a full-block outline whenever the client holds
a light item (`LightBlock.java:67`), so the barrier items are disguised for pack clients as
`minecraft:light` instead of `structure_void`, with `BLOCK_STATE` level 0 set in
`modifyBasePolymerItemStack` (Polymer strips the item's default level 15 first,
`PolymerItemUtils.java:416,437`) so a placement preview is never a level-15 light. Holding a light
item makes the client draw light markers (`ClientLevel.java:571-581`); the pack overrides
`minecraft:block/light_00` with a fully transparent particle texture, which the particle shader
discards (`particle.fsh`, alpha below 0.1), so stand-ins show only the sprites. Closed barriers
stay solid barriers. Without the pack nothing changes but the reach: open barriers stay air.

**Done when.** Holding a barrier item in creative, a bot sees an open barrier as a level-0 light,
passes a one-way barrier one way and is stopped the other, walks through a players-only wall, and
sees the stand-ins turn back to air when it switches item; in-world, every barrier can be targeted
and broken while editing, and no light marker shows over the sprites.

### As built

Built as described; the bot half of "Done when" is green, 35 of 35 legs. An editing bot sees an
open barrier as `light[level=0]`, passes a one-way barrier east and is stopped going back (seeing
it as a barrier from the exit side), walks through a players-only wall, and sees the stand-ins
turn back to air on switching item; its held item now arrives as `light`. The generated pack
carries the `light_00` override and the blank texture. This supersedes S4's "every barrier goes
solid for an editor" and S9's `structure_void` base item. Still to observe in-world: targeting and
breaking open barriers while editing, and that no light marker shows over the sprites.

## S12 — Pick-block settings, the gamemode rule, and lang markup — **in progress**

**Build.** From the user's report that a picked keyed barrier lost its key, and their rewritten
`en_us.json`. A plain middle-click asks `getCloneItemStack` with `includeData` false
(`ServerGamePacketListenerImpl.java:707-713`), and only a Ctrl-pick copies block-entity data, so
`KeyedBarrierBlock` puts the key on the clone itself and `GameModeBarrierBlock` puts its mode and
rule there. The lang file asks for an allow/block rule on gamemode barriers (block state `rule`,
item component `betterbarriers:gamemode_rule`, absent meaning allow; under block, players not in
the mode pass; non-players never do, under either rule), switched by a sneaking left-click in the
air. A client sends a left-click on a block or entity as a break or attack packet ahead of its
swing (`Minecraft.java:1659-1683`) and one in the air as a bare swing, so a swing mixin plus
Fabric's attack and use callbacks (`item/LeftClicks`) flip the rule only for a swing with no such
action in the last ten ticks and no block in reach along the look; ten ticks cover creative's
five-tick gap between breaks while the button is held. The strings carry colour tags, `{name}`
values, `{first/second}` choices and line breaks, which no client translation can render, so
`text/BarrierText` renders them server-side as literal components through a new kalic0re
`FallbackText.english`; an empty string renders nothing. Read as intended: `….set` is the item's
name once configured (the client flashes a changed name over the hotbar, `Hud.java:1195-1199`),
the gamemode tooltip's `%s` is the other rule from that choice, and `….set.empty` is the only
remaining action-bar message.

**Done when.** A plain pick of a keyed barrier gives the keyed item and of a gamemode barrier the
mode and rule; a `rule=block` barrier stops players in its mode and passes the rest; a sneaking
left-click in the air flips the rule and one on a block does not; a sneaking right-click in the
air still cycles the mode; and in-world, names and tooltips read as the lang file says.

### As built

Built as described; 41 of 41 legs pass, six of them new for this stage. A dump of the stacks a
client receives shows the lang file rendered as written: "Allow"/"Block" coloured in the names,
the gamemode name translated client-side from vanilla's own key, the gamemode tooltip on two grey
lines with the other rule coloured in, the keyed tooltip's yellow label, and no lore at all for an
empty string. One 26.2 change surfaced: `ChatFormatting` only looks up by legacy code now, so the
renderer matches constant names. Setting a key sends no message (the name does not change); that
reading of `keyed_barrier.set` is an assumption the user can overturn. Still to see in-world: the
names flashing over the hotbar on a change, and how the tooltips look.

## S13 — Sneaking left-clicks, creative tabs, plain lang — **in progress**

**Build.** The user found a sneaking right-click in the air flipped the rule as well as the mode.
Polymer turns a PolymerItem's client-swing success into a server swing
(`ServerGamePacketListenerImpMixin:197`, polymer-core), the server shows the player that swing,
and a vanilla client answers every swing it is shown of itself with a swing packet
(`ClientPacketListener.java:1110`, `LocalPlayer.java:325-328`), which arrived as a bare
left-click; bots never echo, so S12's legs missed it. `use()` now returns CONSUME (no swing), and
`LeftClicks` expects one echo per self-swing it sees leave on the send path, for the player's
latency plus ten ticks. A sneaking left-click with the gamemode item now flips the rule whatever
it targets: block and entity hits are refused through Fabric's attack callbacks (Fabric resends
the block, `ServerPlayerInteractionManagerMixin:59-73`), and a click counts once, on the first
event after two quiet ticks, since a held button reports a swing every tick. A swing right after
the player's own right-click still does not count. The lang file goes back to vanilla's format,
plain strings with `%s`, its colours applied in code, so no renderer is needed and pack clients
translate it themselves; `text/BarrierText` and kalic0re's `FallbackText.english` are gone. Tabs:
a Polymer tab with the gamemode barrier as icon, and the five items in vanilla's Operator
Utilities through Fabric's tab event, when operator items show.

**Done when.** With bots that echo shown swings: a sneaking right-click in the air changes only
the mode; a sneaking left-click flips the rule in the air, on a block (which stays) and on an
entity (which takes no damage); a held button flips once. In-world: the tab and its icon, the
Operator Utilities entries, and the restyled names and tooltips.

### As built

Built as described; 43 of 43 legs pass with bots that echo shown swings as vanilla does, which
reproduces the original bug's path. A sneaking right-click in the air changes only the mode; a
sneaking left-click flips the rule in the air, on a stone floor in creative (the stone stays), and
on a pig (health stays 10); a second of held button on a block flips once. The client-side dump
shows the names and tooltips as translatable components with English fallbacks, coloured in
code: green "Allow", red "Block", grey hints, a yellow "Key Item:" label with the key's own name
in white. Still to see in-world: the creative tab and its icon, and the Operator Utilities entries,
which no bot can see.

## S14 — 100-bot stress test, one wall each — **done**

**Build.** `better-barriers-load.js` rewritten: N bots in columns of 25 lanes three blocks apart,
each pacing through its own 3-wide, 4-high wall at x 1110 + 24 per column, z from 1100; wall types
rotate through all five; bots are survival operators holding a stick; a bot held at a closed wall
for a second turns round; bots echo shown swings like vanilla. Spark profile and tick samples as
in S8.

**Done when.** A 60 s run at 100 bots finishes with no bot moved back and no `moved wrongly`, and
its numbers are recorded here.

### As built

Run on a freshly started server, 100 bots, 60 s, one wall each; numbers from spark at 1 ms and
`tick query`:

| Measure | Value |
| --- | --- |
| `BarrierSync.tick`, inclusive | 0.62 ms per tick |
| send-path hooks (`sent`, with the packets they send) | about 0.29 ms per tick |
| server thread, sampled | 14.7 ms per tick |
| MSPT median / `tick query` mean | 5.50 / 5.21 ms |
| block updates per bot per tick | 0.86 |
| eastward wall crossings | 788 |
| bots moved back, `moved wrongly` lines | 0, 0 |

The one-way lanes count no crossings in the window: each bot passed once during the warm-up and
then paced its exit side, stopped at the closed face every time, as designed. A first run, on the
server that had just run the editing legs, showed 0.17 ms in `BarrierSprites.show`, which no bot in
the test reaches; the fresh run has none, so that was the JIT-inlined sprite path being credited
with the scan loop's samples. The send hooks now run for every packet to every player, about ten
thousand a tick with 100 bots in sight of each other; their cost is per packet, not per barrier.

## S15 — Configured items in the creative tabs — **in progress**

**Build.** The creative screen names, directly under an item's name, each tab holding a stack
identical to it (`CreativeModeInventoryScreen.getTooltipFromContainerItem`; `CreativeModeTab.contains`
is a type-and-components set), and Polymer's client copy embeds the whole server stack
(`$polymer:stack`, `PolymerItemUtils.java:472-480`), so a configured gamemode or keyed barrier
named no tabs where the plain items did. As vanilla lists each light level in Operator Utilities
(`CreativeModeTabs.java:1604`), both tabs list the gamemode barrier once per mode and rule
(`GameModeBarrierItem.variants`, six entries, allow first), and the item gains
`gamemode_rule=allow` as a default beside `gamemode=creative`: `PatchedDataComponentMap.set` drops
a value equal to the default, so a stack flipped back to allow or picked from an allow barrier
equals its entry. A keyed barrier with a key names no tabs, as a renamed vanilla item does; its key
can be any item. Writing the lines into lore instead, which a layout below the tooltip needs, was
built and dropped: it meant rewriting every copy sent to a creative player, resending on game-mode
change and telling tab entries apart through a polymer-core internal. Also found: Polymer syncs
vanilla category tabs' contents, our Operator Utilities additions included, to every Polymer client
(`PolymerCreativeModeTabUtils.java:69-79`), so the earlier "only on a client running this mod" was
wrong and is corrected in the README and Javadoc.

**Done when.** A gamemode item flipped back to allow and cycled, and one picked from an
allow/creative barrier, are each identical on the client to a fresh stack with the same settings,
and no item carries tab lines of its own. In-world: both tabs list six gamemode barriers, every
gamemode barrier held names both tabs under its name, and a keyed barrier with a key names none.

### As built

As planned; 45 of 45 legs pass. A gamemode item flipped to block and back, then cycled to
adventure, and one picked from an allow/creative barrier are each identical on the client to a
fresh stack with the same settings, and carry no tab lines of their own. A bot is never sent
creative tabs, so the legs compare client copies; the first version failed because `/give` merged
the equal fresh stack into the held one, so the fresh one now goes into its own hotbar slot. Still
to see in-world: the six entries in both tabs, the tab lines under each held gamemode barrier's
name, none on a keyed barrier with a key.

## S16 — Mounts, players-only game rules, air-only rule flip — **in progress**

**Build.** From the user's README rewrite. A mount or vehicle carrying a player goes as that player
on every barrier but players-only: the subject is the controlling passenger when a player, else
any player aboard, since a minecart has no controller (`Entity.getControllingPassenger`; `Mob`'s
returns only a mob, `Mob.java:221-224`). Players-only stops everything but players by default;
four boolean rules, `betterbarriers:players_only.allow.{mobs,items,projectiles,mounts}`, flat under
the mod id and registered straight into `BuiltInRegistries.GAME_RULE` (first built with kalic0re's
`ComponentGameRules` as `betterbarriers.players_only:allow.…`, switched before any deploy; the lang
key reads `gamerule.betterbarriers.players_only.allow.mobs` either way), each let one kind through: `Mob`, `ItemEntity`, `Projectile`, and any mover carrying a player. Gamemode
`rule=block` now passes every non-player (only players in its mode stop); `rule=allow` still stops
them. The sneak-left-click no longer refuses block and entity hits: they act as usual, and only a
click in the air flips the rule. A swing counts when it starts a click (more than two ticks after
the last) and no hit, use or item drop came within a tick before it; Fabric's attack and use
callbacks and a HEAD hook on `handlePlayerAction` record those; every branch of that method
returns early (`ServerGamePacketListenerImpl.java:1267-1320`), and the hook also covers the swing a
Q drop makes (`Minecraft.java:1920-1922`).

**Done when.** Legs: each of the four rules lets its kind through a players-only floor with the
rule on and not with it off (a cow at speed 0 for mobs, the same cow carrying the bot for mounts);
an item rests on a gamemode `rule=allow` floor and falls through `rule=block`; a sneaking hit on a
block breaks it and on a pig damages it, neither flipping the rule, nor does a held button or a Q
drop; a click in the air flips it both ways. In-world: riding a horse through each barrier type.

### As built

As planned, plus one mixin: an arrow decides it is stuck by asking the block's entity-less
collision shape (`AbstractArrow.java:181`), which a better barrier answers as solid, so with
`allow.projectiles` on an arrow still stopped 0.12 inside the floor. `AbstractArrowMixin` wraps that
one call to ask as the arrow for better barriers only. 53 of 53 legs pass, "moved wrongly" 0; a bot
joining as a vanilla 26.1 client is unaffected by the four new game-rule registry entries. Two
harness fixes: a block hit now really breaks the stone, so the leg hits the one beside the bot's
own (it fell through the platform first), and a Q drop sent as a raw packet is not predicted by
mineflayer, so the leg reads the server's count. Still to see in-world: riding a horse through
each barrier type, and the players-only rules from `/gamerule`.

Replaced after the 2026-10-12 review: telling a hit's swing from an air click by Fabric's attack
and use callbacks missed hits whenever an earlier listener (a claim mod's FAIL) stopped the event,
and adventure-mode block hits, which send no packet at all. A swing now counts only while the
player aims at nothing in reach, the server mirroring the client's own crosshair test
(`LocalPlayer.java:1260-1279`) with barriers solid for an editor shown stand-ins; the callbacks,
the click gap and the use record are gone, the Q-drop record stays. Subjects also look past
direct passengers (`getIndirectPassengers`), for a player on a mob in a minecart.

## S17 — Locks instead of key item types — **in progress**

**Build.** Matching an item type meant any stick opened a stick-keyed door, and names are no
better: an anvil writes a plain custom name (`AnvilMenu.java:289`). A keyed barrier now holds one
lock name, and a key is any item whose custom data lists it,
`custom_data={betterbarriers:{locks:["vault"]}}`, which survival play cannot write; a list, so one
key can open several locks. Names ignore case for A-Z only: one fold, A-Z to a-z and nothing else,
serves the block entity's and the item component's codec (stored folded), the walk-through match
and the off-hand step. `toLowerCase` and `equalsIgnoreCase` each also fold a few non-ASCII letters
(the long s, the dotted capital I) and disagree on which, which had made a key that opened a
barrier unusable from the off hand. Each hand's list is read in place through
`mixin/CustomDataAccessor`, since `CustomData` hands out only copies (`CustomData.java:61`) and its
`matchedBy` compares strings exactly (`CustomData.java:30`). Items survival can copy with their data never count, item
tag `betterbarriers:copyable_in_survival`: book cloning (`BookCloningRecipe.java:137`) and banner
copying (`BannerDuplicateRecipe.java:104`) copy the whole stack, the cartography table copies maps
(`CartographyTableMenu.java:152`), and signing a book and quill keeps its data
(`ServerGamePacketListenerImpl.java:1014`). Lock names, once lowercased, are `a-z 0-9 _ -` in
dot-separated parts, refused otherwise by that same codec, so
`betterbarriers.bypass.keyed.<lock>` is a well-formed node and `keyed.vault.*` covers
`vault.east`. Sneak-use with a key in the off hand sets the barrier item's lock, the next of the
key's locks on each use; an item that is no key, or a copyable one, is refused on the action bar.
Item-type keys, the `betterbarriers:key` component and the per-item nodes are gone.

**Done when.** Legs: a key to the lock passes and empty hands stop; a plain stick stops, a key to
two locks passes, a book and quill carrying the lock stops; the lock node and its `bbtest.*`
wildcard pass empty-handed; a lock-less barrier stops a key; `/data get block` shows the lock; a
name outside the set is refused on block and item; sneak-use cycles a two-lock key's locks and
refuses a book and a plain stick; pick-block keeps the lock.

### As built

As planned; 56 of 56 legs pass, "moved wrongly" 0, and a 10-bot load run crosses the lock-keyed
walls with a stick carrying the lock, no bot moved back. `/setblock` with an invalid lock name
reports "Could not set the block". The sneak-use legs first failed after one use: mineflayer never
sends the `tick_end` packet a 26.x client ends each tick with, and Polymer refuses every item use
after a Polymer item's until one arrives (`ServerGamePacketListenerImpMixin` `preventItemUse`,
reset in `handleClientTickEnd`). The harness now sends it (`lib/flyer.js` `endClientTicks`); the
trap is in `docs/TESTING.md`. Ignoring case came after, at the user's word: its first build threw
in the off-hand cycle (an immutable list's `indexOf(null)` for a lock-less item), which the server
logs as a suppressed packet error, so in game it looked like a refusal; a leg now fails on any
"Failed to handle packet" line in the run. Still to see in-world: setting a lock from a key in the
off hand.

## S18 — Rain through barriers — **in progress**

**Build.** A client draws rain and snow down to the top block its `MOTION_BLOCKING` height map
counts (`WeatherEffectRenderer.java:77`), places splashes and rain sound from the same map
(`ClientLevel.java:355`), and takes that map as the chunk packet carries it
(`LevelChunk.java:537`). The server's map counts every barrier, since the entity-less shape is a
full cube, so rain stopped on all five types: players-only too, though its client holds air there.
`mixin/ClientboundLevelChunkPacketDataMixin` hands each new chunk packet's `MOTION_BLOCKING` map
to `sync/BarrierHeightmaps`, which lowers every column topped by a barrier to the first counted
block below its barriers. The client reads `MOTION_BLOCKING_NO_LEAVES` only for F3 and the dragon
egg (`EnderDragonRenderer.java:222`), so that map keeps them. The server's own maps are untouched, so the
server still finds it dry under a barrier roof (cauldrons, fire, entities in rain). Vanilla
`minecraft:barrier` is left alone, so rooms kept dry with it stay dry.

The client updates its maps itself on each block change (`LevelChunk.java:286`,
`Heightmap.update`), where a barrier counts, so a barrier sent at or above a column's top raises
that column again until the chunk is resent: a correction to solid, a revert after an admitted
player walks off, a barrier placed. Particles are out of reach: they collide with the client's
blocks (`Particle.java:155`), the same ones it predicts the player's movement from.

**Done when.** In rain, under a roof of each type, rain falls through and splashes on the floor; a
vanilla barrier roof beside them stays dry; a player let through a roof (creative under the
gamemode roof, an op under the admin roof) who stands under it and walks off sees that patch stop
rain again, for them alone (the limit above), and relogging clears it.

### As built

As planned. A bot decoding the chunk packets' `MOTION_BLOCKING` over six test roofs read the
vanilla barrier roof as the top (y −56) and all five types as the floor (y −61), same as open
grass. In-world, rain falls through every type, including where the client holds a solid barrier:
weather follows the height map alone. Particles still stop on those, as expected.

The limit holds on a vanilla 26.2 client. In creative under the gamemode roof, F3 read `M: -61`.
Switching to survival there re-sent the barrier: `M` rose to -56 and rain stopped at the roof,
then went back to -61 in creative. A bot saw the same packets on walking off (25 air, then 25
barrier), yet walking off showed no patch on 26.2 or 26.3; cause not found. 26.3 counts a block
by tag, `minecraft:blocks_motion_in_heightmap` (barrier included), not `blocksMotion()`. A 26.2
server has no such tag; the dev ViaFabric's ViaVersion 5.12.1-SNAPSHOT adds none, the live proxy's
5.12.1 does. What a 26.3 client does without it is unchecked.

The limit is accepted: holding back reverts outside reach would add more weight than the
patches are worth. Rain stays visual only. The server still counts barriers, so beneath a roof
cauldrons stay empty, fire burns on and entities stay dry (`Level.java:957`,
`ServerLevel.java:578`). `forceSolidOff()` would change that but also 39 other `isSolid` and
`blocksMotion` checks (signs, item frames, farmland, enderman landing), so it is not used.

## Reference: verified signatures

- `BlockBehaviour.getCollisionShape(BlockState, BlockGetter, BlockPos, CollisionContext)` :327,
  `getShape` :323; `isCollisionShapeFullBlock` uses `CollisionContext.empty()` :336, :677, :921.
- `LevelChunk.setBlockState` :271 calls `onPlace` on every server-side state change unless flag
  512 (:326-327) and `affectNeighborsAfterRemoval` only on block change with flag 1 (:319-320).
  Not used by this design; recorded because the obvious index design would depend on it.
- `ServerChunkCache.getChunkNow(int, int)` :178; `LevelChunkSection.maybeHas` :195,
  `hasOnlyAir` :103; `GlobalPalette.maybeHas` is always true (`GlobalPalette.java:21`), so a
  256-plus-state section is scanned in full.
- `handleMovePlayer` threshold `movedDist > 0.0625` (squared) :1143; only that "moved wrongly"
  flag is skipped for creative and spectator, the noclip check `isEntityCollidingWithAnythingNew`
  :1235 still runs for creative (:1150-1153) and is short-circuited only by `noPhysics` or
  sleeping; vehicle equivalent :444-504.
- `BlockCollisions.computeNext`: cells the box overlaps are always evaluated through the real
  context (:96); only cells one past the ±1e-7 bounds consult the cached `hasLargeCollisionShape`
  (:93), so a per-entity closed shape is never skipped.
- Creative instabreak of a strength −1 Polymer block holds (`ServerPlayerGameMode.java:171-174,
  262-270`); survival progress stays 0 (`:196-199`), and Polymer's server-side miner adds the same
  0 (`ServerPlayerGameModeMixin.java:79-81`).
- `ClientboundBlockUpdatePacket(BlockPos, BlockState)` :24 and `(BlockGetter, BlockPos)` :29;
  `ClientboundSectionBlocksUpdatePacket.runUpdates(BiConsumer)` :72;
  `ClientboundLevelChunkWithLightPacket.getX()/getZ()` :58,62;
  `ClientboundForgetLevelChunkPacket(ChunkPos pos)` record.
- `ClientboundLevelChunkPacketData(LevelChunk)` :36 fills `heightmaps` (`Map<Heightmap.Types,
  long[]>`, private final) with clones of the server's raw data (:40);
  `SimpleBitStorage(int, int, long[])` :244 wraps the array it is given; `Heightmap` packs 256
  columns at `Mth.ceillog2(height + 1)` bits, index `x + z * 16`, value `y - minY` (:39, :122, :142).
- Polymer: `PacketContext` is Fabric's, no `getPlayer()`; `PolymerCommonUtils.getPlayer(ctx)` is
  nullable (`PolymerCommonUtils.java:207`). `PolymerItemUtils.createItemStack` sets the client
  `ITEM_NAME` from the server stack's component (`:455-456`), so a translatable-with-fallback name
  renders on a vanilla client.
- kalic0re 0.25.0: `CommandPermissions.holds(ServerPlayer, String, PermissionLevel)`,
  `FallbackText.load(String modId)` / `of(String key, Object...)`,
  `ItemRegistration.register(String ns, String path, Function<ResourceKey<Item>, T>)`.
- Fabric API 0.157.0: `ServerPlayerEvents.AFTER_RESPAWN`,
  `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL`, `ServerTickEvents.END_SERVER_TICK`,
  `ServerPlayConnectionEvents.DISCONNECT`; no game-mode event.
