package com.example.blockblend;
import com.example.blockblend.security.BlendPacketBudget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BlendPacketBudgetTest {
 @Test void repeatedDenseSnapshotsAreDeduplicatedAndReloadCanRetry(){
  var b=new BlendPacketBudget();assertTrue(b.allowChunk(0,"overworld/0",4096));
  for(int n=0;n<2000;n++)assertFalse(b.allowChunk(19,"overworld/0",4096));
  assertTrue(b.allowChunk(20,"overworld/0",4096));assertTrue(b.allowChunk(20,"nether/0",4096));
 }
 @Test void aggregateResponseWorkIsBoundedBeforeCopyingData(){
  var b=new BlendPacketBudget();int accepted=0;for(int n=0;n<1024;n++)if(b.allowChunk(0,"chunk/"+n,4096))accepted++;
  assertEquals(15,accepted);assertTrue(b.allowChunk(20,"chunk/16",4096));assertFalse(b.allowChunk(20,"invalid",4097));
 }
 @Test void editAndEmptySnapshotCountsAreSeparatelyBounded(){
  var b=new BlendPacketBudget();for(int n=0;n<40;n++)assertTrue(b.allowApply(0));assertFalse(b.allowApply(0));
  for(int n=0;n<1024;n++)assertTrue(b.allowChunk(0,"chunk/"+n,0));assertFalse(b.allowChunk(0,"last",0));assertTrue(b.allowApply(20));
 }
}
