package com.example.blockblend.data;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
public final class BlendTagCodec {
 public static void write(PacketByteBuf b,BlendTag t){b.writeByte(t.mode());b.writeFloat(t.strength());b.writeByte(OverlayCatalog.IDS.indexOf(t.overlayId()));b.writeFloat(t.opacity());}
 public static BlendTag read(PacketByteBuf b){
  int mode=b.readUnsignedByte();float strength=b.readFloat();int overlay=b.readUnsignedByte();
  if(overlay>=OverlayCatalog.IDS.size())throw new IllegalArgumentException("Invalid overlay");
  if(mode>=BlendTag.MODE_COUNT||!Float.isFinite(strength)||strength<0||strength>1)throw new IllegalArgumentException("Invalid blend settings");
  return new BlendTag(mode,strength,OverlayCatalog.IDS.get(overlay),b.readFloat());
 }
 public static NbtCompound toNbt(BlendTag t){NbtCompound n=new NbtCompound();n.putInt("mode",t.mode());n.putFloat("strength",t.strength());n.putFloat("opacity",t.opacity());n.putString("overlayId",t.overlayId().toString());return n;}
 public static BlendTag fromNbt(NbtCompound n){
  if(n.contains("opacity")&&!n.contains("opacity",net.minecraft.nbt.NbtElement.NUMBER_TYPE))throw new IllegalArgumentException("Invalid opacity type");
  return new BlendTag(n.getInt("mode"),n.getFloat("strength"),Identifier.tryParse(n.getString("overlayId")),n.contains("opacity")?n.getFloat("opacity"):1f);
 }
 private BlendTagCodec(){}
}


