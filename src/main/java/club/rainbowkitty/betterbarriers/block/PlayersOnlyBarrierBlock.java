package club.rainbowkitty.betterbarriers.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;

import club.rainbowkitty.betterbarriers.BarrierGameRules;

/**
 * A barrier every player walks through. Mobs, items, projectiles and mounts carrying a player stop,
 * each unless its {@link BarrierGameRules} rule says otherwise; anything else always stops.
 */
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
        if (mover instanceof Player) {
            return true;
        }
        GameRule<Boolean> rule = subject instanceof Player ? BarrierGameRules.PLAYERS_ONLY_MOUNTS
                : mover instanceof Mob ? BarrierGameRules.PLAYERS_ONLY_MOBS
                : mover instanceof ItemEntity ? BarrierGameRules.PLAYERS_ONLY_ITEMS
                : mover instanceof Projectile ? BarrierGameRules.PLAYERS_ONLY_PROJECTILES
                : null;
        return rule != null && BarrierGameRules.get(mover.level(), rule);
    }

    // Open for everyone whose client predicts movement, so chunks carry air and need no fixing.
    @Override
    public boolean defaultOpen() {
        return true;
    }
}
