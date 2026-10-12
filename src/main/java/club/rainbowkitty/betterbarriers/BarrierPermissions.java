package club.rainbowkitty.betterbarriers;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;

import club.rainbowkitty.betterbarriers.sync.BarrierViewer;
import club.rainbowkitty.betterbarriers.sync.BarrierViewers;
import club.rainbowkitty.kalic0re.common.command.CommandPermissions;

/**
 * The bypass nodes, {@code betterbarriers.bypass.<what>}. Operators pass admin barriers unless
 * denied; every other barrier holds them to its rule until the node is granted.
 */
public final class BarrierPermissions {
    private static final String ROOT = BetterBarriers.MOD_ID + ".bypass.";
    public static final String ADMIN = ROOT + "admin";
    public static final String ONE_WAY = ROOT + "oneway";
    private static final Map<GameType, String> GAMEMODE = new EnumMap<>(GameType.class);
    private static final Map<Item, String> KEYED = new HashMap<>();

    static {
        for (GameType mode : GameType.values()) {
            GAMEMODE.put(mode, ROOT + "gamemode." + mode.getName());
        }
    }

    private BarrierPermissions() {
    }

    /** The node that passes a gamemode barrier of {@code mode}. */
    public static String gamemode(GameType mode) {
        return GAMEMODE.get(mode);
    }

    /** The node that passes a keyed barrier whose key is {@code item}: its id with dots. */
    public static String keyed(Holder<Item> item) {
        return KEYED.computeIfAbsent(item.value(), value -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(value);
            return ROOT + "keyed." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        });
    }

    /**
     * Asks the permission provider whether {@code player} holds {@code node}: op level 2 when the
     * admin node is undefined, false for any other undefined node. Uncached; see {@link #bypass}.
     */
    public static boolean ask(ServerPlayer player, String node) {
        return ADMIN.equals(node)
                ? CommandPermissions.holds(player, node, PermissionLevel.GAMEMASTERS)
                : CommandPermissions.holds(player, node, false);
    }

    /** Whether {@code subject} is a player holding {@code node}; cached per player for a second. */
    public static boolean bypass(Entity subject, String node) {
        if (!(subject instanceof ServerPlayer player)) {
            return false;
        }
        // Uncached without a viewer: one made here, say mid-disconnect, nothing would remove.
        BarrierViewer viewer = BarrierViewers.peek(player);
        return viewer != null ? viewer.holds(player, node) : ask(player, node);
    }
}
