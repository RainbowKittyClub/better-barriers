package club.rainbowkitty.betterbarriers.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import club.rainbowkitty.betterbarriers.sync.BarrierHeightmaps;

// Every chunk packet copies the server's height maps here, barriers counted; the client keeps them.
@Mixin(ClientboundLevelChunkPacketData.class)
abstract class ClientboundLevelChunkPacketDataMixin {
    @Shadow
    @Final
    private Map<Heightmap.Types, long[]> heightmaps;

    @Inject(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("RETURN"))
    private void betterbarriers$skipBarriers(LevelChunk chunk, CallbackInfo ci) {
        BarrierHeightmaps.skipBarriers(chunk, this.heightmaps);
    }
}
