package club.rainbowkitty.betterbarriers.block;

//? if <26.3
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
//? if <26.3
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.betterbarriers.BarrierPermissions;

/** A barrier only a player holding {@code betterbarriers.bypass.admin} walks through. */
public final class AdminBarrierBlock extends BetterBarrierBlock {
    //? if <26.3
    public static final MapCodec<BarrierBlock> CODEC = simpleCodec(AdminBarrierBlock::new);

    public AdminBarrierBlock(Properties properties) {
        super(properties);
    }

    //? if <26.3 {
    @Override
    public MapCodec<BarrierBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected boolean passes(BlockState state, BlockPos pos, Entity mover, Entity subject) {
        return BarrierPermissions.bypass(subject, BarrierPermissions.ADMIN);
    }
}
