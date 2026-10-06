package com.example.blockblend.data;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;
import java.util.*;
/** Durable world data. Unloading chunks never removes saved edits. */
public final class BlendState extends PersistentState {
 public static final int MAX_ENTRIES_PER_CHUNK=4096;
 private final Map<ChunkPos,Int2ObjectOpenHashMap<BlendTag>> data=new HashMap<>();
 private final Map<ChunkPos,List<NbtCompound>> preserved=new HashMap<>();
 private NbtCompound futureFormat;
 public boolean isWritable(){return futureFormat==null;}
 private void removePreserved(ChunkPos c,int key){var entries=preserved.get(c);if(entries!=null){entries.removeIf(n->n.getInt("pos")==key);if(entries.isEmpty())preserved.remove(c);}}
 public static BlendState get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(new Type<>(BlendState::new,BlendState::fromNbt,null),"blockblend_state");}
 public BlendTag get(BlockPos p){var m=data.get(new ChunkPos(p));return m==null?null:m.get(ChunkKey.packLocal(p));}
 public boolean set(BlockPos p,BlendTag t){
  if(!isWritable())return false;
  ChunkPos c=new ChunkPos(p);int k=ChunkKey.packLocal(p);
  var m=data.computeIfAbsent(c,x->new Int2ObjectOpenHashMap<>());
  if(m.size()>=MAX_ENTRIES_PER_CHUNK&&!m.containsKey(k))return false;
  removePreserved(c,k);m.put(k,t);markDirty();return true;
 }
 public void remove(BlockPos p){if(!isWritable())return;ChunkPos c=new ChunkPos(p);int key=ChunkKey.packLocal(p);boolean archived=preserved.containsKey(c);removePreserved(c,key);var m=data.get(c);if(m!=null&&m.remove(key)!=null){if(m.isEmpty())data.remove(c);markDirty();}else if(archived)markDirty();}
 public Int2ObjectOpenHashMap<BlendTag> snapshotChunk(ChunkPos c){var m=data.get(c);return m==null?new Int2ObjectOpenHashMap<>():new Int2ObjectOpenHashMap<>(m);}
 public int chunkEntryCount(ChunkPos c){var m=data.get(c);return m==null?0:m.size();}
 @Override public NbtCompound writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup lookup){
  if(futureFormat!=null)return futureFormat.copy();
  n.putInt("format",3);NbtList chunks=new NbtList();
  Set<ChunkPos> positions=new HashSet<>(data.keySet());positions.addAll(preserved.keySet());
  for(var cp:positions){NbtCompound c=new NbtCompound();c.putInt("x",cp.x);c.putInt("z",cp.z);NbtList entries=new NbtList();
   for(var archived:preserved.getOrDefault(cp,List.of()))entries.add(archived.copy());
   var map=data.get(cp);if(map!=null)for(var t:map.int2ObjectEntrySet()){NbtCompound entry=BlendTagCodec.toNbt(t.getValue());entry.putInt("pos",t.getIntKey());entries.add(entry);}
   c.put("entries",entries);chunks.add(c);
  }n.put("chunks",chunks);return n;
 }
 public static BlendState fromNbt(NbtCompound n,RegistryWrapper.WrapperLookup lookup){
  BlendState s=new BlendState();if(n.getInt("format")>3){s.futureFormat=n.copy();com.example.blockblend.BlockBlendMod.LOGGER.warn("Future BlockBlend save format: editing disabled; original data preserved");return s;}
  boolean legacy=n.getInt("format")<2;NbtList chunks=n.getList("chunks",NbtElement.COMPOUND_TYPE);
  for(int i=0;i<chunks.size();i++){NbtCompound c=chunks.getCompound(i);ChunkPos cp=new ChunkPos(c.getInt("x"),c.getInt("z"));NbtList entries=c.getList("entries",NbtElement.COMPOUND_TYPE);
   for(int j=0;j<entries.size();j++){NbtCompound entry=entries.getCompound(j);int k=entry.getInt("pos");
    if(legacy){BlockPos p=new BlockPos((cp.x<<4)|((k>>>12)&15),(k&255)-64,(cp.z<<4)|((k>>>8)&15));k=ChunkKey.packLocal(p);}
    try{s.data.computeIfAbsent(cp,x->new Int2ObjectOpenHashMap<>()).put(k,BlendTagCodec.fromNbt(entry));}catch(IllegalArgumentException ex){NbtCompound archived=entry.copy();archived.putInt("pos",k);s.preserved.computeIfAbsent(cp,x->new ArrayList<>()).add(archived);com.example.blockblend.BlockBlendMod.LOGGER.warn("Preserved unsupported saved blend entry in {} without rendering it",cp);}
   }
  }if(legacy)s.markDirty();return s;
 }
}


