package com.example.blockblend;
import com.example.blockblend.data.*;
import com.example.blockblend.registry.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.nbt.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BlendTagCodecTest {
 private static PacketByteBuf buffer(){return new PacketByteBuf(Unpooled.buffer());}
 @Test void allModesAndTexturesPacketRoundTrip(){for(int m=0;m<7;m++)for(var id:OverlayCatalog.IDS){var tag=new BlendTag(m,.7f,id);var buf=buffer();try{BlendTagCodec.write(buf,tag);assertEquals(tag,BlendTagCodec.read(buf));assertEquals(0,buf.readableBytes());}finally{buf.release();}}}
 @Test void malformedInputsFailClosed(){
  for(int mode:new int[]{7,255}){var b=buffer();try{b.writeByte(mode).writeFloat(.5f).writeByte(0);assertThrows(IllegalArgumentException.class,()->BlendTagCodec.read(b));}finally{b.release();}}
  for(float strength:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,2}){var b=buffer();try{b.writeByte(0).writeFloat(strength).writeByte(0);assertThrows(IllegalArgumentException.class,()->BlendTagCodec.read(b));}finally{b.release();}}
  var b=buffer();try{b.writeByte(0).writeFloat(.5f).writeByte(255);assertThrows(IllegalArgumentException.class,()->BlendTagCodec.read(b));}finally{b.release();}
 }
 @Test void truncatedPayloadIsNotConvertedToErase(){
  var b=buffer();try{b.writeString("minecraft:overworld",128);b.writeBlockPos(BlockPos.ORIGIN);b.writeBoolean(true);b.writeByte(0);
   assertThrows(IndexOutOfBoundsException.class,()->ApplyBlendC2SPayload.CODEC.decode(b));}finally{b.release();}
 }
 @Test void negativeAndOversizedChunkCountsRejectBeforeAllocation(){for(int size:new int[]{-1,4097,Integer.MAX_VALUE}){var b=buffer();try{b.writeString("minecraft:overworld",128);b.writeInt(0).writeInt(0).writeVarInt(size);assertThrows(IllegalArgumentException.class,()->ModNetworking.ChunkData.CODEC.decode(b));}finally{b.release();}}}
 @Test void applyAndEraseRoundTripIncludesDimension(){for(BlendTag tag:new BlendTag[]{null,new BlendTag(6,1,OverlayCatalog.IDS.get(2))}){var p=new ApplyBlendC2SPayload(Identifier.of("minecraft","the_nether"),new BlockPos(-16,319,16),tag);var b=buffer();try{ApplyBlendC2SPayload.CODEC.encode(b,p);assertEquals(p,ApplyBlendC2SPayload.CODEC.decode(b));}finally{b.release();}}}
 @Test void chunkRoundTripPreservesSignedHeight(){var map=new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<BlendTag>();map.put(ChunkKey.packLocal(new BlockPos(15,-1024,7)),new BlendTag(1,.3f,OverlayCatalog.IDS.get(1)));var p=new ModNetworking.ChunkData(Identifier.of("minecraft","overworld"),new ChunkPos(0,0),map);var b=buffer();try{ModNetworking.ChunkData.CODEC.encode(b,p);assertEquals(p,ModNetworking.ChunkData.CODEC.decode(b));}finally{b.release();}}
}
