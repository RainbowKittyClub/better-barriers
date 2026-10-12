package club.rainbowkitty.betterbarriers.item;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * A sneaking left-click with a gamemode barrier item flips its rule, whatever it hits: a block or
 * entity hit is refused (nothing breaks, nothing takes damage), and a click in the air reaches the
 * server only as a swing. A held button keeps sending swings and repeat hits, so only the first
 * event after a gap counts. Two kinds of swing are not left-clicks: the one a client makes after
 * its own successful right-click, and the echo a vanilla client sends back for every swing the
 * server shows it of itself (Polymer turns item successes into those).
 */
public final class LeftClicks {
    // A held button reports something every tick; a longer silence ends the click.
    private static final int CLICK_GAP_TICKS = 2;
    // How long past the player's latency an echo stays expected.
    private static final int ECHO_GRACE_TICKS = 10;
    private static final Map<UUID, Integer> LAST_LEFT = new HashMap<>();
    private static final Map<UUID, Integer> LAST_USE = new HashMap<>();
    private static final Map<UUID, ArrayDeque<Integer>> ECHOES = new HashMap<>();

    private LeftClicks() {
    }

    /** Hooks the hits, the right-clicks that explain a swing, and disconnects. */
    public static void register() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, face) -> hit(player));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, at) -> hit(player));
        UseBlockCallback.EVENT.register((player, level, hand, at) -> used(player));
        UseEntityCallback.EVENT.register((player, level, hand, entity, at) -> used(player));
        UseItemCallback.EVENT.register((player, level, hand) -> used(player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUUID();
            LAST_LEFT.remove(id);
            LAST_USE.remove(id);
            ECHOES.remove(id);
        });
    }

    /** A swing packet from {@code player}, on the server thread. */
    public static void swung(ServerPlayer player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || consumeEcho(player)
                || !holdsWhileSneaking(player)) {
            return;
        }
        Integer use = LAST_USE.get(player.getUUID());
        if (use == null || tick(player) - use > 1) {
            click(player);
        }
    }

    /** After {@code packet} was queued for {@code player}: their own swing, which they echo. */
    public static void sent(ServerPlayer player, Packet<?> packet) {
        if (packet instanceof ClientboundAnimatePacket animate
                && animate.getAction() == ClientboundAnimatePacket.SWING_MAIN_HAND
                && animate.getId() == player.getId()) {
            expectEcho(player);
        }
    }

    private static void expectEcho(ServerPlayer player) {
        int deadline = tick(player) + ECHO_GRACE_TICKS + (int) Math.ceil(
                player.connection.latency() / (double) SharedConstants.MILLIS_PER_TICK);
        ECHOES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>()).addLast(deadline);
    }

    private static InteractionResult hit(Player player) {
        if (player instanceof ServerPlayer serverPlayer && holdsWhileSneaking(serverPlayer)) {
            click(serverPlayer);
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult used(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            LAST_USE.put(player.getUUID(), tick(serverPlayer));
        }
        return InteractionResult.PASS;
    }

    // Flips the rule on the first event of a click; every event keeps the click going.
    private static void click(ServerPlayer player) {
        int now = tick(player);
        Integer last = LAST_LEFT.put(player.getUUID(), now);
        if (last == null || now - last > CLICK_GAP_TICKS) {
            GameModeBarrierItem.flipRule(player.getMainHandItem());
        }
    }

    private static boolean consumeEcho(ServerPlayer player) {
        ArrayDeque<Integer> echoes = ECHOES.get(player.getUUID());
        if (echoes == null) {
            return false;
        }
        int now = tick(player);
        while (!echoes.isEmpty() && echoes.peekFirst() < now) {
            echoes.removeFirst();
        }
        return echoes.pollFirst() != null;
    }

    private static boolean holdsWhileSneaking(ServerPlayer player) {
        return player.isShiftKeyDown()
                && player.getMainHandItem().getItem() instanceof GameModeBarrierItem;
    }

    private static int tick(ServerPlayer player) {
        return player.level().getServer().getTickCount();
    }
}
