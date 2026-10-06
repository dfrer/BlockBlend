package com.example.blockblend;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.*;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;
class OverlayResourcesTest {
 @Test void metadataIconIsAnActualPng() throws Exception {
  try(var stream=getClass().getResourceAsStream("/assets/blockblend/icon.png")){
   assertNotNull(stream);
   var image=ImageIO.read(stream);
   assertNotNull(image, "Fabric metadata icon must decode as an image");
   assertTrue(image.getWidth()>0&&image.getHeight()>0);
  }
 }
 @Test void patternsHaveRealPixelVariationAndDistinctContent() throws Exception {
  Set<String> hashes=new HashSet<>();
  for(String name:List.of("noise","stripes","scratches")){
   try(var stream=getClass().getResourceAsStream("/assets/blockblend/textures/overlay/"+name+".png")){
    assertNotNull(stream);byte[] bytes=stream.readAllBytes();assertTrue(hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))));
    var image=ImageIO.read(new java.io.ByteArrayInputStream(bytes));assertTrue(image.getWidth()>=16&&image.getHeight()>=16);
    Set<Integer> colors=new HashSet<>();for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)colors.add(image.getRGB(x,y));assertTrue(colors.size()>=2,name+" must contain a texture pattern");
   }
  }
 }
}
