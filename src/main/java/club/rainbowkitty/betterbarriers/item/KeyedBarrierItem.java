package club.rainbowkitty.betterbarriers.item;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import club.rainbowkitty.betterbarriers.BetterBarriers;
import club.rainbowkitty.betterbarriers.block.BarrierLocks;
import club.rainbowkitty.betterbarriers.block.KeyedBarrierBlock;

/** The keyed barrier's item: its {@code lock} component is the lock it places. */
public final class KeyedBarrierItem extends BetterBarrierItem {
    public KeyedBarrierItem(KeyedBarrierBlock block, Properties properties) {
        super(block, properties);
    }

    // Sneak-use with a key in the off hand takes a lock it opens, the next one on each use for a
    // key to several; an item that is no key, or one survival could copy, is refused.
    @Override
    protected boolean cycle(ItemStack stack, Player player) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) {
            return false;
        }
        List<String> locks = BarrierLocks.locksOf(offhand);
        if (locks.isEmpty()) {
            player.sendOverlayMessage(BetterBarriers.text().of(BetterBarriers.MOD_ID + ".keyed."
                    + (offhand.is(BarrierLocks.COPYABLE) ? "copyable" : "not_a_key")));
            return false;
        }
        // An immutable list's indexOf throws on null, so a lock-less item starts at the first.
        String current = stack.get(BarrierComponents.LOCK);
        String next = current == null ? locks.getFirst()
                : locks.get((locks.indexOf(current) + 1) % locks.size());
        stack.set(BarrierComponents.LOCK, next);
        player.sendOverlayMessage(
                BetterBarriers.text().of(BetterBarriers.MOD_ID + ".keyed.lock_set", next));
        return true;
    }

    // "Lock:" in yellow before the lock's name, or how to set one.
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> lines, TooltipFlag flag) {
        String tooltip = getDescriptionId() + ".tooltip";
        String lock = stack.get(BarrierComponents.LOCK);
        if (lock == null) {
            lines.accept(hint(tooltip + ".unset"));
        } else {
            lines.accept(BetterBarriers.text().of(tooltip + ".lock",
                    Component.literal(lock).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}
