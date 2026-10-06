package com.example.blockblend.security;
import java.util.HashSet;
import java.util.Set;
/** Tick-based per-player limits include aggregate snapshot work, not just packet counts. */
public final class BlendPacketBudget {
 private long window=Long.MIN_VALUE;
 private int applies,requests,entries;
 private final Set<String> chunks=new HashSet<>();
 private void advance(long ticks){long next=ticks/20;if(next!=window){window=next;applies=0;requests=0;entries=0;chunks.clear();}}
 public boolean allowApply(long ticks){advance(ticks);if(applies>=40)return false;applies++;return true;}
 public boolean allowChunk(long ticks,String key,int size){
  advance(ticks);if(size<0||size>4096||requests>=1024||chunks.contains(key)||entries+size+2>65536)return false;
  requests++;entries+=size+2;chunks.add(key);return true;
 }
}
