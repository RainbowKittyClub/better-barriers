package club.rainbowkitty.betterbarriers.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import club.rainbowkitty.betterbarriers.item.BarrierComponents;

/**
 * Holds a keyed barrier's lock: saved as {@code lock: "<name>"}, so {@code /setblock} and
 * {@code /fill} can set it, and carried as the item's {@code lock} component on place and pick.
 * Polymer strips it from every client's packets, since no client needs it.
 */
public final class KeyedBarrierBlockEntity extends BlockEntity {
    private static final String LOCK_TAG = "lock";

    private @Nullable String lock;

    public KeyedBarrierBlockEntity(BlockPos pos, BlockState state) {
        super(BarrierBlocks.KEYED_ENTITY, pos, state);
    }

    /** The lock name, folded to lowercase, or null for none, which opens for nobody. */
    public @Nullable String lock() {
        return this.lock;
    }

    /** Whether {@code stack} is a key for this lock. */
    public boolean opens(ItemStack stack) {
        return this.lock != null && BarrierLocks.opens(stack, this.lock);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.lock = input.read(LOCK_TAG, BarrierLocks.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable(LOCK_TAG, BarrierLocks.CODEC, this.lock);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.lock = components.get(BarrierComponents.LOCK);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(BarrierComponents.LOCK, this.lock);
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard(LOCK_TAG);
    }
}
