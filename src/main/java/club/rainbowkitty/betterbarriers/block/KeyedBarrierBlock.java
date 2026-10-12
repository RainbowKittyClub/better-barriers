package club.rainbowkitty.betterbarriers.block;

import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.betterbarriers.BarrierPermissions;
import club.rainbowkitty.betterbarriers.item.BarrierComponents;

/**
 * A barrier only a player holding a key for its lock, in either hand, walks through, or one holding
 * {@code betterbarriers.bypass.keyed.<lock>} ({@link BarrierLocks}). With no lock set it opens for
 * nobody.
 */
public final class KeyedBarrierBlock extends BetterBarrierBlock implements EntityBlock {
    public static final MapCodec<BarrierBlock> CODEC = simpleCodec(KeyedBarrierBlock::new);

    public KeyedBarrierBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<BarrierBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean passes(BlockState state, BlockPos pos, Entity mover, Entity subject) {
        if (!(subject instanceof ServerPlayer player)
                || !(mover.level().getBlockEntity(pos) instanceof KeyedBarrierBlockEntity entity)
                || entity.lock() == null) {
            return false;
        }
        return entity.opens(player.getMainHandItem()) || entity.opens(player.getOffhandItem())
                || BarrierPermissions.bypass(player, BarrierPermissions.keyed(entity.lock()));
    }

    // A plain pick carries no block data, so the lock goes onto the item here.
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
            boolean includeData) {
        ItemStack stack = super.getCloneItemStack(level, pos, state, includeData);
        if (level.getBlockEntity(pos) instanceof KeyedBarrierBlockEntity entity) {
            stack.set(BarrierComponents.LOCK, entity.lock());
        }
        return stack;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KeyedBarrierBlockEntity(pos, state);
    }
}
