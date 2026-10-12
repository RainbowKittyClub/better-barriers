package club.rainbowkitty.betterbarriers.mixin;

import io.netty.channel.ChannelFutureListener;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import club.rainbowkitty.betterbarriers.item.LeftClicks;
import club.rainbowkitty.betterbarriers.sync.BarrierSync;

// Every block, section and chunk packet to a player passes through send here; BarrierSync keeps
// that player's barrier corrections in place through them, and LeftClicks hears their own swings.
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class ServerCommonPacketListenerImplMixin {
    @Shadow
    @Final
    protected MinecraftServer server;

    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;"
            + "Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), argsOnly = true)
    private Packet<?> betterbarriers$keepCorrections(Packet<?> packet) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game
                && this.server.isSameThread()) {
            return BarrierSync.outgoing(game.player, packet);
        }
        return packet;
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;"
            + "Lio/netty/channel/ChannelFutureListener;)V", at = @At("TAIL"))
    private void betterbarriers$followCorrections(Packet<?> packet,
            @Nullable ChannelFutureListener listener, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game
                && this.server.isSameThread()) {
            BarrierSync.sent(game.player, packet);
            LeftClicks.sent(game.player, packet);
        }
    }
}
