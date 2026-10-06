package com.example.blockblend.client.mixin;

import com.example.blockblend.client.render.BlendRenderer;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkOcclusionDataBuilder.class)
public abstract class ChunkOcclusionMixin {
    @Inject(method = "markClosed", at = @At("HEAD"), cancellable = true)
    private void blockblend$openSectionVisibility(BlockPos pos, CallbackInfo ci) {
        if (BlendRenderer.opensVisibility(pos)) ci.cancel();
    }
}
