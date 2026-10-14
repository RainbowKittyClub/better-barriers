package club.rainbowkitty.betterbarriers.sync;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Leaves barriers out of the height maps a chunk packet carries, so a client draws rain and snow
 * through them. The client keeps these maps until a block change in a column, which it applies
 * itself and where a barrier counts again.
 */
public final class BarrierHeightmaps {
    // The maps a client draws weather from; both count a barrier, as it blocks motion.
    private static final Set<Heightmap.Types> WEATHER = EnumSet.of(
            Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
    private static final int COLUMNS = 256;

    private BarrierHeightmaps() {
    }

    /** Lowers each column of {@code heightmaps} topped by a barrier to the block below it. */
    public static void skipBarriers(LevelChunk chunk, Map<Heightmap.Types, long[]> heightmaps) {
        if (!hasBarriers(chunk)) {
            return;
        }
        int minY = chunk.getMinY();
        int bits = Mth.ceillog2(chunk.getHeight() + 1);
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Map.Entry<Heightmap.Types, long[]> entry : heightmaps.entrySet()) {
            if (!WEATHER.contains(entry.getKey())) {
                continue;
            }
            Predicate<BlockState> counts = entry.getKey().isOpaque();
            // Wraps the packet's own copy, so each set writes straight into the packet.
            SimpleBitStorage heights = new SimpleBitStorage(bits, COLUMNS, entry.getValue());
            for (int index = 0; index < COLUMNS; index++) {
                int top = heights.get(index) + minY - 1;
                int y = top;
                while (y >= minY) {
                    BlockState state = chunk.getBlockState(
                            pos.set(baseX + (index & 15), y, baseZ + (index >> 4)));
                    if (counts.test(state) && !BarrierSync.IS_BARRIER.test(state)) {
                        break;
                    }
                    y--;
                }
                if (y != top) {
                    heights.set(index, y + 1 - minY);
                }
            }
        }
    }

    private static boolean hasBarriers(LevelChunk chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.hasOnlyAir() && section.maybeHas(BarrierSync.IS_BARRIER)) {
                return true;
            }
        }
        return false;
    }
}
