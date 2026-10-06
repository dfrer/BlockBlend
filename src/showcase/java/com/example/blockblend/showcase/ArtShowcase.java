package com.example.blockblend.showcase;

import com.example.blockblend.client.BlendClientCache;
import com.example.blockblend.client.render.BlendRenderer;
import com.example.blockblend.data.BlendState;
import com.example.blockblend.data.BlendTag;
import com.example.blockblend.data.OverlayCatalog;
import com.example.blockblend.registry.ModNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.tutorial.TutorialStep;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Original, deterministic gallery fixture. Never registers commands or opens an existing world. */
public final class ArtShowcase implements ClientModInitializer {
    public static final long SEED = 178123L;
    private record Step(String name, Runnable action, BooleanSupplier ready, int delay, int timeout) {}
    private final Deque<Step> steps = new ArrayDeque<>();
    private final Map<BlockPos, BlendTag> painted = new LinkedHashMap<>();
    private final List<Double> frameMillis = new ArrayList<>();
    private MinecraftClient client;
    private Step active;
    private CompletableFuture<Void> serverWork;
    private Path output;
    private String worldName;
    private boolean started, finished, measuring;
    private int elapsed, totalTicks, captures;
    private long lastFrame;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("blockblend.showcase")) return;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        WorldRenderEvents.END.register(context -> {
            long now = System.nanoTime();
            if (measuring && lastFrame != 0 && frameMillis.size() < 5000) frameMillis.add((now-lastFrame)/1_000_000d);
            lastFrame = now;
        });
    }

    private void tick(MinecraftClient mc) {
        if (finished) return;
        client = mc;
        try {
            if (!started) {
                if (!mc.isFinishedLoading() || mc.getOverlay() != null) return;
                Path run = mc.runDirectory.toPath().toAbsolutePath().normalize();
                // The harness intentionally refuses ordinary run folders and launcher worlds.
                require(run.endsWith(Path.of("build", "showcase", "client")), "showcase run directory must end in build/showcase/client");
                require(run.equals(Path.of(System.getProperty("blockblend.showcase.runDir", run.toString())).toAbsolutePath().normalize()), "actual showcase run directory matches configured directory");
                worldName = "blockblend-art-" + UUID.randomUUID();
                output = Path.of(System.getProperty("blockblend.showcase.output", "build/showcase/evidence")).toAbsolutePath().normalize().resolve(worldName);
                Files.createDirectories(output);
                event("start", "new disposable world="+worldName+" seed="+SEED+" Minecraft=1.21.1 FabricAPI=0.102.0+1.21.1");
                mc.options.pauseOnLostFocus = false;
                mc.options.hudHidden = true;
                mc.options.getViewDistance().setValue(8);
                mc.options.getSimulationDistance().setValue(5);
                mc.options.getBobView().setValue(false);
                mc.options.getAo().setValue(false);
                mc.options.getGamma().setValue(1d);
                mc.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
                mc.options.getGraphicsMode().setValue(GraphicsMode.FANCY);
                mc.options.setAccessibilityOnboarded();
                mc.getTutorialManager().setStep(TutorialStep.NONE);
                enqueue(); started = true;
                mc.createIntegratedServerLoader().createAndStart(worldName,
                    new LevelInfo("BlockBlend original art gallery", GameMode.SPECTATOR, false, Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(SEED, false, false), r -> r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder(), new TitleScreen());
            }
            require(++totalTicks <= 12000, "10 minute tick budget");
            if (active == null) {
                active = steps.poll();
                if (active == null) { finish(); return; }
                elapsed = 0; event("step", active.name()); active.action().run();
            }
            if (++elapsed >= active.delay() && active.ready().getAsBoolean()) { event("pass", active.name()); active = null; }
            else require(elapsed <= active.timeout(), "Timed out: "+active.name());
        } catch (Throwable failure) {
            finished = true; event("FAIL", failure.toString()); failure.printStackTrace(); mc.scheduleStop();
        }
    }

    private void enqueue() {
        step("joined disposable world", () -> {}, this::joined, 20, 2400);
        server("construct original three-study gallery", p -> {
            ServerWorld w = p.getServerWorld();
            w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, p.server);
            w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, p.server);
            w.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, p.server);
            w.setTimeOfDay(6000); w.setWeather(0, 0, false, false);
            // Quiet elevated stage: all supporting blocks and backgrounds are vanilla opaque blocks.
            fill(w, -19, 78, 1, 84, 78, 35, Blocks.BLACK_CONCRETE);
            windows(w); screens(w); sculpture(w);
            p.teleport(w, 31.5, 90, -28, 0, 12);
            event("fixture", "painted="+painted.size()+"; three original scenes; server BlendState tags written and exact readback checked");
        });
        step("terrain and models settle", () -> client.setScreen(null), this::joined, 180, 2400);
        server("production chunk sync of persisted tags", this::synchronize);
        step("every painted block is cached and renderer eligible", () -> {}, () -> {
            for (var e : painted.entrySet()) if (!e.getValue().equals(BlendClientCache.get(e.getKey())) || !BlendRenderer.suppress(e.getKey(),client.world.getBlockState(e.getKey()))) return false;
            return true;
        }, 120, 1200);
        camera("overview", 87, 85, 4, 53, 7, 65);
        capture("gallery-overview");
        camera("seven mode windows", .5, 83.1, -3.5, 0, 3, 42);
        capture("seven-mode-windows");
        camera("layered patterned screens", 35.5, 82.5, 4, -7, 3, 43);
        capture("layered-screens");
        camera("chromatic ring sculpture", 64, 84, 7, -29, 6, 44);
        capture("chromatic-sculpture");
        step("small gallery frame sample", () -> { frameMillis.clear(); lastFrame=0; measuring=true; }, () -> true, 120, 200);
        step("report bounded frame sample", () -> {
            measuring=false; require(!frameMillis.isEmpty(), "render samples recorded");
            frameMillis.sort(Double::compareTo);
            event("frame-sample", "painted="+painted.size()+" frames="+frameMillis.size()+" median-ms="+frameMillis.get(frameMillis.size()/2)+" p95-ms="+frameMillis.get(Math.min(frameMillis.size()-1,(int)(frameMillis.size()*.95)))+"; wall-clock render intervals, vsync/scheduling included; small gallery only, not dense-world benchmark");
        }, () -> true, 1, 10);
    }

    private void windows(ServerWorld w) {
        // Seven 3x5 windows in a dark architectural surround, against an uninterrupted color quilt.
        Block[] quilt = {Blocks.CYAN_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.MAGENTA_CONCRETE};
        for(int x=-15;x<=15;x++)for(int y=79;y<=88;y++) put(w,x,y,26,quilt[Math.floorMod((x+15)/3+(y-79)/2,quilt.length)]);
        fill(w,-15,79,20,15,87,20,Blocks.BLACK_CONCRETE);
        fill(w,-16,79,19,16,79,19,Blocks.SMOOTH_QUARTZ);
        for(int mode=0;mode<7;mode++) {
            // Facing +Z, screenshot-left is world +X. Reverse placement for mode order 0..6.
            int left=11-mode*4;
            for(int x=left;x<left+3;x++)for(int y=81;y<=85;y++) paint(w,x,y,20,Blocks.PINK_CONCRETE,mode,0,0,.7f);
            // A small luminous marker separates each equation without in-frame text/UI.
            put(w,left+1,79,19,Blocks.SEA_LANTERN);
        }
        event("study", "seven-mode-windows | screenshot mode left to right 0..6 | pink concrete | noise strength=0 | opacity=.7 | x=11-4m..13-4m y=81..85 z=20 | quilt z=26");
    }

    private void screens(ServerWorld w) {
        Block[] quilt={Blocks.YELLOW_CONCRETE,Blocks.BLUE_CONCRETE,Blocks.MAGENTA_CONCRETE,Blocks.ORANGE_CONCRETE};
        for(int x=31;x<=45;x++)for(int y=79;y<=88;y++) put(w,x,y,27,quilt[Math.floorMod((x-31)/3+(y-79)/2,quilt.length)]);
        fill(w,30,79,14,46,79,26,Blocks.SMOOTH_QUARTZ);
        for(int x=33;x<=43;x++)for(int y=81;y<=86;y++) {
            paint(w,x,y,22,Blocks.LIGHT_BLUE_CONCRETE,1,2,.8f,.6f);
            paint(w,x-2,y,18,Blocks.PINK_CONCRETE,0,1,.9f,.6f);
        }
        // Offsets expose both screen edges; opaque rails emphasize that transparency is a surface effect.
        fill(w,30,80,18,42,80,18,Blocks.BLACK_CONCRETE);
        fill(w,32,87,22,44,87,22,Blocks.BLACK_CONCRETE);
        event("study", "layered-screens | front pink multiply stripes strength=.9 opacity=.6 x=31..41 y=81..86 z=18 | rear light-blue screen scratches strength=.8 opacity=.6 x=33..43 y=81..86 z=22 | quilt z=27");
    }

    private void sculpture(ServerWorld w) {
        fill(w,63,79,14,80,79,31,Blocks.SMOOTH_QUARTZ);
        for(int x=63;x<=80;x++)for(int y=79;y<=90;y++) put(w,x,y,32,((x-63)/3+(y-79)/3)%2==0?Blocks.CYAN_CONCRETE:Blocks.ORANGE_CONCRETE);
        // Three cubic rings occupy different planes/depths. Each remains a solid collision block.
        for(int x=66;x<=73;x++)for(int y=81;y<=88;y++) if(x==66||x==73||y==81||y==88)
            paint(w,x,y,20,Blocks.ORANGE_CONCRETE,1,0,.3f,.7f);
        for(int z=18;z<=27;z++)for(int y=82;y<=87;y++) if(z==18||z==27||y==82||y==87)
            paint(w,71,y,z,Blocks.LIGHT_BLUE_CONCRETE,0,1,.7f,.6f);
        for(int x=69;x<=77;x++)for(int y=80;y<=86;y++) if(x==69||x==77||y==80||y==86)
            paint(w,x,y,25,Blocks.MAGENTA_CONCRETE,3,2,.6f,.7f);
        put(w,69,80,19,Blocks.SEA_LANTERN); put(w,76,80,28,Blocks.SEA_LANTERN);
        event("study", "chromatic-sculpture | orange XY ring x=66..73 y=81..88 z=20 Screen/noise .3/.7 | blue YZ ring x=71 y=82..87 z=18..27 Multiply/stripes .7/.6 | magenta XY ring x=69..77 y=80..86 z=25 Soft Light/scratches .6/.7 | later ring wins at shared cells");
    }

    private void paint(ServerWorld w,int x,int y,int z,Block block,int mode,int overlay,float strength,float opacity) {
        BlockPos pos=new BlockPos(x,y,z); put(w,x,y,z,block);
        BlendTag tag=new BlendTag(mode,strength,OverlayCatalog.IDS.get(overlay),opacity);
        require(BlendState.get(w).set(pos,tag),"fixture persistence accepts "+pos);
        require(tag.equals(BlendState.get(w).get(pos)),"exact persisted fixture tag");
        painted.put(pos,tag);
    }
    private void synchronize(ServerPlayerEntity p) {
        Set<ChunkPos> chunks=new LinkedHashSet<>(); painted.keySet().forEach(pos->chunks.add(new ChunkPos(pos)));
        for(ChunkPos chunk:chunks) ServerPlayNetworking.send(p,new ModNetworking.ChunkData(p.getServerWorld().getRegistryKey().getValue(),chunk,BlendState.get(p.getServerWorld()).snapshotChunk(chunk)));
        event("sync", "production ChunkData packets="+chunks.size()+" persisted surfaces="+painted.size());
    }
    private void camera(String name,double x,double y,double z,float yaw,float pitch,int fov) {
        server("camera "+name,p->p.teleport(p.getServerWorld(),x,y,z,yaw,pitch));
        step("frame camera "+name,()->{client.options.getFov().setValue(fov); client.options.hudHidden=true;},()->client.player!=null&&Math.abs(client.player.getX()-x)<.01&&Math.abs(client.player.getZ()-z)<.01,80,600);
        step("camera receipt "+name,()->event("camera",name+" xyz="+x+","+y+","+z+" yaw="+yaw+" pitch="+pitch+" fov="+fov),()->true,1,10);
    }
    private void capture(String name) {
        step("capture "+name,()->{},()->{
            try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                require(image.getWidth()>=1280&&image.getHeight()>=720,"at least 1280x720 framebuffer");
                image.writeTo(output.resolve(name+".png"));
                event("screenshot",name+".png | "+image.getWidth()+"x"+image.getHeight()+" | native Minecraft framebuffer; no pixel modifications");
            }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
            captures++; return true;
        },60,90);
    }
    private static void fill(ServerWorld w,int x0,int y0,int z0,int x1,int y1,int z1,Block b){for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)put(w,x,y,z,b);}
    private static void put(ServerWorld w,int x,int y,int z,Block b){w.setBlockState(new BlockPos(x,y,z),b.getDefaultState());}
    private void server(String name,Consumer<ServerPlayerEntity> action){step(name,()->{UUID id=client.player.getUuid();var server=client.getServer();serverWork=CompletableFuture.runAsync(()->action.accept(Objects.requireNonNull(server.getPlayerManager().getPlayer(id))),server);},()->{if(!serverWork.isDone())return false;serverWork.join();return true;},3,1200);}
    private void step(String name,Runnable action,BooleanSupplier ready,int delay,int timeout){steps.add(new Step(name,action,ready,delay,timeout));}
    private boolean joined(){return client.player!=null&&client.world!=null&&client.getServer()!=null&&client.currentScreen==null;}
    private static void require(boolean condition,String detail){if(!condition)throw new AssertionError(detail);}
    private synchronized void event(String kind,String detail){if(output==null)return;String line="{\"time\":\""+Instant.now()+"\",\"kind\":\""+kind+"\",\"detail\":\""+detail.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"}\n";try{Files.writeString(output.resolve("showcase-receipt.jsonl"),line,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
    private void finish(){event("PASS","four raw anonymous frames; persisted and renderer-eligible surfaces="+painted.size()+"; captures="+captures+"; gallery artistic inspection remains separate");finished=true;if(client.world!=null)client.world.disconnect();client.disconnect(new TitleScreen());client.scheduleStop();}
}
