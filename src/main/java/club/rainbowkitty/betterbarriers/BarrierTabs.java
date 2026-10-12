package club.rainbowkitty.betterbarriers;

import java.util.ArrayList;
import java.util.List;

import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import club.rainbowkitty.betterbarriers.block.BarrierBlocks;
import club.rainbowkitty.betterbarriers.block.BetterBarrierBlock;
import club.rainbowkitty.betterbarriers.item.GameModeBarrierItem;

/**
 * This mod's own creative tab, shown with the gamemode barrier, and the barriers' place in
 * vanilla's Operator Utilities. Polymer syncs the contents of both to clients running Polymer
 * ({@code PolymerCreativeModeTabUtils.getCreativeModeTabs}).
 */
public final class BarrierTabs {
    private BarrierTabs() {
    }

    /** Registers the tab and the Operator Utilities entries. */
    public static void register() {
        CreativeModeTab tab = PolymerCreativeModeTabUtils.builder()
                .title(BetterBarriers.text().of("itemGroup." + BetterBarriers.MOD_ID + ".barriers"))
                .icon(() -> new ItemStack(BarrierBlocks.GAMEMODE))
                .displayItems((params, output) -> entries().forEach(output::accept))
                .build();
        PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(
                BetterBarriers.id("barriers"), tab);
        // Vanilla fills Operator Utilities only for an operator with the tab turned on.
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.OP_BLOCKS).register(output -> {
            if (output.shouldShowOpRestrictedItems()) {
                entries().forEach(output::accept);
            }
        });
    }

    // Each gamemode setting is its own entry, as vanilla lists each light level: a client names an
    // item's tabs in its tooltip only for an item equal to one of their entries.
    private static List<ItemStack> entries() {
        List<ItemStack> entries = new ArrayList<>();
        for (BetterBarrierBlock block : BarrierBlocks.ALL) {
            if (block.asItem() instanceof GameModeBarrierItem item) {
                entries.addAll(item.variants());
            } else {
                entries.add(new ItemStack(block));
            }
        }
        return entries;
    }
}
