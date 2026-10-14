package club.rainbowkitty.betterbarriers.sync;

import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Leaves barriers out of the height map a client draws rain and snow from, as a chunk packet
 * carries it. The client keeps it until a block change in a column, which it applies itself and
 * where a barrier counts again.
 */
public final class BarrierHeightmaps {
    private static final int COLUMNS = 256;
    private static final Predicate<BlockState> COUNTS =
            Heightmap.Types.MOTION_BLOCKING.isOpaque().and(BarrierSync.IS_BARRIER.negate());

    private BarrierHeightmaps() {
    }

    /** Lowers each column of the packet's {@code MOTION_BLOCKING} map topped by a barrier. */
    public static void skipBarriers(LevelChunk chunk, Map<Heightmap.Types, long[]> heightmaps) {
        long[] raw = heightmaps.get(Heightmap.Types.MOTION_BLOCKING);
        if (raw == null || !hasBarriers(chunk)) {
            return;
        }
        int minY = chunk.getMinY();
        // Wraps the packet's own copy, so each set writes straight into the packet.
        SimpleBitStorage heights =
                new SimpleBitStorage(Mth.ceillog2(chunk.getHeight() + 1), COLUMNS, raw);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int index = 0; index < COLUMNS; index++) {
            int y = heights.get(index) + minY - 1;
            // Chunk-local x and z, as vanilla's own Heightmap reads; the chunk masks them.
            while (y >= minY
                    && !COUNTS.test(chunk.getBlockState(pos.set(index & 15, y, index >> 4)))) {
                y--;
            }
            heights.set(index, y + 1 - minY);
        }
    }

    private static boolean hasBarriers(LevelChunk chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (BarrierSync.mayHoldBarriers(section)) {
                return true;
            }
        }
        return false;
    }
}
