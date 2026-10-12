package club.rainbowkitty.betterbarriers;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import club.rainbowkitty.betterbarriers.block.BarrierBlocks;
import club.rainbowkitty.betterbarriers.item.BarrierComponents;
import club.rainbowkitty.betterbarriers.item.LeftClicks;
import club.rainbowkitty.betterbarriers.sync.BarrierSync;
import club.rainbowkitty.kalic0re.common.text.FallbackText;

/** Mod entrypoint. */
public final class BetterBarriers implements ModInitializer {
    public static final String MOD_ID = "betterbarriers";

    private static FallbackText text;

    /** {@code betterbarriers:<path>} identifier. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** This mod's English, as translatable components a vanilla client can read. */
    public static FallbackText text() {
        return text;
    }

    @Override
    public void onInitialize() {
        text = FallbackText.load(MOD_ID);
        BarrierComponents.init();
        BarrierBlocks.init();
        BarrierSync.register();
        LeftClicks.register();
        BarrierTabs.register();
        // Not markAsRequired(): without the pack, items and markers fall back to vanilla's.
        PolymerResourcePackUtils.addModAssets(MOD_ID);
    }
}
