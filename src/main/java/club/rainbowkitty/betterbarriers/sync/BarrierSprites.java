package club.rainbowkitty.betterbarriers.sync;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.betterbarriers.block.BetterBarrierBlock;

/**
 * The sprites one editing player sees: an item display at each barrier within reach showing that
 * type's icon from the resource pack, sent to that player alone. The reconcile calls
 * {@link #show} for every barrier it scans and {@link #endTick} after, so sprites follow the
 * player and the blocks with no state of their own.
 */
final class BarrierSprites {
    private final ElementHolder holder = new ElementHolder();
    // Element positions are offsets from here; any fixed point in the level would do.
    private final Vec3 origin;
    private final Long2ObjectMap<Sprite> shown = new Long2ObjectOpenHashMap<>();
    private final LongSet seen = new LongOpenHashSet();

    BarrierSprites(ServerPlayer player) {
        this.origin = Vec3.atLowerCornerOf(player.blockPosition());
        new ManualAttachment(this.holder, player.level(), () -> this.origin);
        this.holder.startWatching(player);
    }

    // Keeps a sprite at `pos` for `state`, replacing one made for a different state.
    void show(BlockPos pos, BlockState state, BetterBarrierBlock block) {
        long key = pos.asLong();
        this.seen.add(key);
        Sprite sprite = this.shown.get(key);
        if (sprite != null && sprite.state() == state) {
            return;
        }
        if (sprite != null) {
            this.holder.removeElement(sprite.element());
        }
        ItemDisplayElement element = new ItemDisplayElement(new ItemStack(block.asItem()));
        element.setItemDisplayContext(ItemDisplayContext.FIXED);
        element.setBillboardMode(block.spriteBillboard(state));
        element.setLeftRotation(block.spriteRotation(state));
        element.setBrightness(Brightness.FULL_BRIGHT);
        element.setOffset(Vec3.atCenterOf(pos).subtract(this.origin));
        this.holder.addElement(element);
        this.shown.put(key, new Sprite(state, element));
    }

    // Takes down every sprite no scan this tick asked for: out of reach, or no longer a barrier.
    void endTick() {
        ObjectIterator<Long2ObjectMap.Entry<Sprite>> entries =
                this.shown.long2ObjectEntrySet().iterator();
        while (entries.hasNext()) {
            Long2ObjectMap.Entry<Sprite> entry = entries.next();
            if (!this.seen.contains(entry.getLongKey())) {
                this.holder.removeElement(entry.getValue().element());
                entries.remove();
            }
        }
        this.seen.clear();
    }

    // Removes every sprite from the client; harmless on a client that already dropped them.
    void close() {
        this.holder.destroy();
    }

    private record Sprite(BlockState state, ItemDisplayElement element) {
    }
}
