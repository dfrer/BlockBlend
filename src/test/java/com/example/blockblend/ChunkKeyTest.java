package com.example.blockblend;
import com.example.blockblend.data.ChunkKey;
import net.minecraft.util.math.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ChunkKeyTest {
 @Test void everyVanillaHeightIsDistinctAndRoundTrips(){
  Set<Integer> keys=new HashSet<>();ChunkPos chunk=new ChunkPos(-3,7);
  for(int y=-64;y<320;y++){BlockPos p=new BlockPos(-35,y,119);int k=ChunkKey.packLocal(p);assertTrue(keys.add(k),"height collision at "+y);assertEquals(p,ChunkKey.unpackLocal(k,chunk));}
 }
 @Test void customDimensionAndSignedCoordinatesRoundTrip(){
  for(int y:new int[]{-2048,-1024,-65,-64,0,191,192,255,256,319,320,1024,2047})
   for(int x:new int[]{-30000000,-17,-16,-1,0,15,16,30000000}){
    BlockPos p=new BlockPos(x,y,-x);assertEquals(p,ChunkKey.unpackLocal(ChunkKey.packLocal(p),new ChunkPos(p)));
   }
 }
 @Test void collisionRegression(){assertNotEquals(ChunkKey.packLocal(new BlockPos(0,-64,0)),ChunkKey.packLocal(new BlockPos(0,192,0)));}
}
