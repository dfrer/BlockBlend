package com.example.blockblend.client.mixin;

import com.example.blockblend.client.render.BlendRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderManager.class)
public abstract class BlockRenderManagerMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void blockblend$replaceOpaqueMesh(BlockState state, BlockPos pos, BlockRenderView world,
            MatrixStack matrices, VertexConsumer vertices, boolean cull, Random random, CallbackInfo ci) {
        if (BlendRenderer.suppress(pos, state)) ci.cancel();
    }
}
