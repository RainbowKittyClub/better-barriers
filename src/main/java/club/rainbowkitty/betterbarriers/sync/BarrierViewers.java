package club.rainbowkitty.betterbarriers.sync;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;

/** The viewers of every online player, by UUID, so a respawn keeps the same one. */
public final class BarrierViewers {
    private static final Map<UUID, BarrierViewer> VIEWERS = new HashMap<>();

    private BarrierViewers() {
    }

    /** The viewer for {@code player}, made on first use. */
    public static BarrierViewer of(ServerPlayer player) {
        return VIEWERS.computeIfAbsent(player.getUUID(), id -> new BarrierViewer());
    }

    /** {@code player}'s viewer, or null if they have none. */
    public static @Nullable BarrierViewer peek(ServerPlayer player) {
        return VIEWERS.get(player.getUUID());
    }

    /** Drops a disconnected player's viewer. */
    public static void forget(UUID id) {
        BarrierViewer viewer = VIEWERS.remove(id);
        if (viewer != null) {
            viewer.closeSprites();
        }
    }

    /** Forgets a player's corrections after a respawn or level change, which resend chunks. */
    public static void reset(ServerPlayer player) {
        BarrierViewer viewer = VIEWERS.get(player.getUUID());
        if (viewer != null) {
            viewer.reset(player.level());
        }
    }
}
