package com.example.blockblend.client.mixin;

import com.example.blockblend.client.render.BlendRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric Indigo can bypass BlockRenderManager entirely during section building. */
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext", remap = false)
public abstract class IndigoTerrainMixin {
    @Inject(method = "tessellateBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void blockblend$replaceIndigoMesh(BlockState state, BlockPos pos, BakedModel model,
            MatrixStack matrices, CallbackInfo ci) {
        if (BlendRenderer.suppress(pos, state)) ci.cancel();
    }
}
