package club.rainbowkitty.betterbarriers.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import club.rainbowkitty.betterbarriers.item.LeftClicks;

// A left-click in the air reaches the server only as a swing, and an item drop swings too; no
// Fabric event reports either.
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    // TAIL runs on the server thread only: the netty-thread pass stops at the thread check.
    @Inject(method = "handleAnimate", at = @At("TAIL"))
    private void betterbarriers$noteSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
        LeftClicks.swung(this.player, packet.getHand());
    }

    // HEAD, since every action returns early; the netty-thread pass is skipped by hand.
    @Inject(method = "handlePlayerAction", at = @At("HEAD"))
    private void betterbarriers$noteDrop(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        ServerboundPlayerActionPacket.Action action = packet.getAction();
        if ((action == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)
                && this.player.level().getServer().isSameThread()) {
            LeftClicks.dropped(this.player);
        }
    }
}
