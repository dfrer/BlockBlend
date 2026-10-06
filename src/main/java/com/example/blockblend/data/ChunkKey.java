package com.example.blockblend.data;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
/** Signed 24-bit Y plus 4 bits each for local X/Z. No dimension-height assumption. */
public final class ChunkKey {
 public static int packLocal(BlockPos p) {
  if(p==null || p.getY() < -8388608 || p.getY() > 8388607) throw new IllegalArgumentException("Unsupported position");
  return (p.getY()<<8)|((p.getX()&15)<<4)|(p.getZ()&15);
 }
 public static BlockPos unpackLocal(int p, ChunkPos c) { return new BlockPos((c.x<<4)|getX(p),getY(p),(c.z<<4)|getZ(p)); }
 public static int getX(int p){return (p>>>4)&15;}
 public static int getZ(int p){return p&15;}
 public static int getY(int p){return p>>8;}
 private ChunkKey(){}
}


