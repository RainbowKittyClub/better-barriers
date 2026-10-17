package club.rainbowkitty.betterbarriers.sync;

import java.util.function.Predicate;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import club.rainbowkitty.betterbarriers.block.BetterBarrierBlock;
import club.rainbowkitty.betterbarriers.item.BetterBarrierItem;

/**
 * Keeps each player's client in step with what the server will let them through. Chunk packets
 * carry every barrier's default state; each tick this scans the blocks within reach of a player,
 * sends air or barrier where the default is wrong for them, and remembers what it sent. Barriers
 * are invisible, so nothing further away needs to be right until they get there.
 *
 * <p>Vanilla and Polymer resend real block states freely, each of which Polymer maps back to the
 * default; the two hooks below, called from {@code ServerCommonPacketListenerImpl.send}, keep
 * a correction in place through those resends and drop it only when the block itself changed.
 */
public final class BarrierSync {
    private static final double BASE_REACH = 3.0;
    private static final double MAX_REACH = 12.0;
    private static final double EDIT_REACH = 16.0;
    private static final double LOOKAHEAD_TICKS = 4.0;
    static final Predicate<BlockState> IS_BARRIER =
            state -> state.getBlock() instanceof BetterBarrierBlock;
    // Set while this class sends its own corrections, which the send hooks then leave alone.
    private static boolean correcting;

    private BarrierSync() {
    }

