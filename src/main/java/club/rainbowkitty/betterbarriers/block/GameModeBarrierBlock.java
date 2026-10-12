package club.rainbowkitty.betterbarriers.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import club.rainbowkitty.betterbarriers.BarrierPermissions;
import club.rainbowkitty.betterbarriers.item.GameModeBarrierItem;

/**
 * A barrier for players by game mode: under {@code rule=allow} only players in its mode pass, and
 * nothing else does; under {@code rule=block} only players in its mode stop, and everything else
 * passes. A player holding {@code betterbarriers.bypass.gamemode.<mode>} passes either way, and a
 * mount carrying a player goes as they do. No spectator mode: spectators collide with nothing.
 */
public final class GameModeBarrierBlock extends BetterBarrierBlock {
    public static final MapCodec<BarrierBlock> CODEC = simpleCodec(GameModeBarrierBlock::new);
    public static final EnumProperty<GameType> MODE = EnumProperty.create("mode", GameType.class,
            GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE);
    public static final EnumProperty<GameModeRule> RULE =
            EnumProperty.create("rule", GameModeRule.class);

    public GameModeBarrierBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(WATERLOGGED, false).setValue(MODE, GameType.CREATIVE)
                .setValue(RULE, GameModeRule.ALLOW));
    }

    @Override
    public MapCodec<BarrierBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean passes(BlockState state, BlockPos pos, Entity mover, Entity subject) {
        if (!(subject instanceof ServerPlayer player)) {
            return state.getValue(RULE) == GameModeRule.BLOCK;
        }
        GameType mode = state.getValue(MODE);
        boolean inMode = player.gameMode() == mode;
        boolean passes = state.getValue(RULE) == GameModeRule.ALLOW ? inMode : !inMode;
        return passes || BarrierPermissions.bypass(player, BarrierPermissions.gamemode(mode));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE, RULE);
    }

    // A plain pick carries no block data, so the mode and rule go onto the item here.
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
            boolean includeData) {
        ItemStack stack = super.getCloneItemStack(level, pos, state, includeData);
        GameModeBarrierItem.configure(stack, state.getValue(MODE), state.getValue(RULE));
        return stack;
    }
}
