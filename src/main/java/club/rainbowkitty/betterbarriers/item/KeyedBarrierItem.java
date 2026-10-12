package club.rainbowkitty.betterbarriers.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import club.rainbowkitty.betterbarriers.BetterBarriers;
import club.rainbowkitty.betterbarriers.block.KeyedBarrierBlock;

/** The keyed barrier's item: its {@code key} component is the key it places. */
public final class KeyedBarrierItem extends BetterBarrierItem {
    public KeyedBarrierItem(KeyedBarrierBlock block, Properties properties) {
        super(block, properties);
    }

    // Sneak-use with the key in the off hand makes that item the key; the tooltip shows it.
    @Override
    protected boolean cycle(ItemStack stack, Player player) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) {
            return false;
        }
        stack.set(BarrierComponents.KEY, offhand.typeHolder());
        return true;
    }

    // "Key Item:" in yellow before the key's own name, or how to set one.
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> lines, TooltipFlag flag) {
        String tooltip = getDescriptionId() + ".tooltip";
        Holder<Item> key = stack.get(BarrierComponents.KEY);
        if (key == null) {
            lines.accept(hint(tooltip + ".unset"));
        } else {
            Component name = key.value().getDefaultInstance().getHoverName().copy()
                    .withStyle(ChatFormatting.WHITE);
            lines.accept(BetterBarriers.text().of(tooltip + ".key", name)
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}
