package club.rainbowkitty.betterbarriers.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;

/**
 * Exposes the tag {@code CustomData} keeps private, so a key's lock names can be read in place on
 * every barrier check; vanilla hands out only copies. Read-only: never change what it returns.
 */
@Mixin(CustomData.class)
public interface CustomDataAccessor {
    /** The item's own custom data. */
    @Accessor("tag")
    CompoundTag betterbarriers$tag();
}
