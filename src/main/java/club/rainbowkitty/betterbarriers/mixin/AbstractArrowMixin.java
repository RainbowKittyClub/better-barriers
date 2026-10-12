package club.rainbowkitty.betterbarriers.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import club.rainbowkitty.betterbarriers.block.BetterBarrierBlock;

// An arrow asks whether it sits inside a block without saying who asks, which a better barrier
// answers as solid; asked as the arrow, one the barrier lets through stays in flight.
@Mixin(AbstractArrow.class)
abstract class AbstractArrowMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape("
                    + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)"
                    + "Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape betterbarriers$askAsArrow(BlockState state, BlockGetter level, BlockPos pos,
            Operation<VoxelShape> original) {
        return state.getBlock() instanceof BetterBarrierBlock
                ? state.getCollisionShape(level, pos, CollisionContext.of((Entity) (Object) this))
                : original.call(state, level, pos);
    }
}
