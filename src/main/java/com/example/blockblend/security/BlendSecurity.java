package com.example.blockblend.security;
import com.example.blockblend.data.BlendTag;
import com.example.blockblend.registry.ModItems;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
public final class BlendSecurity {
 public static String rejection(ServerPlayerEntity p,BlockPos pos,BlendTag tag){
  var w=p.getServerWorld();
  if(p.isSpectator()||!p.getAbilities().allowModifyWorld||!w.canPlayerModifyAt(p,pos))return "You cannot edit this block.";
  if(!p.getMainHandStack().isOf(ModItems.BLEND_APPLICATOR))return "Hold the Blend Applicator in your main hand.";
  if(!w.isInBuildLimit(pos)||!w.getWorldBorder().contains(pos)||!w.getChunkManager().isChunkLoaded(pos.getX()>>4,pos.getZ()>>4))return "Block is outside the loaded world.";
  var hit=p.raycast(p.getBlockInteractionRange(),1,false);
  if(hit.getType()!=HitResult.Type.BLOCK||!((BlockHitResult)hit).getBlockPos().equals(pos))return "Aim at a block within normal reach.";
  var state=w.getBlockState(pos);
  if(state.isAir()||!state.isOpaqueFullCube(w,pos))return "Blend a solid full block (stone, planks, wool, etc.).";
  return null;
 }
 private BlendSecurity(){}
}
