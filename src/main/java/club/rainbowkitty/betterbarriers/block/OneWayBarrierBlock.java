package club.rainbowkitty.betterbarriers.block;

//? if <26.3
import com.mojang.serialization.MapCodec;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
//? if <26.3
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

import club.rainbowkitty.betterbarriers.BarrierPermissions;

/**
 * A barrier anything passes travelling toward {@code facing} and nothing passes back. It is
 * closed only to a mover whose box lies entirely past its exit face, so a row of them is a
 * one-way plane: entered from behind, from inside, or from the side.
 */
public final class OneWayBarrierBlock extends BetterBarrierBlock {
    //? if <26.3
    public static final MapCodec<BarrierBlock> CODEC = simpleCodec(OneWayBarrierBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public OneWayBarrierBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(WATERLOGGED, false).setValue(FACING, Direction.NORTH));
    }

    //? if <26.3 {
    @Override
    public MapCodec<BarrierBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected boolean passes(BlockState state, BlockPos pos, Entity mover, Entity subject) {
        Direction facing = state.getValue(FACING);
        Direction.Axis axis = facing.getAxis();
        AABB box = mover.getBoundingBox();
        // A box the server stopped at the face can sit up to vanilla's contact epsilon inside it.
        boolean beyond = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? box.min(axis) >= pos.get(axis) + 1 - Shapes.EPSILON
                : box.max(axis) <= pos.get(axis) + Shapes.EPSILON;
        return !beyond || BarrierPermissions.bypass(subject, BarrierPermissions.ONE_WAY);
    }

    // The arrow along FACING with its broad side toward the placer's left and right, so it reads
    // from either side; up and down turn round the vertical to face whoever looks instead.
    @Override
    public Display.BillboardConstraints spriteBillboard(BlockState state) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Y
                ? Display.BillboardConstraints.VERTICAL : Display.BillboardConstraints.FIXED;
    }

    @Override
    public Quaternionf spriteRotation(BlockState state) {
        return switch (state.getValue(FACING)) {
            case UP -> new Quaternionf().rotationZ(Mth.HALF_PI);
            case DOWN -> new Quaternionf().rotationZ(-Mth.HALF_PI);
            case EAST -> new Quaternionf();
            case SOUTH -> new Quaternionf().rotationY(-Mth.HALF_PI);
            case WEST -> new Quaternionf().rotationY(Mth.PI);
            case NORTH -> new Quaternionf().rotationY(Mth.HALF_PI);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    // Passable the way the placer looks, so they can walk on through what they just placed.
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
