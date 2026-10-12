package club.rainbowkitty.betterbarriers.sync;

import net.minecraft.world.level.block.state.BlockState;

// What one client holds at one position instead of the block's default: `sent`, computed from the
// real state `evaluated`. A packet carrying any other real state means the block changed.
record Correction(BlockState evaluated, BlockState sent) {
}
