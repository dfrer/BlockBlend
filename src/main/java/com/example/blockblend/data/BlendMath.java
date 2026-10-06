package com.example.blockblend.data;
/** Standard normalized per-channel blend functions, with strength interpolated after blending. */
public final class BlendMath {
 /** Same scene equation as scene_blend.fsh: strength is already applied to the surface texture. */
 public static double sceneChannel(double backdrop,double surface,int mode,double opacity){
  return channel(backdrop,surface,mode,opacity);
 }
 public static double channel(double base,double overlay,int mode,double strength){
  double blended=switch(mode){
   case 0 -> base*overlay;
   case 1 -> 1-(1-base)*(1-overlay);
   case 2 -> base<=.5?2*base*overlay:1-2*(1-base)*(1-overlay);
   case 3 -> overlay<=.5?base-(1-2*overlay)*base*(1-base):base+(2*overlay-1)*(soft(base)-base);
   case 4 -> overlay<=.5?2*base*overlay:1-2*(1-base)*(1-overlay);
   case 5 -> base<=0?0:overlay>=1?1:Math.min(1,base/(1-overlay));
   case 6 -> base>=1?1:overlay<=0?0:1-Math.min(1,(1-base)/overlay);
   default -> throw new IllegalArgumentException("Invalid blend mode");
  };
  return Math.max(0,Math.min(1,base+(blended-base)*strength));
 }
 private static double soft(double b){return b<=.25?((16*b-12)*b+4)*b:Math.sqrt(b);}
 private BlendMath(){}
}
