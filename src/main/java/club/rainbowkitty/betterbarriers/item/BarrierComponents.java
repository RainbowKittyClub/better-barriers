package club.rainbowkitty.betterbarriers.item;

import com.mojang.serialization.DataResult;
import eu.pb4.polymer.core.api.other.PolymerComponent;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;

import club.rainbowkitty.betterbarriers.BetterBarriers;
import club.rainbowkitty.betterbarriers.block.GameModeBarrierBlock;
import club.rainbowkitty.betterbarriers.block.GameModeRule;

/** The item components that carry a barrier item's setting into the block it places. */
public final class BarrierComponents {
    /** The game mode a gamemode barrier item places. */
    public static final DataComponentType<GameType> GAMEMODE = register("gamemode",
            DataComponentType.<GameType>builder()
                    .persistent(GameType.CODEC.validate(BarrierComponents::placeable)).build());
    /** Whether a gamemode barrier item places an allow or a block barrier. */
    public static final DataComponentType<GameModeRule> GAMEMODE_RULE = register("gamemode_rule",
            DataComponentType.<GameModeRule>builder().persistent(GameModeRule.CODEC).build());
    /** The item a keyed barrier item places as the key. */
    public static final DataComponentType<Holder<Item>> KEY = register("key",
            DataComponentType.<Holder<Item>>builder()
                    .persistent(Item.CODEC).build());

    static {
        // Server-side only: hidden from Fabric's registry sync, so a client without this mod is
        // not turned away over unknown component types, and never sent inside an item stack.
        PolymerComponent.registerDataComponent(GAMEMODE, GAMEMODE_RULE, KEY);
    }

    private BarrierComponents() {
    }

    /** Forces registration; the fields above and the static block do the work. */
    public static void init() {
    }

    // Spectators pass through everything, so the block's mode has no spectator value.
    private static DataResult<GameType> placeable(GameType mode) {
        return GameModeBarrierBlock.MODE.getPossibleValues().contains(mode)
                ? DataResult.success(mode)
                : DataResult.error(() -> "No gamemode barrier for " + mode.getName());
    }

    private static <T> DataComponentType<T> register(String path, DataComponentType<T> type) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, BetterBarriers.id(path),
                type);
    }
}
