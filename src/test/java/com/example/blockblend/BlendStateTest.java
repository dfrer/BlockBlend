package com.example.blockblend;
import com.example.blockblend.data.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BlendStateTest {
 private static final BlendTag TAG=new BlendTag(3,.6f,OverlayCatalog.IDS.get(2));
 @Test void saveReloadEraseAndChunkCopies(){
  BlendState state=new BlendState();for(int y=-64;y<320;y++)assertTrue(state.set(new BlockPos(-17,y,19),TAG));
  NbtCompound saved=state.writeNbt(new NbtCompound(),null);assertEquals(3,saved.getInt("format"));
  BlendState loaded=BlendState.fromNbt(saved,null);for(int y=-64;y<320;y++)assertEquals(TAG,loaded.get(new BlockPos(-17,y,19)));
  var snapshot=loaded.snapshotChunk(new ChunkPos(-2,1));snapshot.clear();assertEquals(TAG,loaded.get(new BlockPos(-17,319,19)));
  loaded.remove(new BlockPos(-17,319,19));assertNull(BlendState.fromNbt(loaded.writeNbt(new NbtCompound(),null),null).get(new BlockPos(-17,319,19)));
  assertEquals(TAG,loaded.get(new BlockPos(-17,63,19)));
 }
 @Test void chunkLimitRejectsNewEditWithoutEvictingOld(){
  BlendState s=new BlendState();for(int n=0;n<4096;n++)assertTrue(s.set(new BlockPos((n>>4)&15,n>>8,n&15),TAG));
  assertFalse(s.set(new BlockPos(0,16,0),TAG));assertEquals(4096,s.snapshotChunk(new ChunkPos(0,0)).size());assertEquals(TAG,s.get(BlockPos.ORIGIN));
  assertTrue(s.set(BlockPos.ORIGIN,new BlendTag(0,1,OverlayCatalog.IDS.getFirst())));
 }
 @Test void invalidSavedEntryIsSkippedWithoutInventingAnOverlay(){
  NbtCompound saved=new BlendState().writeNbt(new NbtCompound(),null),chunk=new NbtCompound(),entry=BlendTagCodec.toNbt(TAG);
  chunk.putInt("x",0);chunk.putInt("z",0);entry.putFloat("strength",Float.NaN);entry.putInt("pos",0);NbtList entries=new NbtList();entries.add(entry);chunk.put("entries",entries);NbtList chunks=new NbtList();chunks.add(chunk);saved.put("chunks",chunks);
  assertNull(BlendState.fromNbt(saved,null).get(BlockPos.ORIGIN));
 }
 @Test void legacyReadableEntriesKeepTheirExistingCoordinates(){
  NbtCompound saved=new NbtCompound(),chunk=new NbtCompound(),entry=BlendTagCodec.toNbt(TAG);
  chunk.putInt("x",-1);chunk.putInt("z",2);entry.putInt("pos",(15<<12)|(3<<8)|128);NbtList entries=new NbtList();entries.add(entry);chunk.put("entries",entries);NbtList chunks=new NbtList();chunks.add(chunk);saved.put("chunks",chunks);
  assertEquals(TAG,BlendState.fromNbt(saved,null).get(new BlockPos(-1,64,35)));
 }
 @Test void unsupportedLegacyOverlaySurvivesMigrationAndSubsequentEdit(){
  NbtCompound saved=new NbtCompound(),chunk=new NbtCompound(),entry=BlendTagCodec.toNbt(TAG);
  entry.putString("overlayId","minecraft:block/stone");entry.putInt("pos",(15<<12)|(3<<8)|128);entry.putString("extra","preserve-me");
  chunk.putInt("x",-1);chunk.putInt("z",2);NbtList entries=new NbtList();entries.add(entry);chunk.put("entries",entries);NbtList chunks=new NbtList();chunks.add(chunk);saved.put("chunks",chunks);
  BlendState s=BlendState.fromNbt(saved,null);assertNull(s.get(new BlockPos(-1,64,35)));assertTrue(s.set(new BlockPos(-2,64,35),TAG));
  NbtCompound rewritten=s.writeNbt(new NbtCompound(),null);boolean found=false;
  NbtList written=rewritten.getList("chunks",10).getCompound(0).getList("entries",10);
  for(int i=0;i<written.size();i++){var n=written.getCompound(i);if(n.getString("overlayId").equals("minecraft:block/stone")){found=true;assertEquals("preserve-me",n.getString("extra"));assertEquals(ChunkKey.packLocal(new BlockPos(-1,64,35)),n.getInt("pos"));}}
  assertTrue(found);assertEquals(rewritten,BlendState.fromNbt(rewritten,null).writeNbt(new NbtCompound(),null));
 }
 @Test void futureFormatRemainsByteEquivalentAndReadOnly(){
  NbtCompound n=new NbtCompound();n.putInt("format",99);n.putString("future","unknown payload");BlendState s=BlendState.fromNbt(n,null);
  assertFalse(s.isWritable());assertFalse(s.set(BlockPos.ORIGIN,TAG));s.remove(BlockPos.ORIGIN);assertEquals(n,s.writeNbt(new NbtCompound(),null));
 }
}
