package club.rainbowkitty.betterbarriers.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A barrier every player walks through and nothing else does. */
public final class PlayersOnlyBarrierBlock extends BetterBarrierBlock {
    // Typed as the superclass's, which its public codec() fixes; it still constructs this class.
    public static final MapCodec<BarrierBlock> CODEC = simpleCodec(PlayersOnlyBarrierBlock::new);

    public PlayersOnlyBarrierBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<BarrierBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean passes(BlockState state, BlockPos pos, Entity mover, Entity subject) {
        return subject instanceof Player;
    }

    // Open for everyone whose client predicts movement, so chunks carry air and need no fixing.
    @Override
    public boolean defaultOpen() {
        return true;
    }
}
