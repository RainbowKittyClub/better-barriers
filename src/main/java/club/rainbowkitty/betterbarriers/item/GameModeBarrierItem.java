package club.rainbowkitty.betterbarriers.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.betterbarriers.BetterBarriers;
import club.rainbowkitty.betterbarriers.block.GameModeBarrierBlock;
import club.rainbowkitty.betterbarriers.block.GameModeRule;

/**
 * The gamemode barrier's item: its {@code gamemode} and {@code gamemode_rule} components are what
 * it places. Sneak-use in the air cycles the mode, a sneaking left-click flips the rule
 * ({@link LeftClicks}), and its name, "Allow Creative Barrier" and the like, shows both.
 */
public final class GameModeBarrierItem extends BetterBarrierItem {
    private static final GameType DEFAULT_MODE = GameType.CREATIVE;
    private static final GameModeRule DEFAULT_RULE = GameModeRule.ALLOW;

    // Both settings default on the item, so a stack set back to the defaults equals a fresh one
    // and still matches its creative-tab entry.
    public GameModeBarrierItem(GameModeBarrierBlock block, Properties properties) {
        super(block, properties.component(BarrierComponents.GAMEMODE, DEFAULT_MODE)
                .component(BarrierComponents.GAMEMODE_RULE, DEFAULT_RULE));
    }

    /** The mode this stack places. */
    public static GameType modeOf(ItemStack stack) {
        return stack.getOrDefault(BarrierComponents.GAMEMODE, DEFAULT_MODE);
    }

    /** The rule this stack places. */
    public static GameModeRule ruleOf(ItemStack stack) {
        return stack.getOrDefault(BarrierComponents.GAMEMODE_RULE, DEFAULT_RULE);
    }

    /** Sets the mode and rule {@code stack} places. */
    public static void configure(ItemStack stack, GameType mode, GameModeRule rule) {
        stack.set(BarrierComponents.GAMEMODE, mode);
        stack.set(BarrierComponents.GAMEMODE_RULE, rule);
    }

    /** Flips this stack between allow and block. */
    public static void flipRule(ItemStack stack) {
        stack.set(BarrierComponents.GAMEMODE_RULE, ruleOf(stack).flipped());
    }

    /** This item set to every mode and rule, the allow ones first. */
    public List<ItemStack> variants() {
        List<ItemStack> stacks = new ArrayList<>();
        for (GameModeRule rule : GameModeRule.values()) {
            for (GameType mode : GameModeBarrierBlock.MODE.getPossibleValues()) {
                ItemStack stack = new ItemStack(this);
                configure(stack, mode, rule);
                stacks.add(stack);
            }
        }
        return stacks;
    }

    @Override
    protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        ItemStack stack = context.getItemInHand();
        return state == null ? null : state.setValue(GameModeBarrierBlock.MODE, modeOf(stack))
                .setValue(GameModeBarrierBlock.RULE, ruleOf(stack));
    }

    // The block's modes in order, round again; the client flashes the new name over the hotbar.
    @Override
    protected boolean cycle(ItemStack stack, Player player) {
        List<GameType> modes = GameModeBarrierBlock.MODE.getPossibleValues();
        stack.set(BarrierComponents.GAMEMODE,
                modes.get((modes.indexOf(modeOf(stack)) + 1) % modes.size()));
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> lines, TooltipFlag flag) {
        String key = getDescriptionId() + ".tooltip";
        lines.accept(hint(key + ".rule", ruleWord(ruleOf(stack).flipped())));
        lines.accept(hint(key + ".mode"));
    }

    // The client stack carries the per-stack name, which the mode and rule decide.
    @Override
    public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag flag, PacketContext context,
            HolderLookup.Provider lookup) {
        ItemStack out = super.getPolymerItemStack(stack, flag, context, lookup);
        out.set(DataComponents.ITEM_NAME, BetterBarriers.text().of(getDescriptionId() + ".named",
                ruleWord(ruleOf(stack)), modeOf(stack).getShortDisplayName()));
        return out;
    }

    // "Allow" in green or "Block" in red.
    private static MutableComponent ruleWord(GameModeRule rule) {
        return BetterBarriers.text().of(BetterBarriers.MOD_ID + ".gamemode_rule."
                + rule.getSerializedName()).withStyle(
                        rule == GameModeRule.ALLOW ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
}