    /** Wires the per-tick reconcile and the player lifecycle. */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BarrierSync::tick);
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> BarrierViewers.forget(handler.player.getUUID()));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
                (player, origin, destination) -> BarrierViewers.reset(player));
        ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> BarrierViewers.reset(newPlayer));
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // A spectator collides with nothing; their corrections wait for them to come back.
            if (player.isAlive() && !player.isSpectator()) {
                reconcile(player);
            } else {
                BarrierViewer viewer = BarrierViewers.peek(player);
                if (viewer != null) {
                    viewer.closeSprites();
                }
            }
        }
    }

    private static void reconcile(ServerPlayer player) {
        ServerLevel level = player.level();
        BarrierViewer viewer = BarrierViewers.of(player);
        viewer.enterLevel(level);
        boolean editing = BetterBarrierItem.isEditing(player);
        boolean standIn = showsStandIns(player);
        BarrierSprites sprites = viewer.sprites(player, standIn);
        Entity mover = player.getRootVehicle();
        AABB region = mover.getBoundingBox().inflate(reach(player, mover, editing));
        int minX = Mth.floor(region.minX);
        int maxX = Mth.floor(region.maxX);
        int minY = Math.max(level.getMinY(), Mth.floor(region.minY));
        int maxY = Math.min(level.getMaxY(), Mth.floor(region.maxY));
        int minZ = Mth.floor(region.minZ);
        int maxZ = Mth.floor(region.maxZ);
        ServerChunkCache chunks = level.getChunkSource();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                LevelChunk chunk = chunks.getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                int x0 = Math.max(minX, chunkX << 4);
                int x1 = Math.min(maxX, (chunkX << 4) + 15);
                int z0 = Math.max(minZ, chunkZ << 4);
                int z1 = Math.min(maxZ, (chunkZ << 4) + 15);
                for (int index = chunk.getSectionIndex(minY);
                        index <= chunk.getSectionIndex(maxY); index++) {
                    LevelChunkSection section = chunk.getSection(index);
                    if (!mayHoldBarriers(section)) {
                        continue;
                    }
                    int sectionY = chunk.getSectionYFromSectionIndex(index) << 4;
                    int y0 = Math.max(minY, sectionY);
                    int y1 = Math.min(maxY, sectionY + 15);
                    // Storage order, y then z then x, so the reads run along the section's array.
                    for (int y = y0; y <= y1; y++) {
                        for (int z = z0; z <= z1; z++) {
                            for (int x = x0; x <= x1; x++) {
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                if (state.getBlock() instanceof BetterBarrierBlock block) {
                                    correct(player, viewer, block, state, pos.set(x, y, z), mover,
                                            standIn);
                                    if (sprites != null) {
                                        sprites.show(pos, state, block);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        revertOutside(player, viewer, minX, minY, minZ, maxX, maxY, maxZ);
        if (sprites != null) {
            sprites.endTick();
        }
    }

    // Sends `player` the state they should hold at `pos`, if it is not what they hold already.
    private static void correct(ServerPlayer player, BarrierViewer viewer, BetterBarrierBlock block,
            BlockState state, BlockPos pos, Entity mover, boolean standIn) {
        long key = pos.asLong();
        BlockState want = block.substitute(state, block.admits(state, pos, mover), standIn);
        if (want == viewer.clientState(key, state, block)) {
            return;
        }
        send(player, pos.immutable(), want);
        if (want == block.defaultSubstitute(state)) {
            viewer.corrections.remove(key);
        } else {
            viewer.corrections.put(key, new Correction(state, want));
        }
    }

    // Corrections out of the region go back to the default. Those inside it the scan just
    // handled, and a block that changed under one lost its correction in the send hooks already.
    private static void revertOutside(ServerPlayer player, BarrierViewer viewer, int minX, int minY,
            int minZ, int maxX, int maxY, int maxZ) {
        if (viewer.corrections.isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        ObjectIterator<Long2ObjectMap.Entry<Correction>> entries =
                Long2ObjectMaps.fastIterator(viewer.corrections);
        while (entries.hasNext()) {
            Long2ObjectMap.Entry<Correction> entry = entries.next();
            long key = entry.getLongKey();
            int x = BlockPos.getX(key);
            int y = BlockPos.getY(key);
            int z = BlockPos.getZ(key);
            if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                continue;
            }
            BlockPos pos = BlockPos.of(key);
            BlockState real = level.hasChunkAt(pos) ? level.getBlockState(pos) : null;
            if (real != null && real == entry.getValue().evaluated()
                    && real.getBlock() instanceof BetterBarrierBlock block) {
                send(player, pos, block.defaultSubstitute(real));
            }
            entries.remove();
        }
    }

    /** The state {@code player}'s client holds at {@code pos}: its correction, else the default. */
    public static BlockState clientState(ServerPlayer player, BlockPos pos, BlockState state,
            BetterBarrierBlock block) {
        BarrierViewer viewer = BarrierViewers.peek(player);
        return viewer != null ? viewer.clientState(pos.asLong(), state, block)
                : block.defaultSubstitute(state);
    }

    /**
     * Whether {@code player}'s client is shown open barriers as targetable light stand-ins: an
     * editor with the resource pack. Every other client is shown them as air.
     */
    public static boolean showsStandIns(ServerPlayer player) {
        return BetterBarrierItem.isEditing(player)
                && PolymerResourcePackUtils.hasMainPack(player.connection);
    }

    // How far ahead of the player the client state must already be right: their speed over a few
    // ticks plus their latency. An editor wants to see them all.
    private static double reach(ServerPlayer player, Entity mover, boolean editing) {
        if (editing) {
            return EDIT_REACH;
        }
        double lookaheadTicks = LOOKAHEAD_TICKS
                + player.connection.latency() / (double) SharedConstants.MILLIS_PER_TICK;
        return Math.min(MAX_REACH, BASE_REACH + mover.getKnownMovement().length() * lookaheadTicks);
    }

    private static void send(ServerPlayer player, BlockPos pos, BlockState state) {
        correcting = true;
        try {
            player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
        } finally {
            correcting = false;
        }
    }

    /**
     * The packet to send {@code player} in place of {@code packet}: a resend of a corrected
     * position's unchanged real state becomes the correction again; a changed state passes and
     * ends the correction. Server thread only.
     */
    public static Packet<?> outgoing(ServerPlayer player, Packet<?> packet) {
        if (correcting || !(packet instanceof ClientboundBlockUpdatePacket update)) {
            return packet;
        }
        BarrierViewer viewer = BarrierViewers.peek(player);
        if (viewer == null) {
            return packet;
        }
        long key = update.getPos().asLong();
        Correction correction = viewer.corrections.get(key);
        if (correction == null) {
            return packet;
        }
        if (correction.evaluated() == update.getBlockState()) {
            return new ClientboundBlockUpdatePacket(update.getPos(), correction.sent());
        }
        viewer.corrections.remove(key);
        return packet;
    }

    /**
     * After {@code packet} was queued for {@code player}: a section or chunk packet that just
     * carried defaults over corrections gets those corrections sent right behind it, in the same
     * batch, or dropped where the block changed. Server thread only.
     */
    public static void sent(ServerPlayer player, Packet<?> packet) {
        // Type first: this runs for every packet to every player, the viewer lookup only for three.
        if (!(packet instanceof ClientboundSectionBlocksUpdatePacket
                || packet instanceof ClientboundLevelChunkWithLightPacket
                || packet instanceof ClientboundForgetLevelChunkPacket)) {
            return;
        }
        BarrierViewer viewer = BarrierViewers.peek(player);
        if (viewer == null || viewer.corrections.isEmpty()) {
            return;
        }
        if (packet instanceof ClientboundSectionBlocksUpdatePacket section) {
            section.runUpdates((pos, state) -> follow(player, viewer, pos, state));
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            resendChunk(player, viewer, chunk.getX(), chunk.getZ());
        } else if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
            dropChunk(viewer, forget.pos().x(), forget.pos().z());
        }
    }

    private static void follow(ServerPlayer player, BarrierViewer viewer, BlockPos pos,
            BlockState state) {
        long key = pos.asLong();
        Correction correction = viewer.corrections.get(key);
        if (correction == null) {
            return;
        }
        if (correction.evaluated() == state) {
            send(player, pos.immutable(), correction.sent());
        } else {
            viewer.corrections.remove(key);
        }
    }

    // The chunk arrived with defaults: each correction in it follows right behind, or is dropped
    // where the block changed, so the client never acts on the default.
    private static void resendChunk(ServerPlayer player, BarrierViewer viewer, int chunkX,
            int chunkZ) {
        // Keys, not entries: an open-hash entry reads its slot, which a removal can refill.
        LongArrayList inChunk = new LongArrayList();
        for (LongIterator keys = viewer.corrections.keySet().iterator(); keys.hasNext();) {
            long key = keys.nextLong();
            if (inChunk(key, chunkX, chunkZ)) {
                inChunk.add(key);
            }
        }
        ServerLevel level = player.level();
        for (int i = 0; i < inChunk.size(); i++) {
            BlockPos pos = BlockPos.of(inChunk.getLong(i));
            follow(player, viewer, pos, level.getBlockState(pos));
        }
    }

    private static void dropChunk(BarrierViewer viewer, int chunkX, int chunkZ) {
        viewer.corrections.long2ObjectEntrySet()
                .removeIf(entry -> inChunk(entry.getLongKey(), chunkX, chunkZ));
    }

    private static boolean inChunk(long pos, int chunkX, int chunkZ) {
        return BlockPos.getX(pos) >> 4 == chunkX && BlockPos.getZ(pos) >> 4 == chunkZ;
    }

    // A palette check, so a section of 256-plus states always may.
    static boolean mayHoldBarriers(LevelChunkSection section) {
        return !section.hasOnlyAir() && section.maybeHas(IS_BARRIER);
    }
}
