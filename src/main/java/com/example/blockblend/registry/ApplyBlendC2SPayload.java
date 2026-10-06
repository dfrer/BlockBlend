package com.example.blockblend.registry;
import com.example.blockblend.data.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
public record ApplyBlendC2SPayload(Identifier dimension,BlockPos pos,BlendTag tag) implements CustomPayload {
 public static final Id<ApplyBlendC2SPayload> ID=new Id<>(Identifier.of("blockblend","v3/apply"));
 public static final PacketCodec<PacketByteBuf,ApplyBlendC2SPayload> CODEC=new PacketCodec<>(){
  public ApplyBlendC2SPayload decode(PacketByteBuf b){Identifier d=Identifier.of(b.readString(128));BlockPos p=b.readBlockPos();return new ApplyBlendC2SPayload(d,p,b.readBoolean()?BlendTagCodec.read(b):null);}
  public void encode(PacketByteBuf b,ApplyBlendC2SPayload p){b.writeString(p.dimension.toString(),128);b.writeBlockPos(p.pos);b.writeBoolean(p.tag!=null);if(p.tag!=null)BlendTagCodec.write(b,p.tag);}
 };
 public Id<? extends CustomPayload> getId(){return ID;}
}
