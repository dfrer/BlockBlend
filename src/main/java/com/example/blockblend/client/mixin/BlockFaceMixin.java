package com.example.blockblend.client.mixin;

import com.example.blockblend.client.render.BlendRenderer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockFaceMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void blockblend$restoreNeighborFace(BlockState state, BlockView world, BlockPos pos,
            Direction side, BlockPos neighborPos, CallbackInfoReturnable<Boolean> cir) {
        if (BlendRenderer.suppress(neighborPos, world.getBlockState(neighborPos))) cir.setReturnValue(true);
    }
}
