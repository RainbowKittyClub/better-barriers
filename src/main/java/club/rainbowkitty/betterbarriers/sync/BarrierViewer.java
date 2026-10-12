package club.rainbowkitty.betterbarriers.sync;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.betterbarriers.BarrierPermissions;
import club.rainbowkitty.betterbarriers.block.BetterBarrierBlock;

/**
 * One player's side of the barriers: which positions their client holds a correction at, and
 * their permission answers, asked at most once a second per node so collision never waits on the
 * permission provider.
 */
public final class BarrierViewer {
    private static final int PERMISSION_TTL_TICKS = 20;

    final Long2ObjectMap<Correction> corrections = new Long2ObjectOpenHashMap<>();
    // 1 held, 0 not; -1, the default, not asked since the last refresh.
    private final Object2ByteOpenHashMap<String> nodes = new Object2ByteOpenHashMap<>();
    // Starts due, without an overflow in the subtraction below on the first ask.
    private int nodesAskedTick = -PERMISSION_TTL_TICKS;
    private @Nullable ServerLevel level;
    private @Nullable BarrierSprites sprites;

    BarrierViewer() {
        this.nodes.defaultReturnValue((byte) -1);
    }

    /** Whether {@code player} holds {@code node}, as {@link BarrierPermissions#ask} answers. */
    public boolean holds(ServerPlayer player, String node) {
        int tick = player.level().getServer().getTickCount();
        if (tick - this.nodesAskedTick >= PERMISSION_TTL_TICKS) {
            this.nodes.clear();
            this.nodesAskedTick = tick;
        }
        byte held = this.nodes.getByte(node);
        if (held < 0) {
            held = BarrierPermissions.ask(player, node) ? (byte) 1 : (byte) 0;
            this.nodes.put(node, held);
        }
        return held == 1;
    }

    // The state this client holds at `pos` for a block whose real state is `state`.
    BlockState clientState(long pos, BlockState state, BetterBarrierBlock block) {
        Correction correction = this.corrections.get(pos);
        return correction != null && correction.evaluated() == state
                ? correction.sent() : block.defaultSubstitute(state);
    }

    // A client drops every chunk on leaving a level, corrections included; nothing to send back.
    void enterLevel(ServerLevel level) {
        if (this.level != level) {
            reset(level);
        }
    }

    // Forgets every correction and sprite, as the client has, for a player now in `level`.
    void reset(ServerLevel level) {
        this.corrections.clear();
        closeSprites();
        this.level = level;
    }

    // The sprites for an editor whose client has the pack, made on first use; null, with any old
    // ones taken down, for everyone else.
    @Nullable BarrierSprites sprites(ServerPlayer player, boolean show) {
        if (this.sprites != null && !show) {
            closeSprites();
        }
        if (show && this.sprites == null) {
            this.sprites = new BarrierSprites(player);
        }
        return this.sprites;
    }

    void closeSprites() {
        if (this.sprites != null) {
            this.sprites.close();
            this.sprites = null;
        }
    }
}
