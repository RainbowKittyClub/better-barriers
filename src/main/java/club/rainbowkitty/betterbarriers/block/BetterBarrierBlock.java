package club.rainbowkitty.betterbarriers.block;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import club.rainbowkitty.betterbarriers.sync.BarrierSync;


/**
 * A barrier that passes the entities its subclass names and blocks every other one. The server
 * answers each shape query from the entity in the collision context; the client of every player
 * is told {@code minecraft:barrier} or {@code minecraft:air} per block instead, which a vanilla
 * client needs nothing to render.
 *
 * <p>Polymer would otherwise answer a player's collision query with the client state's shape
 * ({@code BlockStateBaseMixin}), so {@link #overridePlayerCollisionsWithPolymer} opts out. A
 * context with no entity (pathfinding, the shape cache) gets the full cube.
 */
public abstract class BetterBarrierBlock extends BarrierBlock implements PolymerBlock {
    private static final BlockState SOLID = Blocks.BARRIER.defaultBlockState();
    private static final BlockState SOLID_WET = SOLID.setValue(WATERLOGGED, true);
    private static final BlockState OPEN = Blocks.AIR.defaultBlockState();
    private static final BlockState OPEN_WET = Blocks.WATER.defaultBlockState();
    // An editor's open barrier: no collision, a full-block outline while their client holds a
    // light item (the barrier item's disguise), no light; the pack blanks its marker.
    private static final BlockState STAND_IN = Blocks.LIGHT.defaultBlockState()
            .setValue(LightBlock.LEVEL, 0);
    private static final BlockState STAND_IN_WET = STAND_IN.setValue(LightBlock.WATERLOGGED, true);

    protected BetterBarrierBlock(Properties properties) {
        super(properties);
    }

    /**
     * The vanilla barrier's properties, plus {@code isSuffocating} and {@code isViewBlocking} off:
     * {@code Entity.isInWall} asks those from the entity-less shape cache, which is the full cube,
     * so a player standing inside an open barrier would otherwise take suffocation damage.
     */
    public static Properties properties(ResourceKey<Block> key) {
        return Properties.ofFullCopy(Blocks.BARRIER)
                .isSuffocating((state, level, pos) -> false)
                //? if >=26.3 {
                /*.isViewBlocking((state, level, pos, nearPlane) -> false)
                *///?} else {
                .isViewBlocking((state, level, pos) -> false)
                //?}
                .setId(key);
    }

    /** Whether {@code mover} may pass this block at {@code pos}. */
    public final boolean admits(BlockState state, BlockPos pos, Entity mover) {
        return passes(state, pos, mover, subjectOf(mover));
    }

    /**
     * This type's rule. {@code mover} is the colliding entity; {@code subject} is who decides: the
     * player aboard when {@code mover} is a mount or vehicle carrying one, else {@code mover}.
     */
    protected abstract boolean passes(BlockState state, BlockPos pos, Entity mover, Entity subject);

    /** Whether a chunk packet shows this block open (air) rather than solid (barrier). */
    public boolean defaultOpen() {
        return false;
    }

    /**
     * The client state for this block, open or not, keeping its water. An editor with the
     * resource pack gets a level-0 light for an open one, which they can target and walk through.
     */
    public final BlockState substitute(BlockState state, boolean open, boolean editing) {
        boolean water = state.getValue(WATERLOGGED);
        if (!open) {
            return water ? SOLID_WET : SOLID;
        }
        if (editing) {
            return water ? STAND_IN_WET : STAND_IN;
        }
        return water ? OPEN_WET : OPEN;
    }

    /** How this block's sprite turns toward the viewer in the editing view. */
    public Display.BillboardConstraints spriteBillboard(BlockState state) {
        return Display.BillboardConstraints.CENTER;
    }

    /** The sprite's rotation inside that billboard; none faces east, the texture's right. */
    public Quaternionf spriteRotation(BlockState state) {
        return new Quaternionf();
    }

    /** The client state every chunk packet carries, before any per-player correction. */
    public final BlockState defaultSubstitute(BlockState state) {
        return substitute(state, defaultOpen(), false);
    }

    // The entity whose permissions, mode and hands decide: the player steering a mount, else any
    // player aboard however high (a minecart has no controller), else the mover itself.
    private static Entity subjectOf(Entity mover) {
        if (mover.getControllingPassenger() instanceof ServerPlayer player) {
            return player;
        }
        for (Entity passenger : mover.getIndirectPassengers()) {
            if (passenger instanceof ServerPlayer player) {
                return player;
            }
        }
        return mover;
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return defaultSubstitute(state);
    }

    /**
     * What {@code player}'s client holds at {@code pos}, corrections included. Overrides the RKC
     * Polymer fork's {@code PolymerBlock} default by signature alone, to build on stock Polymer.
     */
    public BlockState getPolymerBlockStateAt(BlockState state, BlockPos pos, ServerPlayer player) {
        return BarrierSync.clientState(player, pos, state, this);
    }

    @Override
    public boolean overridePlayerCollisionsWithPolymer(BlockGetter level, BlockPos pos,
            BlockState state, ServerPlayer player) {
        return false;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return shape(state, pos, context);
    }

    // The outline too, so a server-side ray cast for an admitted player passes as their client's.
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return shape(state, pos, context);
    }

    private VoxelShape shape(BlockState state, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext) {
            Entity mover = entityContext.getEntity();
            if (mover != null && admits(state, pos, mover)) {
                return Shapes.empty();
            }
        }
        return Shapes.block();
    }
}
