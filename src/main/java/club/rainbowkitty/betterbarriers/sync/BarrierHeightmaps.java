package club.rainbowkitty.betterbarriers.sync;

import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Leaves dry barriers out of the height map a client draws rain and snow from, as a chunk packet
 * carries it. The client keeps it until a block change in a column, which it applies itself and
 * where a barrier counts again.
 */
public final class BarrierHeightmaps {
    private static final int COLUMNS = 256;
    private static final Predicate<BlockState> MOTION_BLOCKING =
            Heightmap.Types.MOTION_BLOCKING.isOpaque();

    private BarrierHeightmaps() {
    }

    /** Lowers each column of the packet's {@code MOTION_BLOCKING} map topped by a dry barrier. */
    public static void skipBarriers(LevelChunk chunk, Map<Heightmap.Types, long[]> heightmaps) {
        long[] raw = heightmaps.get(Heightmap.Types.MOTION_BLOCKING);
        if (raw == null || !chunk.getLevel().canHaveWeather() || !hasBarriers(chunk)) {
            return;
        }
        int minY = chunk.getMinY();
        // Wraps the packet's own copy, so each set writes straight into the packet.
        SimpleBitStorage heights =
                new SimpleBitStorage(Mth.ceillog2(chunk.getHeight() + 1), COLUMNS, raw);
        for (int index = 0; index < COLUMNS; index++) {
            int top = heights.get(index) + minY - 1;
            heights.set(index, firstAvailable(chunk, index & 15, index >> 4, top) - minY);
        }
    }

    // Whether any section of the chunk may hold one of this mod's barriers.
    private static boolean hasBarriers(LevelChunk chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (BarrierSync.mayHoldBarriers(section)) {
                return true;
            }
        }
        return false;
    }

    // The y above the highest block at or under top in a column that tops it for rain.
    private static int firstAvailable(LevelChunk chunk, int x, int z, int top) {
        int y = top;
        while (y >= chunk.getMinY()) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
            if (section.hasOnlyAir()) {
                y = (y & ~15) - 1;
            } else if (topsColumn(section.getBlockState(x, y & 15, z))) {
                return y + 1;
            } else {
                y--;
            }
        }
        return chunk.getMinY();
    }

    // Whether a block tops a column for rain: as vanilla counts, a barrier only for its water.
    private static boolean topsColumn(BlockState state) {
        return BarrierSync.IS_BARRIER.test(state)
                ? !state.getFluidState().isEmpty()
                : MOTION_BLOCKING.test(state);
    }
}
