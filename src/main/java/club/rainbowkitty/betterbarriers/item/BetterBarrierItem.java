package club.rainbowkitty.betterbarriers.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

import club.rainbowkitty.betterbarriers.BetterBarriers;
import club.rainbowkitty.betterbarriers.block.BetterBarrierBlock;

/**
 * The item for one barrier type, shown with its sprite to a client with the resource pack and as a
 * vanilla barrier item to any other. Held in the main hand by a creative player it also turns on
 * <em>editing</em>: barriers near them show their sprites, and the open ones can be targeted while
 * still letting them through.
 */
public class BetterBarrierItem extends BlockItem implements PolymerItem {
    public BetterBarrierItem(BetterBarrierBlock block, Properties properties) {
        super(block, properties);
    }

    /** Whether {@code player} is editing barriers: creative, with one of these in the main hand. */
    public static boolean isEditing(ServerPlayer player) {
        return player.isCreative()
                && player.getMainHandItem().getItem() instanceof BetterBarrierItem;
    }

    // Sneak-use on nothing cycles the item's own setting, for the types that have one. CONSUME,
    // not SUCCESS: Polymer turns a success into a server-sent swing, which a vanilla client echoes
    // back as if it were a left-click (LeftClicks).
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown() && cycle(player.getItemInHand(hand), player)) {
            return InteractionResult.CONSUME;
        }
        return super.use(level, player, hand);
    }

    /** Advances this item's setting, if it has one; returns whether anything changed. */
    protected boolean cycle(ItemStack stack, Player player) {
        return false;
    }

    /** A grey hint line from this mod's lang file. */
    protected static MutableComponent hint(String key, Object... args) {
        return BetterBarriers.text().of(key, args).withStyle(ChatFormatting.GRAY);
    }

    // With the resource pack the client holds a light item: that gives an editor's level-0 light
    // stand-ins their outline, and the pack blanks the one marker it would add. Without the pack,
    // the barrier item and its markers.
    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        return PolymerResourcePackUtils.hasMainPack(context) ? Items.LIGHT : Items.BARRIER;
    }

    // Level 0, so the client's placement preview is a dark light block, not a level-15 flash.
    @Override
    public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context,
            HolderLookup.Provider lookup) {
        if (out.is(Items.LIGHT)) {
            out.set(DataComponents.BLOCK_STATE,
                    BlockItemStateProperties.EMPTY.with(LightBlock.LEVEL, 0));
        }
    }

    // This type's sprite for a client with the pack; null keeps the base item's own model.
    @Override
    public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context,
            HolderLookup.Provider lookup) {
        return PolymerResourcePackUtils.hasMainPack(context)
                ? stack.get(DataComponents.ITEM_MODEL) : null;
    }

    // Vanilla asks the placer's own collision shape, empty for one this barrier admits, so without
    // this a mob standing in the cell would be entombed.
    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        BlockPos pos = context.getClickedPos();
        return super.canPlace(context, state) && context.getLevel()
                .isUnobstructed(null, Shapes.block().move(pos.getX(), pos.getY(), pos.getZ()));
    }
}
