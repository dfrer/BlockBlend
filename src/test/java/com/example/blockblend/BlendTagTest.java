package com.example.blockblend;
import com.example.blockblend.data.*;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BlendTagTest {
 @Test void rejectsNonfiniteAndOutOfRangeStrength(){for(float s:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-.1f,1.1f})assertThrows(IllegalArgumentException.class,()->new BlendTag(0,s,OverlayCatalog.IDS.getFirst()));}
 @Test void rejectsUnknownTextureAndMode(){assertThrows(IllegalArgumentException.class,()->new BlendTag(7,.5f,OverlayCatalog.IDS.getFirst()));assertThrows(IllegalArgumentException.class,()->new BlendTag(0,.5f,Identifier.of("minecraft","block/stone")));assertThrows(IllegalArgumentException.class,()->new BlendTag(0,.5f,null));}
 @Test void allSelectionsAreValid(){for(int m=0;m<7;m++)for(var id:OverlayCatalog.IDS)for(int s=0;s<=10;s++)assertEquals(s/10f,new BlendTag(m,s/10f,id).strength());}
 @Test void mathMatchesReferenceExamples(){
  assertEquals(.18,BlendMath.channel(.3,.6,0,1),1e-9);assertEquals(.72,BlendMath.channel(.3,.6,1,1),1e-9);
  assertEquals(.36,BlendMath.channel(.3,.6,2,1),1e-9);assertEquals(.3495445115,BlendMath.channel(.3,.6,3,1),1e-9);
  assertEquals(.44,BlendMath.channel(.3,.6,4,1),1e-9);assertEquals(.75,BlendMath.channel(.3,.6,5,1),1e-9);
  assertEquals(0,BlendMath.channel(.3,.6,6,1),1e-9);
 }
 @Test void strengthZeroAndFiniteOutputForEveryMode(){
  for(int m=0;m<7;m++)for(double b:new double[]{0,.25,.5,.75,1})for(double o:new double[]{0,.25,.5,.75,1}){
   assertEquals(b,BlendMath.channel(b,o,m,0));double result=BlendMath.channel(b,o,m,1);
   assertTrue(Double.isFinite(result)&&result>=0&&result<=1);assertEquals((b+result)/2,BlendMath.channel(b,o,m,.5),1e-9);
  }
 }
 @Test void dodgeAndBurnBackdropEndpointsRemainStable(){
  for(double strength:new double[]{0,.5,1}){assertEquals(0,BlendMath.channel(0,1,5,strength));assertEquals(1,BlendMath.channel(1,0,6,strength));}
 }
}
