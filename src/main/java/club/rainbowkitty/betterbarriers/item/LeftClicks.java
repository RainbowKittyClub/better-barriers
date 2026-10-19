package club.rainbowkitty.betterbarriers.item;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.Packet;
//? if <26.3
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import club.rainbowkitty.betterbarriers.sync.BarrierSync;

/**
 * A sneaking left-click in the air with a gamemode barrier item flips its rule. It reaches the
 * server only as a swing, which a hit on a block or entity sends too, so a swing counts only while
 * the player aims at nothing they could hit. Two more swings are not left-clicks: the one after an
 * item drop, and the echo a vanilla client sends back for every swing the server shows it of
 * itself. Hits act as usual.
 */
public final class LeftClicks {
    // How long past the player's latency an echo stays expected.
    private static final int ECHO_GRACE_TICKS = 10;
    private static final Map<UUID, Integer> LAST_DROP = new HashMap<>();
    private static final Map<UUID, ArrayDeque<Integer>> ECHOES = new HashMap<>();

    private LeftClicks() {
    }

    /** Forgets a player on disconnect. */
    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUUID();
            LAST_DROP.remove(id);
            ECHOES.remove(id);
        });
    }

    /** A swing packet from {@code player}, on the server thread. */
    public static void swung(ServerPlayer player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || consumeEcho(player) || !player.isShiftKeyDown()
                || !(player.getMainHandItem().getItem() instanceof GameModeBarrierItem)) {
            return;
        }
        Integer drop = LAST_DROP.get(player.getUUID());
        if ((drop == null || tick(player) - drop > 1) && aimsAtNothing(player)) {
            GameModeBarrierItem.flipRule(player.getMainHandItem());
        }
    }

    /** An item drop by {@code player}, whose client swings after it. Server thread. */
    public static void dropped(ServerPlayer player) {
        LAST_DROP.put(player.getUUID(), tick(player));
    }

    /** After {@code packet} was queued for {@code player}: their own swing, which they echo. */
    public static void sent(ServerPlayer player, Packet<?> packet) {
        // A 26.3 client echoes no swing.
        //? if <26.3 {
        if (packet instanceof ClientboundAnimatePacket animate
                && animate.getAction() == ClientboundAnimatePacket.SWING_MAIN_HAND
                && animate.getId() == player.getId()) {
            expectEcho(player);
        }
        //?}
    }

    // The client's own crosshair test (LocalPlayer.pick): no block within block reach and no
    // pickable entity within entity reach. An editor's client shows every barrier as something to
    // aim at; anyone else's shows the open ones as air, as this player's own collision does.
    private static boolean aimsAtNothing(ServerPlayer player) {
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double blockReach = player.blockInteractionRange();
        CollisionContext context = BarrierSync.showsStandIns(player)
                ? CollisionContext.empty() : CollisionContext.of(player);
        Vec3 blockEnd = from.add(look.scale(blockReach));
        HitResult block = player.level().clip(new ClipContext(from, blockEnd,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, context));
        if (block.getType() != HitResult.Type.MISS) {
            return false;
        }
        double entityReach = player.entityInteractionRange();
        AABB around = player.getBoundingBox().expandTowards(look.scale(entityReach)).inflate(1.0);
        return ProjectileUtil.getEntityHitResult(player, from, from.add(look.scale(entityReach)),
                around, EntitySelector.CAN_BE_PICKED, entityReach * entityReach) == null;
    }

    private static void expectEcho(ServerPlayer player) {
        int deadline = tick(player) + ECHO_GRACE_TICKS + (int) Math.ceil(
                player.connection.latency() / (double) SharedConstants.MILLIS_PER_TICK);
        ECHOES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>()).addLast(deadline);
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

    private static int tick(ServerPlayer player) {
        return player.level().getServer().getTickCount();
    }
}
