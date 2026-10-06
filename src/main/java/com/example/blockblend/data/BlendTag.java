package com.example.blockblend.data;
import net.minecraft.util.Identifier;
public record BlendTag(int mode,float strength,Identifier overlayId,float opacity) {
 public BlendTag(int mode,float strength,Identifier overlayId){this(mode,strength,overlayId,1f);}
 public static final int MODE_MULTIPLY=0,MODE_SCREEN=1,MODE_OVERLAY=2,MODE_SOFT_LIGHT=3,MODE_HARD_LIGHT=4,MODE_COLOR_DODGE=5,MODE_COLOR_BURN=6,MODE_COUNT=7;
 public BlendTag {
  if(mode<0||mode>=MODE_COUNT) throw new IllegalArgumentException("Invalid blend mode");
  if(!Float.isFinite(strength)||strength<0||strength>1) throw new IllegalArgumentException("Invalid blend strength");
  if(!Float.isFinite(opacity)||opacity<0||opacity>1) throw new IllegalArgumentException("Invalid blend opacity");
  if(!OverlayCatalog.contains(overlayId)) throw new IllegalArgumentException("Unknown overlay");
 }
 public String getModeName(){return new String[]{"Multiply","Screen","Overlay","Soft Light","Hard Light","Color Dodge","Color Burn"}[mode];}
}


