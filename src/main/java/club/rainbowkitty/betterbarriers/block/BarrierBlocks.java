package club.rainbowkitty.betterbarriers.block;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import club.rainbowkitty.betterbarriers.BetterBarriers;
import club.rainbowkitty.betterbarriers.item.BetterBarrierItem;
import club.rainbowkitty.betterbarriers.item.GameModeBarrierItem;
import club.rainbowkitty.betterbarriers.item.KeyedBarrierItem;
import club.rainbowkitty.kalic0re.common.item.ItemNames;
import club.rainbowkitty.kalic0re.common.item.ItemRegistration;

/** Every barrier block and its item. */
public final class BarrierBlocks {
    public static final PlayersOnlyBarrierBlock PLAYERS_ONLY =
            register("players_only_barrier", PlayersOnlyBarrierBlock::new);
    public static final AdminBarrierBlock ADMIN =
            register("admin_barrier", AdminBarrierBlock::new);
    public static final OneWayBarrierBlock ONE_WAY =
            register("one_way_barrier", OneWayBarrierBlock::new);
    public static final GameModeBarrierBlock GAMEMODE = register("gamemode_barrier",
            GameModeBarrierBlock::new, GameModeBarrierItem::new);
    public static final KeyedBarrierBlock KEYED =
            register("keyed_barrier", KeyedBarrierBlock::new, KeyedBarrierItem::new);
    public static final BlockEntityType<KeyedBarrierBlockEntity> KEYED_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    BetterBarriers.id("keyed_barrier"),
                    new BlockEntityType<>(KeyedBarrierBlockEntity::new, Set.of(KEYED)));

    /** Every barrier, in creative-tab order. */
    public static final List<BetterBarrierBlock> ALL =
            List.of(PLAYERS_ONLY, ONE_WAY, GAMEMODE, KEYED, ADMIN);

    static {
        // Stripped from chunk and block-entity packets for every client; none needs the key.
        PolymerBlockUtils.registerBlockEntity(KEYED_ENTITY);
    }

    private BarrierBlocks() {
    }

    /** Forces registration; the fields above do the work. */
    public static void init() {
    }

    private static <T extends BetterBarrierBlock> T register(String path,
            Function<BlockBehaviour.Properties, T> factory) {
        return register(path, factory, BetterBarrierItem::new);
    }

    private static <T extends BetterBarrierBlock> T register(String path,
            Function<BlockBehaviour.Properties, T> factory,
            BiFunction<T, Item.Properties, BetterBarrierItem> itemFactory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, BetterBarriers.id(path));
        T block = Registry.register(BuiltInRegistries.BLOCK, key,
                factory.apply(BetterBarrierBlock.properties(key)));
        ItemRegistration.register(BetterBarriers.MOD_ID, path, itemKey -> ItemNames.named(itemKey,
                BetterBarriers.text().of("block." + BetterBarriers.MOD_ID + "." + path),
                itemFactory.apply(block,
                        new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
        return block;
    }
}
