package com.example.blockblend.registry;
import com.example.blockblend.data.*;
import com.example.blockblend.security.BlendSecurity;
import com.example.blockblend.security.BlendPacketBudget;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import java.util.*;
public final class ModNetworking {
 private static final Map<UUID,BlendPacketBudget> RATES=new HashMap<>();
 private static BlendPacketBudget budget(ServerPlayerEntity p){return RATES.computeIfAbsent(p.getUuid(),id->new BlendPacketBudget());}
 public static void init(){
  PayloadTypeRegistry.playC2S().register(ApplyBlendC2SPayload.ID,ApplyBlendC2SPayload.CODEC);
  PayloadTypeRegistry.playC2S().register(ChunkRequest.ID,ChunkRequest.CODEC);
  PayloadTypeRegistry.playS2C().register(Update.ID,Update.CODEC);
  PayloadTypeRegistry.playS2C().register(ChunkData.ID,ChunkData.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(ApplyBlendC2SPayload.ID,(packet,context)->{
   var p=context.player();if(!budget(p).allowApply(p.getServer().getTicks())||!p.getServerWorld().getRegistryKey().getValue().equals(packet.dimension()))return;
   String error=BlendSecurity.rejection(p,packet.pos(),packet.tag());
   if(error!=null){p.sendMessage(Text.literal(error),true);return;}
   var state=BlendState.get(p.getServerWorld());
   if(!state.isWritable()){p.sendMessage(Text.literal("This world's blend data uses a newer format. Editing is disabled to preserve it."),true);return;}
   if(packet.tag()==null)state.remove(packet.pos());
   else if(!state.set(packet.pos(),packet.tag())){p.sendMessage(Text.literal("This chunk has reached its blend limit (4096). Erase an edit first."),true);return;}
   var update=new Update(p.getServerWorld().getRegistryKey().getValue(),packet.pos(),packet.tag());
   for(var watcher:PlayerLookup.tracking(p.getServerWorld(),new ChunkPos(packet.pos())))if(ServerPlayNetworking.canSend(watcher,Update.ID))ServerPlayNetworking.send(watcher,update);
   p.sendMessage(Text.literal(packet.tag()==null?"Blend erased.":"Applied "+packet.tag().getModeName()+" / "+packet.tag().overlayId().getPath().substring(8)+" / pattern "+Math.round(packet.tag().strength()*100)+"% / opacity "+Math.round(packet.tag().opacity()*100)+"%"),true);
  });
  ServerPlayNetworking.registerGlobalReceiver(ChunkRequest.ID,(packet,context)->{
   var p=context.player();var w=p.getServerWorld();
   if(!w.getRegistryKey().getValue().equals(packet.dimension)||!PlayerLookup.tracking(w,packet.pos).contains(p))return;
   var state=BlendState.get(w);
   if(!budget(p).allowChunk(p.getServer().getTicks(),packet.dimension+"/"+packet.pos.toLong(),state.chunkEntryCount(packet.pos)))return;
   ServerPlayNetworking.send(p,new ChunkData(packet.dimension,packet.pos,state.snapshotChunk(packet.pos)));
  });
  ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->server.execute(()->RATES.remove(handler.player.getUuid())));
  ServerLifecycleEvents.SERVER_STOPPED.register(server->RATES.clear());
 }
 public record ChunkRequest(Identifier dimension,ChunkPos pos) implements CustomPayload{
  public static final Id<ChunkRequest> ID=new Id<>(Identifier.of("blockblend","v3/request_chunk"));
  public static final PacketCodec<PacketByteBuf,ChunkRequest> CODEC=new PacketCodec<>(){
   public ChunkRequest decode(PacketByteBuf b){return new ChunkRequest(Identifier.of(b.readString(128)),new ChunkPos(b.readInt(),b.readInt()));}
   public void encode(PacketByteBuf b,ChunkRequest p){b.writeString(p.dimension.toString(),128);b.writeInt(p.pos.x);b.writeInt(p.pos.z);}
  };
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Update(Identifier dimension,BlockPos pos,BlendTag tag) implements CustomPayload{
  public static final Id<Update> ID=new Id<>(Identifier.of("blockblend","v3/update"));
  public static final PacketCodec<PacketByteBuf,Update> CODEC=new PacketCodec<>(){
   public Update decode(PacketByteBuf b){Identifier d=Identifier.of(b.readString(128));BlockPos p=b.readBlockPos();return new Update(d,p,b.readBoolean()?BlendTagCodec.read(b):null);}
   public void encode(PacketByteBuf b,Update p){b.writeString(p.dimension.toString(),128);b.writeBlockPos(p.pos);b.writeBoolean(p.tag!=null);if(p.tag!=null)BlendTagCodec.write(b,p.tag);}
  };
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record ChunkData(Identifier dimension,ChunkPos pos,Int2ObjectOpenHashMap<BlendTag> entries) implements CustomPayload{
  public static final Id<ChunkData> ID=new Id<>(Identifier.of("blockblend","v3/chunk"));
  public static final PacketCodec<PacketByteBuf,ChunkData> CODEC=new PacketCodec<>(){
   public ChunkData decode(PacketByteBuf b){
    Identifier d=Identifier.of(b.readString(128));ChunkPos p=new ChunkPos(b.readInt(),b.readInt());int size=b.readVarInt();
    if(size<0||size>BlendState.MAX_ENTRIES_PER_CHUNK)throw new IllegalArgumentException("Invalid chunk entry count");
    var map=new Int2ObjectOpenHashMap<BlendTag>();for(int i=0;i<size;i++){int key=b.readInt();map.put(key,BlendTagCodec.read(b));}
    return new ChunkData(d,p,map);
   }
   public void encode(PacketByteBuf b,ChunkData p){
    if(p.entries.size()>BlendState.MAX_ENTRIES_PER_CHUNK)throw new IllegalArgumentException("Oversized chunk");
    b.writeString(p.dimension.toString(),128);b.writeInt(p.pos.x);b.writeInt(p.pos.z);b.writeVarInt(p.entries.size());
    for(var e:p.entries.int2ObjectEntrySet()){b.writeInt(e.getIntKey());BlendTagCodec.write(b,e.getValue());}
   }
  };
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 private ModNetworking(){}
}


