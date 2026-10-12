package club.rainbowkitty.betterbarriers.block;

import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.betterbarriers.BarrierPermissions;
import club.rainbowkitty.betterbarriers.item.BarrierComponents;

/**
 * A barrier only a player holding its key item, in either hand, walks through, or one holding
 * {@code betterbarriers.bypass.keyed.<namespace>.<path>} for that item. With no key set it opens
 * for nobody.
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

    // The key of the keyed barrier at `pos`, or null for none or no such block.
    private static @Nullable Holder<Item> keyAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof KeyedBarrierBlockEntity entity
                ? entity.key() : null;
    }

    @Override
    protected boolean passes(BlockState state, BlockPos pos, Entity mover, Entity subject) {
        if (!(subject instanceof ServerPlayer player)) {
            return false;
        }
        Holder<Item> key = keyAt(mover.level(), pos);
        return key != null && (player.isHolding(key.value())
                || BarrierPermissions.bypass(player, BarrierPermissions.keyed(key)));
    }

    // A plain pick carries no block data, so the key goes onto the item here.
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
            boolean includeData) {
        ItemStack stack = super.getCloneItemStack(level, pos, state, includeData);
        stack.set(BarrierComponents.KEY, keyAt(level, pos));
        return stack;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KeyedBarrierBlockEntity(pos, state);
    }
}
