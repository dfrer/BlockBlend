package com.example.blockblend.client;
import com.example.blockblend.registry.*;
import com.example.blockblend.client.render.BlendRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import org.lwjgl.glfw.GLFW;
public final class BlockBlendClient implements ClientModInitializer {
 private static net.minecraft.client.world.ClientWorld requestWorld;
 private static final java.util.Map<net.minecraft.util.math.ChunkPos,Integer> pending=new java.util.LinkedHashMap<>();
 private static int requestTick;
 private static void ensureRequests(net.minecraft.client.world.ClientWorld world){if(requestWorld!=world){pending.clear();requestWorld=world;}}
 public void onInitializeClient(){
  KeyBinding mode=key("mode",GLFW.GLFW_KEY_B),texture=key("texture",GLFW.GLFW_KEY_N),up=key("increase",GLFW.GLFW_KEY_RIGHT_BRACKET),down=key("decrease",GLFW.GLFW_KEY_LEFT_BRACKET);
  KeyBinding opacityUp=key("opacity_increase",GLFW.GLFW_KEY_PERIOD),opacityDown=key("opacity_decrease",GLFW.GLFW_KEY_COMMA);
  ClientTickEvents.END_CLIENT_TICK.register(client->{
   BlendClientCache.ensureWorld();
   BlendRenderer.refreshEligibility();
   ensureRequests(client.world);
   requestTick++;
   if(client.world!=null&&ClientPlayNetworking.canSend(ModNetworking.ChunkRequest.ID)){
    int sent=0;for(var entry:pending.entrySet()){if(sent>=32)break;if(requestTick>=entry.getValue()){sent++;entry.setValue(requestTick+40);ClientPlayNetworking.send(new ModNetworking.ChunkRequest(client.world.getRegistryKey().getValue(),entry.getKey()));}}
   }
   if(client.player==null||client.currentScreen!=null||!client.player.getMainHandStack().isOf(ModItems.BLEND_APPLICATOR))return;
   boolean changed=false;
   while(mode.wasPressed()){BlendClientState.mode=(BlendClientState.mode+1)%7;changed=true;}
   while(texture.wasPressed()){BlendClientState.overlay=(BlendClientState.overlay+1)%3;changed=true;}
   while(up.wasPressed()){BlendClientState.strengthStep=Math.min(10,BlendClientState.strengthStep+1);changed=true;}
   while(down.wasPressed()){BlendClientState.strengthStep=Math.max(0,BlendClientState.strengthStep-1);changed=true;}
   while(opacityUp.wasPressed()){BlendClientState.opacityStep=Math.min(10,BlendClientState.opacityStep+1);changed=true;}
   while(opacityDown.wasPressed()){BlendClientState.opacityStep=Math.max(0,BlendClientState.opacityStep-1);changed=true;}
   if(changed)client.player.sendMessage(Text.literal(BlendClientState.description()),true);
  });
  UseBlockCallback.EVENT.register((player,world,hand,hit)->{
   if(!world.isClient||hand!=Hand.MAIN_HAND||!player.getStackInHand(hand).isOf(ModItems.BLEND_APPLICATOR))return ActionResult.PASS;
   if(!ClientPlayNetworking.canSend(ApplyBlendC2SPayload.ID)){player.sendMessage(Text.literal("This server needs BlockBlend installed."),true);return ActionResult.FAIL;}
   if(!player.isSneaking()&&!BlendRenderer.supports(world.getBlockState(hit.getBlockPos()),hit.getBlockPos())){player.sendMessage(Text.literal("Choose a simple solid cube with static opaque textures (stone, planks, wool, concrete). Layered/partial models, alpha textures, animation and block entities are unsupported."),true);return ActionResult.FAIL;}
   ClientPlayNetworking.send(new ApplyBlendC2SPayload(world.getRegistryKey().getValue(),hit.getBlockPos(),player.isSneaking()?null:BlendClientState.tag()));
   return ActionResult.SUCCESS;
  });
  ClientPlayNetworking.registerGlobalReceiver(ModNetworking.Update.ID,(p,c)->BlendClientCache.update(p.dimension(),p.pos(),p.tag()));
  ClientPlayNetworking.registerGlobalReceiver(ModNetworking.ChunkData.ID,(p,c)->{if(BlendClientCache.accepts(p.dimension())){BlendClientCache.chunk(p.dimension(),p.pos(),p.entries());pending.remove(p.pos());}});
  ClientChunkEvents.CHUNK_LOAD.register((world,chunk)->{
   BlendClientCache.ensureWorld();
   ensureRequests(world);pending.put(chunk.getPos(),0);
  });
  ClientChunkEvents.CHUNK_UNLOAD.register((world,chunk)->{BlendClientCache.unload(chunk.getPos());pending.remove(chunk.getPos());});
  ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->client.execute(()->{
   // Fabric may fire this event on Netty IO. Preserve a newer session if cleanup was queued.
   if(client.getNetworkHandler()!=null&&client.getNetworkHandler()!=handler)return;
   BlendClientCache.clear();BlendRenderer.clear();pending.clear();requestWorld=null;
  }));
  HudRenderCallback.EVENT.register((draw,tick)->{
   var client=net.minecraft.client.MinecraftClient.getInstance();
   if(client.player!=null&&client.player.getMainHandStack().isOf(ModItems.BLEND_APPLICATOR)&&!client.options.hudHidden){
    int y=8,width=Math.max(40,client.getWindow().getScaledWidth()-16);
    for(String line:java.util.List.of("BlockBlend: "+BlendClientState.description(),"B: mode | N: texture","[ / ]: pattern strength | , / .: opacity","Right-click: apply | Shift + right-click: erase")){
     for(var wrapped:client.textRenderer.wrapLines(Text.literal(line),width)){draw.drawTextWithShadow(client.textRenderer,wrapped,8,y,0xffffff);y+=10;}
    }
   }
  });
  BlendRenderer.init();
 }
 private static KeyBinding key(String name,int code){return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.blockblend."+name,InputUtil.Type.KEYSYM,code,"category.blockblend"));}
}
