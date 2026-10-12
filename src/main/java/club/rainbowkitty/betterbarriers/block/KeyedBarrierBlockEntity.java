package club.rainbowkitty.betterbarriers.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import club.rainbowkitty.betterbarriers.item.BarrierComponents;

/**
 * Holds a keyed barrier's key: saved as {@code key: "<item id>"}, so {@code /setblock} and
 * {@code /fill} can set it, and carried as the item's {@code key} component on place and pick.
 * Polymer strips it from every client's packets, since no client needs it.
 */
public final class KeyedBarrierBlockEntity extends BlockEntity {
    private static final String KEY_TAG = "key";

    private @Nullable Holder<Item> key;

    public KeyedBarrierBlockEntity(BlockPos pos, BlockState state) {
        super(BarrierBlocks.KEYED_ENTITY, pos, state);
    }

    /** The item that opens this barrier, or null for none, which opens for nobody. */
    public @Nullable Holder<Item> key() {
        return this.key;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.key = input.read(KEY_TAG, Item.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable(KEY_TAG, Item.CODEC, this.key);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.key = components.get(BarrierComponents.KEY);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(BarrierComponents.KEY, this.key);
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard(KEY_TAG);
    }
}
