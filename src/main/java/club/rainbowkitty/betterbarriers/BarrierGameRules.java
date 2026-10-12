package club.rainbowkitty.betterbarriers;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;

/**
 * Game rules that open the players-only barrier to more than players, each off by default:
 * {@code /gamerule betterbarriers:players_only.allow.mobs true} and so on.
 */
public final class BarrierGameRules {
    /** Mobs pass a players-only barrier. */
    public static final GameRule<Boolean> PLAYERS_ONLY_MOBS = allow("mobs");
    /** Dropped items pass a players-only barrier. */
    public static final GameRule<Boolean> PLAYERS_ONLY_ITEMS = allow("items");
    /** Arrows, tridents, snowballs and other projectiles pass a players-only barrier. */
    public static final GameRule<Boolean> PLAYERS_ONLY_PROJECTILES = allow("projectiles");
    /** A mount or vehicle carrying a player passes a players-only barrier with them. */
    public static final GameRule<Boolean> PLAYERS_ONLY_MOUNTS = allow("mounts");

    private BarrierGameRules() {
    }

    /** Forces registration before the registry freezes; the fields above do the work. */
    public static void init() {
    }

    /** {@code rule}'s value in {@code level}; false off the server. */
    public static boolean get(Level level, GameRule<Boolean> rule) {
        return level instanceof ServerLevel serverLevel && serverLevel.getGameRules().get(rule);
    }

    // Straight into vanilla's registry, a boolean rule built as vanilla builds its own.
    private static GameRule<Boolean> allow(String what) {
        GameRule<Boolean> rule = new GameRule<>(GameRuleCategory.MISC, GameRuleType.BOOL,
                BoolArgumentType.bool(), (visitor, self) -> visitor.visitBoolean(self), Codec.BOOL,
                value -> value ? 1 : 0, false, FeatureFlags.VANILLA_SET);
        return Registry.register(BuiltInRegistries.GAME_RULE,
                BetterBarriers.id("players_only.allow." + what), rule);
    }
}
