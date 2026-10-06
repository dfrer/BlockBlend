package com.example.blockblend.smoke;

import com.example.blockblend.client.BlendClientCache;
import com.example.blockblend.client.BlendClientState;
import com.example.blockblend.client.render.BlendRenderer;
import com.example.blockblend.data.BlendState;
import com.example.blockblend.data.BlendTag;
import com.example.blockblend.data.OverlayCatalog;
import com.example.blockblend.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.tutorial.TutorialStep;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.util.Identifier;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import com.example.blockblend.registry.ModNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Test-only scene-compositing contract. Deliberately independent of production BlendMath. */
public final class OpacitySmoke implements ClientModInitializer {
    // The ordinary backdrop touches z15 across a chunk boundary. Independent
    // painted layers use z15/z17 with a real air gap at z16.
    private static final BlockPos FRONT = new BlockPos(0, 81, 15), BACKDROP = FRONT.south(),
        REAR = FRONT.south(2), OCCLUDER = FRONT.north(2);
    private record Step(String name, Runnable action, BooleanSupplier ready, int delay, int timeout) {}
    private record Frame(int[] pixels, long hash) {}
    private record View(double x, double y, double z, float yaw, float pitch) {}
    private final Deque<Step> steps = new ArrayDeque<>();
    private final Map<String, Frame> frames = new HashMap<>();
    private MinecraftClient client;
    private Path receipt;
    private String worldName;
    private Step active;
    private int elapsed, totalTicks, passed;
    private volatile int screenshots;
    private boolean started, finished;
    private CompletableFuture<Void> serverWork;
    private CompletableFuture<Void> resourceReload;
    private IntegratedServer stopping;
    private int invalidDepthMode, restoredDepthWidth;
    private Framebuffer alteredDepth;
    private int originalWidth, originalHeight;
    private boolean fabulous;
    private View captureView = new View(.5, 80, 11.5, 0, 0);
    private static final BlockPos GRASS = new BlockPos(7, 81, 12);
    private static final List<net.minecraft.block.Block> SUPPORTED_BLOCKS = List.of(
        Blocks.STONE, Blocks.WHITE_WOOL, Blocks.OAK_PLANKS, Blocks.GLOWSTONE,
        Blocks.PINK_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.ORANGE_CONCRETE,
        Blocks.MAGENTA_CONCRETE, Blocks.RED_CONCRETE, Blocks.CYAN_CONCRETE,
        Blocks.YELLOW_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.WHITE_CONCRETE,
        Blocks.BLACK_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE);
    private final BlendTag frontSaved = tag(0, 0, .5f), rearSaved = tag(1, 0, .5f);

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("blockblend.smoke")) return;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        // Test-only fault injection at the real event boundary; production has no testing switch.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!fabulous || invalidDepthMode == 0) return;
            try {
                if (invalidDepthMode == 1) {
                    var ready = BlendRenderer.class.getDeclaredField("opaqueDepthReady");
                    ready.setAccessible(true); ready.setBoolean(null, false);
                } else {
                    var field = BlendRenderer.class.getDeclaredField("fabulousScene");
                    field.setAccessible(true); alteredDepth = (Framebuffer) field.get(null);
                    require(alteredDepth != null, "depth framebuffer prepared before invalid-dimension test");
                    restoredDepthWidth = alteredDepth.textureWidth;
                    alteredDepth.textureWidth++;
                }
            } catch (ReflectiveOperationException e) { throw new AssertionError("test-only depth fault injection", e); }
        });
        WorldRenderEvents.END.register(context -> {
            if (alteredDepth != null) {
                alteredDepth.textureWidth = restoredDepthWidth;
                alteredDepth = null;
            }
        });
    }
    private void tick(MinecraftClient mc) {
        if (finished) return;
        client = mc;
        try {
            if (active != null && active.name().startsWith("frame ")) restoreCaptureRotation(active.name());
            if (!started) {
                if (!mc.isFinishedLoading() || mc.getOverlay() != null) return;
                var run = mc.runDirectory.toPath().toAbsolutePath().normalize();
                String configured = System.getProperty("blockblend.smoke.runDir");
                require(configured != null && run.equals(Path.of(configured).toAbsolutePath().normalize()), "explicit isolated smoke run directory");
                worldName = "blockblend-opacity-" + UUID.randomUUID();
                receipt = Path.of(System.getProperty("blockblend.smoke.evidence",run.resolveSibling("evidence").toString())).resolve(worldName + ".jsonl");
                Files.createDirectories(receipt.getParent());
                event("start", worldName + " | new disposable world; dev identity only");
                mc.options.pauseOnLostFocus = false;
                mc.options.getViewDistance().setValue(5);
                mc.options.getSimulationDistance().setValue(5);
                mc.options.getBobView().setValue(false);
                mc.options.getFov().setValue(70);
                mc.options.getAo().setValue(false);
                mc.options.getGamma().setValue(1d);
                fabulous = System.getProperty("blockblend.smoke.graphics", "fabulous").equals("fabulous");
                mc.options.getGraphicsMode().setValue(fabulous ? GraphicsMode.FABULOUS : GraphicsMode.FANCY);
                originalWidth = mc.getWindow().getWidth(); originalHeight = mc.getWindow().getHeight();
                event("graphics", "mode="+mc.options.getGraphicsMode().getValue()+" | AO=false | gamma=1 | vanilla Fabric renderer; no Sodium/shaders");
                mc.options.setAccessibilityOnboarded();
                mc.getTutorialManager().setStep(TutorialStep.NONE);
                enqueue(); started = true;
                mc.createIntegratedServerLoader().createAndStart(worldName,
                    new LevelInfo("BlockBlend opacity smoke", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(178123L, false, false), r -> r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder(), new TitleScreen());
            }
            require(++totalTicks <= 18000, "15-minute tick budget");
            if (active == null) {
                active = steps.poll();
                if (active == null) { finish(); return; }
                elapsed = 0; event("step", active.name()); active.action().run();
            }
            if (++elapsed >= active.delay() && active.ready().getAsBoolean()) {
                passed++; event("pass", active.name()); active = null;
            } else require(elapsed <= active.timeout(), "Timed out: " + active.name());
        } catch (Throwable failure) {
            finished = true; event("FAIL", failure.toString()); failure.printStackTrace(); mc.scheduleStop();
        }
    }

    private void enqueue() {
        step("integrated server joined", () -> {}, this::joined, 20, 2400);
        server("fixture across touching chunk boundary", p -> {
            var w = p.getServerWorld();
            w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, p.server);
            w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, p.server);
            w.setTimeOfDay(6000); w.setWeather(0, 0, false, false);
            for (int x = -3; x <= 3; x++) for (int z = 5; z <= 18; z++) w.setBlockState(new BlockPos(x,79,z), Blocks.SMOOTH_STONE.getDefaultState());
            for (int x = -2; x <= 2; x++) for (int y = 79; y <= 84; y++) {
                w.setBlockState(new BlockPos(x,y,17), ((x+y)%2 == 0 ? Blocks.RED_CONCRETE : Blocks.CYAN_CONCRETE).getDefaultState());
                w.setBlockState(new BlockPos(x,y,16), ((x+y)%2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLUE_CONCRETE).getDefaultState());
            }
            // Emissive central backdrop avoids treating retained physical shadow/AO as failed transparency.
            w.setBlockState(BACKDROP,Blocks.GLOWSTONE.getDefaultState());
            // White front-wall neighbors touch the target and must reveal their interior faces.
            for (int x=-1;x<=1;x++) for(int y=80;y<=82;y++) w.setBlockState(new BlockPos(x,y,15), Blocks.WHITE_WOOL.getDefaultState());
            w.setBlockState(FRONT, Blocks.AIR.getDefaultState());
            p.teleport(w,.5,80,11.5,0,0);
            p.getInventory().selectedSlot=0; p.getInventory().setStack(0,new ItemStack(ModItems.BLEND_APPLICATOR)); p.currentScreenHandler.sendContentUpdates();
        });
        step("tool delivered", () -> client.setScreen(null), () -> joined() && client.player.getMainHandStack().isOf(ModItems.BLEND_APPLICATOR), 80, 600);
        capture("unobstructed-reference");
        server("restore ordinary solid oak", p -> p.getServerWorld().setBlockState(FRONT, Blocks.OAK_PLANKS.getDefaultState()));
        capture("solid-reference");
        server("loaded crafting recipe creates the applicator", p -> {
            var entry = p.getServerWorld().getRecipeManager().get(Identifier.of("blockblend","blend_applicator")).orElseThrow();
            require(entry.value() instanceof CraftingRecipe, "registered crafting recipe");
            var recipe = (CraftingRecipe) entry.value();
            var input = CraftingRecipeInput.create(1,3,List.of(new ItemStack(Items.FEATHER),new ItemStack(Items.COPPER_INGOT),new ItemStack(Items.STICK)));
            require(recipe.matches(input,p.getServerWorld()),"feather/copper/stick vertical recipe matches");
            var result = recipe.craft(input,p.getServerWorld().getRegistryManager());
            require(result.isOf(ModItems.BLEND_APPLICATOR)&&result.getCount()==1,"one applicator crafted through loaded recipe");
            require(!recipe.matches(CraftingRecipeInput.create(1,3,List.of(new ItemStack(Items.STICK),new ItemStack(Items.COPPER_INGOT),new ItemStack(Items.FEATHER))),p.getServerWorld()),"wrong ingredient order rejected");
        });
        step("pattern key cycles independently",()->press(GLFW.GLFW_KEY_N,1),()->BlendClientState.overlay==1&&BlendClientState.opacityStep==5&&BlendClientState.strengthStep==5,3,40);
        step("pattern key wraps all three patterns",()->press(GLFW.GLFW_KEY_N,2),()->BlendClientState.overlay==0,3,40);
        server("prepare unsupported targets",p->{
            p.getServerWorld().setBlockState(new BlockPos(3,81,12),Blocks.GLASS.getDefaultState());
            p.getServerWorld().setBlockState(new BlockPos(4,81,12),Blocks.OAK_SLAB.getDefaultState());
            p.getServerWorld().setBlockState(new BlockPos(5,81,12),Blocks.CHEST.getDefaultState());
            p.getServerWorld().setBlockState(new BlockPos(6,81,12),Blocks.SEA_LANTERN.getDefaultState());
            p.getServerWorld().setBlockState(GRASS, Blocks.GRASS_BLOCK.getDefaultState());
            for (int i = 0; i < SUPPORTED_BLOCKS.size(); i++) {
                p.getServerWorld().setBlockState(new BlockPos(9+i,81,12), SUPPORTED_BLOCKS.get(i).getDefaultState());
            }
        });
        step("unsupported target states reach client",()->{},()->client.world.getBlockState(new BlockPos(3,81,12)).isOf(Blocks.GLASS)&&client.world.getBlockState(new BlockPos(4,81,12)).isOf(Blocks.OAK_SLAB)&&client.world.getBlockState(new BlockPos(5,81,12)).isOf(Blocks.CHEST)&&client.world.getBlockState(new BlockPos(6,81,12)).isOf(Blocks.SEA_LANTERN)&&client.world.getBlockState(GRASS).isOf(Blocks.GRASS_BLOCK),3,300);
        step("supported target palette reaches client",()->{},()->{
            for (int i = 0; i < SUPPORTED_BLOCKS.size(); i++) {
                if (!client.world.getBlockState(new BlockPos(9+i,81,12)).isOf(SUPPORTED_BLOCKS.get(i))) return false;
            }
            return true;
        },3,300);
        check("simple cube and gallery palettes remain supported",()->{
            for (int i = 0; i < SUPPORTED_BLOCKS.size(); i++) {
                var pos = new BlockPos(9+i,81,12);
                require(BlendRenderer.supports(client.world.getBlockState(pos),pos),"supported palette "+SUPPORTED_BLOCKS.get(i));
            }
        });
        check("unsupported shape/entity/animation targets retain ordinary geometry",()->{
            for (int x=3;x<=7;x++) {
                var pos=new BlockPos(x,81,12);
                require(!BlendRenderer.supports(client.world.getBlockState(pos),pos),"unsupported target "+pos);
                require(!BlendRenderer.suppress(pos,client.world.getBlockState(pos)),"unsupported target not suppressed");
                var result=client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(pos),Direction.NORTH,pos,false));
                require(!result.isAccepted(),"ordinary unsupported apply rejected");
            }
        });
        server("saved unsupported grass stroke uses normal snapshot channel",p->{
            var state = BlendState.get(p.getServerWorld());
            require(state.set(GRASS,tag(0,0,0)),"saved unsupported stroke retained");
            var chunk = new ChunkPos(GRASS);
            ServerPlayNetworking.send(p,new ModNetworking.ChunkData(p.getServerWorld().getRegistryKey().getValue(),chunk,state.snapshotChunk(chunk)));
        });
        step("unsupported saved grass keeps vanilla mesh and visibility",()->{},()->{
            if (BlendClientCache.get(GRASS)==null) return false;
            require(!BlendRenderer.suppress(GRASS,client.world.getBlockState(GRASS))&&!BlendRenderer.opensVisibility(GRASS),
                "unsupported saved grass does not suppress geometry or open section visibility");
            return true;
        },20,300);
        step("opacity and strength controls are independent", () -> {
            require(BlendClientState.opacityStep==5 && BlendClientState.strengthStep==5, "fresh 50% defaults");
            press(GLFW.GLFW_KEY_PERIOD,1); press(GLFW.GLFW_KEY_LEFT_BRACKET,1);
        }, () -> BlendClientState.opacityStep==6 && BlendClientState.strengthStep==4,3,40);
        controls(0,0,0); apply(FRONT,tag(0,0,0));
        check("zero-opacity render eligibility published",()-> {
            boolean suppress=BlendRenderer.suppress(FRONT,client.world.getBlockState(FRONT)), open=BlendRenderer.opensVisibility(FRONT);
            event("render-eligibility","front suppress="+suppress+" opensVisibility="+open);
            require(suppress&&open,"eligible painted target suppresses vanilla mesh and opens visibility");
        });
        capture("opacity-0");
        check("zero opacity exposes exact central backdrop", () -> near("opacity-0","unobstructed-reference",.015));
        controls(0,0,10); apply(FRONT,tag(0,0,1)); capture("mode-0-opacity-100");
        check("multiply100 uses world backdrop", () -> equation("mode-0-opacity-100","unobstructed-reference","solid-reference",0,1,.065));
        controls(0,0,5); apply(FRONT,frontSaved); capture("opacity-50");
        check("half opacity exposes surface and backdrop", () -> {
            equation("opacity-50","unobstructed-reference","solid-reference",0,.5,.055);
            midpoint("opacity-50","opacity-0","mode-0-opacity-100",.025);
            require(distance("opacity-50","opacity-0")>.01 && distance("opacity-50","solid-reference")>.01,"half differs from backdrop and ordinary solid");
        });
        if (fabulous) {
            check("simulate missing captured depth at real render boundary",()->invalidDepthMode=1);
            capture("missing-depth-guard");
            check("missing-depth guard skips paint without drawing through scene",()->near("missing-depth-guard","unobstructed-reference",.015));
            check("restore normal captured depth",()->invalidDepthMode=0);
            capture("after-missing-depth");
            check("normal rendering resumes after missing depth",()->near("after-missing-depth","opacity-50",.015));
            check("simulate mismatched captured dimensions at real render boundary",()->invalidDepthMode=2);
            capture("mismatched-depth-guard");
            check("dimension guard skips invalid compositor",()->near("mismatched-depth-guard","unobstructed-reference",.015));
            check("restore captured dimensions",()->invalidDepthMode=0);
            capture("after-dimension-guard");
            check("normal rendering resumes after dimension mismatch",()->near("after-dimension-guard","opacity-50",.015));
        }
        for(int mode=1;mode<7;mode++) {
            int m=mode;
            controls(m,0,10); apply(FRONT,tag(m,0,1)); capture("mode-"+m+"-opacity-100");
            check("scene equation mode "+m, () -> equation("mode-"+m+"-opacity-100","unobstructed-reference","solid-reference",m,1,.075));
        }
        check("mode variation excludes HUD", () -> {
            Set<Long> hashes=new HashSet<>(); for(int m=0;m<7;m++) hashes.add(frames.get("mode-"+m+"-opacity-100").hash());
            require(hashes.size()>=4,"at least four distinct mode target regions"); event("visual-samples",hashes.size()+" distinct target ROI modes");
        });
        // Pattern opacity control remains independent when both are nonzero.
        controls(0,5,5); apply(FRONT,tag(0,.5f,.5f)); capture("pattern-50-opacity-50");
        controls(0,0,5); apply(FRONT,frontSaved);
        erase(FRONT); capture("erase-restores-solid");
        check("erase restores ordinary solid geometry", () -> near("erase-restores-solid","solid-reference",.015));
        apply(FRONT,frontSaved);
        server("foreground green object", p -> p.getServerWorld().setBlockState(OCCLUDER,Blocks.LIME_CONCRETE.getDefaultState()));
        capture("foreground-painted-behind");
        server("remove foreground to access tool target", p -> p.getServerWorld().setBlockState(OCCLUDER,Blocks.AIR.getDefaultState()));
        erase(FRONT);
        server("same foreground with ordinary solid behind", p -> p.getServerWorld().setBlockState(OCCLUDER,Blocks.LIME_CONCRETE.getDefaultState()));
        capture("foreground-solid-behind");
        check("foreground correctly occludes painted block", () -> near("foreground-painted-behind","foreground-solid-behind",.015));
        server("prepare air-separated overlapping painted cubes", p -> {
            p.getServerWorld().setBlockState(OCCLUDER,Blocks.AIR.getDefaultState());
            p.getServerWorld().setBlockState(FRONT,Blocks.AIR.getDefaultState());
            p.getServerWorld().setBlockState(BACKDROP,Blocks.AIR.getDefaultState());
            p.getServerWorld().setBlockState(REAR,Blocks.GLOWSTONE.getDefaultState());
            p.teleport(p.getServerWorld(),.5,80,12.5,0,0);
        });
        step("rear layer within ordinary tool reach",()->{},()->Math.abs(client.player.getZ()-12.5)<.01,20,300);
        controls(1,0,5); apply(REAR,rearSaved);
        server("return to reference camera after rear apply",p->p.teleport(p.getServerWorld(),.5,80,11.5,0,0));
        step("reference camera restored",()->{},()->Math.abs(client.player.getZ()-11.5)<.01,20,300);
        capture("overlap-rear-only");
        server("restore front cube before overlapping apply", p -> p.getServerWorld().setBlockState(FRONT,Blocks.OAK_PLANKS.getDefaultState()));
        capture("overlap-front-solid");
        controls(0,0,0); apply(FRONT,tag(0,0,0)); capture("overlap-front-0");
        check("zero front opacity exposes painted rear cube", () -> near("overlap-front-0","overlap-rear-only",.015));
        controls(0,0,5); apply(FRONT,frontSaved); capture("overlap-both-50");
        check("two painted layers compose far to near", () -> equation("overlap-both-50","overlap-rear-only","overlap-front-solid",0,.5,.055));
        server("world identity collision and opacity preserved", p -> {
            var frontState=p.getServerWorld().getBlockState(FRONT);var rearState=p.getServerWorld().getBlockState(REAR);
            require(frontState.isOf(Blocks.OAK_PLANKS)&&rearState.isOf(Blocks.GLOWSTONE),"original block identities retained");
            require(!frontState.getCollisionShape(p.getServerWorld(),FRONT).isEmpty()&&!rearState.getCollisionShape(p.getServerWorld(),REAR).isEmpty(),"original solid collisions retained");
            require(frontSaved.equals(BlendState.get(p.getServerWorld()).get(FRONT))&&rearSaved.equals(BlendState.get(p.getServerWorld()).get(REAR)),"both saved opacity tags exact");
        });
        server("leave fixture chunks", p -> p.teleport(p.getServerWorld(),1024.5,80,.5,0,0));
        step("chunk unload drops both opacity tags",()->{},()->client.player.getX()>1000&&BlendClientCache.get(FRONT)==null&&BlendClientCache.get(REAR)==null,100,1200);
        server("return to fixture",p->p.teleport(p.getServerWorld(),.5,80,11.5,0,0));
        step("chunk return synchronizes opacity",()->{},this::cachedBoth,60,1200);
        server("travel to Nether",p->{var w=p.server.getWorld(World.NETHER);w.setBlockState(new BlockPos(0,79,0),Blocks.BEDROCK.getDefaultState());p.teleport(w,.5,80,.5,0,0);});
        step("dimension clears opacity cache",()->{},()->client.world!=null&&client.world.getRegistryKey().equals(World.NETHER)&&BlendClientCache.get(FRONT)==null&&BlendClientCache.get(REAR)==null,80,1600);
        server("return from Nether",p->p.teleport(p.server.getOverworld(),.5,80,11.5,0,0));
        step("dimension return restores opacity",()->{},()->client.world!=null&&client.world.getRegistryKey().equals(World.OVERWORLD)&&cachedBoth(),80,1600);
        step("save and stop integrated server",()->{stopping=client.getServer();disconnect();},()->client.getServer()==null&&stopping.isStopped(),40,1200);
        step("reopen same disposable world",()->client.createIntegratedServerLoader().start(worldName,()->{}),this::joined,40,2400);
        step("reloaded opacity reaches client",()->client.setScreen(null),this::cachedBoth,60,1200);
        server("reloaded server opacity exact",p->require(frontSaved.equals(BlendState.get(p.getServerWorld()).get(FRONT))&&rearSaved.equals(BlendState.get(p.getServerWorld()).get(REAR)),"saved layer tags restored"));
        capture("after-world-restart");
        check("reopened world retains scene appearance",()->near("after-world-restart","overlap-both-50",.02));
        step("reload client resources",()->resourceReload=client.reloadResources(),()->{if(!resourceReload.isDone())return false;resourceReload.join();return client.getOverlay()==null;},3,1200);
        capture("after-resource-reload");
        check("resource reload preserves scene composition",()->near("after-resource-reload","after-world-restart",.02));
        server("vanilla blindness fog beyond painted cube",p->{p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS,1200,0,false,false));p.teleport(p.getServerWorld(),.5,80,6.5,0,0);});
        step("blindness and distant camera synchronized",()->{},()->client.player.hasStatusEffect(StatusEffects.BLINDNESS)&&Math.abs(client.player.getZ()-6.5)<.01,30,300);
        capture("fog-painted", 6.5);
        server("return within reach to erase fog target",p->p.teleport(p.getServerWorld(),.5,80,11.5,0,0));
        step("fog target within reach",()->{},()->Math.abs(client.player.getZ()-11.5)<.01,20,300);
        erase(FRONT);
        server("same distant fog camera with ordinary solid",p->p.teleport(p.getServerWorld(),.5,80,6.5,0,0));
        capture("fog-ordinary-solid", 6.5);
        check("fully fogged paint matches vanilla scene",()-> {
            near("fog-painted","fog-ordinary-solid",.015);
            require(distance("fog-painted","after-world-restart")>.03,"blindness actually changes target appearance");
        });
        server("clear fog and return to supported fixture",p->{p.removeStatusEffect(StatusEffects.BLINDNESS);p.teleport(p.getServerWorld(),.5,80,11.5,0,0);});
        step("clear fog synchronized",()->{},()->!client.player.hasStatusEffect(StatusEffects.BLINDNESS)&&Math.abs(client.player.getZ()-11.5)<.01,20,300);
        apply(FRONT,frontSaved);
        // Diagnostic only: translucent foreground is explicitly outside supported acceptance.
        capture("diagnostic-no-glass-opacity50");
        server("diagnostic glass foreground",p->p.getServerWorld().setBlockState(OCCLUDER,Blocks.GLASS.getDefaultState()));
        capture("diagnostic-glass-opacity50");
        server("diagnostic remove glass to access target",p->p.getServerWorld().setBlockState(OCCLUDER,Blocks.AIR.getDefaultState()));
        controls(0,0,0);apply(FRONT,tag(0,0,0));capture("diagnostic-no-glass-opacity0");
        server("diagnostic glass foreground at zero opacity",p->p.getServerWorld().setBlockState(OCCLUDER,Blocks.GLASS.getDefaultState()));
        capture("diagnostic-glass-opacity0");
        check("record glass diagnostic without qualifying translucent foreground",()->event("unqualified-diagnostic",
            "glass-vs-no-glass targetROI difference opacity50="+distance("diagnostic-glass-opacity50","diagnostic-no-glass-opacity50")
                +" opacity0="+distance("diagnostic-glass-opacity0","diagnostic-no-glass-opacity0")+"; inspect full frames; glass/water/particles remain unqualified"));
        server("restore supported fixture after glass diagnostic",p->p.getServerWorld().setBlockState(OCCLUDER,Blocks.AIR.getDefaultState()));
        controls(0,0,5);apply(FRONT,frontSaved);
        step("resize actual window",()->client.getWindow().setWindowedSize(1024,576),()->client.getFramebuffer().textureWidth==client.getWindow().getFramebufferWidth()&&client.getFramebuffer().textureHeight==client.getWindow().getFramebufferHeight()&&client.getWindow().getWidth()==1024,40,300);
        capture("resized-painted");
        controls(0,0,0);apply(FRONT,tag(0,0,0));capture("resized-zero");
        server("temporarily remove front block after resize",p->p.getServerWorld().setBlockState(FRONT,Blocks.AIR.getDefaultState()));
        capture("resized-air");
        check("resize preserves exact zero-opacity backdrop",()->near("resized-zero","resized-air",.015));
        server("restore resized solid reference",p->p.getServerWorld().setBlockState(FRONT,Blocks.OAK_PLANKS.getDefaultState()));
        controls(0,0,5);apply(FRONT,frontSaved);
        server("resized foreground object",p->p.getServerWorld().setBlockState(OCCLUDER,Blocks.LIME_CONCRETE.getDefaultState()));
        capture("resized-foreground-painted");
        server("remove resized foreground",p->p.getServerWorld().setBlockState(OCCLUDER,Blocks.AIR.getDefaultState()));
        erase(FRONT);
        server("resized foreground over ordinary solid",p->p.getServerWorld().setBlockState(OCCLUDER,Blocks.LIME_CONCRETE.getDefaultState()));
        capture("resized-foreground-solid");
        check("resize preserves foreground depth",()->near("resized-foreground-painted","resized-foreground-solid",.015));
        server("remove resized foreground for restoration",p->p.getServerWorld().setBlockState(OCCLUDER,Blocks.AIR.getDefaultState()));
        controls(0,0,5);apply(FRONT,frontSaved);
        step("restore original window size",()->client.getWindow().setWindowedSize(originalWidth,originalHeight),()->client.getWindow().getWidth()==originalWidth&&client.getWindow().getHeight()==originalHeight,40,300);
        capture("after-size-restoration");
        check("size restoration retains original composed scene",()->near("after-size-restoration","after-resource-reload",.02));
        enqueuePaintedBoundaries();
    }

    private void enqueuePaintedBoundaries() {
        server("construct touching different-paint boundary fixture", p -> {
            var world = p.getServerWorld();
            var state = BlendState.get(world);
            for (int x=-2;x<=2;x++) for (int y=80;y<=84;y++) for (int z=14;z<=18;z++) {
                var pos = new BlockPos(x,y,z);
                world.setBlockState(pos,Blocks.AIR.getDefaultState());
                state.remove(pos);
            }
            world.setBlockState(FRONT,Blocks.OAK_PLANKS.getDefaultState());
            world.setBlockState(BACKDROP,Blocks.GLOWSTONE.getDefaultState());
            world.setBlockState(REAR,Blocks.WHITE_CONCRETE.getDefaultState());
            require(state.set(FRONT,frontSaved)&&state.set(BACKDROP,rearSaved),"touching tags stored");
            synchronizeBoundaryChunks(p);
        });
        step("touching painted boundary spans loaded chunks",()->{},()->{
            if (!frontSaved.equals(BlendClientCache.get(FRONT)) || !rearSaved.equals(BlendClientCache.get(BACKDROP))) return false;
            require(paintedNeighbor(FRONT)&&paintedNeighbor(BACKDROP),"nonzero differing material and tag join exterior region");
            return true;
        },40,300);
        capture("touching-exterior-union");
        server("zero-opacity neighbor becomes a visual gap",p->{
            require(BlendState.get(p.getServerWorld()).set(BACKDROP,tag(1,0,0)),"zero neighbor stored");
            synchronizeBoundaryChunks(p);
        });
        step("zero neighbor does not hide shared boundary",()->{},()->{
            if (!tag(1,0,0).equals(BlendClientCache.get(BACKDROP))) return false;
            require(!paintedNeighbor(BACKDROP),"zero opacity exposes adjacent visible surface");
            return true;
        },30,300);
        capture("touching-back-zero");
        check("hidden shared rear plane contributes no extra layer",()->near("touching-exterior-union","touching-back-zero",.015));
        server("visible rear behind invisible touching front",p->{
            var state=BlendState.get(p.getServerWorld());
            require(state.set(FRONT,tag(0,0,0))&&state.set(BACKDROP,rearSaved),"front gap and visible rear stored");
            synchronizeBoundaryChunks(p);
        });
        step("front gap restores visible rear boundary",()->{},()->{
            if (!tag(0,0,0).equals(BlendClientCache.get(FRONT)) || !rearSaved.equals(BlendClientCache.get(BACKDROP))) return false;
            require(!paintedNeighbor(FRONT)&&paintedNeighbor(BACKDROP),"gap and nonzero exterior decisions");
            return true;
        },30,300);
        capture("touching-front-zero");
        server("air reference for emissive rear boundary",p->p.getServerWorld().setBlockState(FRONT,Blocks.AIR.getDefaultState()));
        capture("touching-front-air");
        check("zero front gap exposes emissive rear without hidden planes",()->near("touching-front-zero","touching-front-air",.015));
        server("restore touching exterior after gap",p->{
            p.getServerWorld().setBlockState(FRONT,Blocks.OAK_PLANKS.getDefaultState());
            require(BlendState.get(p.getServerWorld()).set(FRONT,frontSaved),"front restored");
            synchronizeBoundaryChunks(p);
        });
        step("restored front boundary reaches client",()->{},()->frontSaved.equals(BlendClientCache.get(FRONT))&&paintedNeighbor(FRONT),30,300);
        erase(FRONT);
        capture("touching-erased-front");
        server("ordinary rear reference behind erased front",p->{
            BlendState.get(p.getServerWorld()).remove(BACKDROP);
            synchronizeBoundaryChunks(p);
        });
        step("erased boundaries remain ordinary",()->{},()->BlendClientCache.get(FRONT)==null&&BlendClientCache.get(BACKDROP)==null,30,300);
        capture("touching-ordinary-reference");
        check("erase restores opaque foreground boundary",()->near("touching-erased-front","touching-ordinary-reference",.015));
        controls(0,0,5); apply(FRONT,frontSaved);
        server("repaint touching rear",p->{
            require(BlendState.get(p.getServerWorld()).set(BACKDROP,rearSaved),"rear repainted");
            synchronizeBoundaryChunks(p);
        });
        step("repainted union synchronized",()->{},()->rearSaved.equals(BlendClientCache.get(BACKDROP))&&paintedNeighbor(BACKDROP),30,300);
        capture("touching-repainted-union");
        check("repaint restores the same exterior region",()->near("touching-repainted-union","touching-exterior-union",.015));
        server("nonzero painted neighbors in all six directions",p->{
            var world=p.getServerWorld(); var state=BlendState.get(world);
            for (Direction side:Direction.values()) {
                var pos=FRONT.offset(side);
                world.setBlockState(pos,(side.ordinal()%2==0?Blocks.OAK_PLANKS:Blocks.PINK_CONCRETE).getDefaultState());
                require(state.set(pos,tag(side.ordinal(),side.ordinal()/10f,(side.ordinal()+1)/10f)),"directional paint stored");
            }
            synchronizeBoundaryChunks(p);
        });
        step("all material and paint boundaries are joined",()->{},()->{
            for (Direction side:Direction.values()) {
                var pos=FRONT.offset(side);
                if (!tag(side.ordinal(),side.ordinal()/10f,(side.ordinal()+1)/10f).equals(BlendClientCache.get(pos))) return false;
                require(paintedNeighbor(pos),"shared plane hidden in direction "+side);
            }
            return true;
        },40,300);
        server("missing zero unsupported and replaced neighbors",p->{
            var world=p.getServerWorld(); var state=BlendState.get(world);
            state.remove(FRONT.down());
            require(state.set(FRONT.east(),tag(0,0,0)),"zero boundary stored");
            world.setBlockState(FRONT.up(),Blocks.GRASS_BLOCK.getDefaultState());
            world.setBlockState(FRONT.west(),Blocks.AIR.getDefaultState());
            synchronizeBoundaryChunks(p);
        });
        step("invalid neighbors retain exposed visible boundaries",()->{},()->{
            if (BlendClientCache.get(FRONT.down())!=null || !tag(0,0,0).equals(BlendClientCache.get(FRONT.east()))
                || !client.world.getBlockState(FRONT.up()).isOf(Blocks.GRASS_BLOCK)
                || !client.world.getBlockState(FRONT.west()).isAir()) return false;
            for (Direction side:List.of(Direction.DOWN,Direction.EAST,Direction.UP,Direction.WEST)) {
                require(!paintedNeighbor(FRONT.offset(side)),"invalid or gap boundary exposed "+side);
            }
            require(paintedNeighbor(FRONT.north())&&paintedNeighbor(FRONT.south()),"remaining nonzero boundaries still joined");
            return true;
        },40,300);
        server("oblique mixed-paint solid sculpture fixture",p->{
            var world=p.getServerWorld(); var state=BlendState.get(world);
            for (int x=-1;x<=1;x++) for (int y=81;y<=83;y++) for (int z=15;z<=16;z++) {
                var pos=new BlockPos(x,y,z); boolean alternate=(x+y+z)%2==0;
                world.setBlockState(pos,(alternate?Blocks.ORANGE_CONCRETE:Blocks.PINK_CONCRETE).getDefaultState());
                require(state.set(pos,alternate?tag(1,0,.7f):tag(0,0,.6f)),"oblique surface stored");
            }
            synchronizeBoundaryChunks(p);
            p.getAbilities().flying=true; p.sendAbilitiesUpdate();
            p.teleport(world,-3.5,80,11.5,-45,-8);
        });
        step("oblique sculpture camera synchronized",()->{},()->Math.abs(client.player.getX()+3.5)<.01&&Math.abs(client.player.getZ()-11.5)<.01,40,300);
        capture("oblique-connected-painted-sculpture",new View(-3.5,80,11.5,-45,-8));
    }

    private void synchronizeBoundaryChunks(ServerPlayerEntity player) {
        var state=BlendState.get(player.getServerWorld());
        for (int x=-1;x<=0;x++) for (int z=0;z<=1;z++) {
            var chunk=new ChunkPos(x,z);
            ServerPlayNetworking.send(player,new ModNetworking.ChunkData(player.getServerWorld().getRegistryKey().getValue(),chunk,state.snapshotChunk(chunk)));
        }
    }

    private boolean paintedNeighbor(BlockPos pos) {
        try {
            var method=BlendRenderer.class.getDeclaredMethod("hasVisiblePaintedNeighbor",BlockPos.class);
            method.setAccessible(true);
            return (boolean)method.invoke(null,pos);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("actual painted face-selection predicate",e);
        }
    }

    private void controls(int mode,int strength,int opacity) {
        step("controls mode="+mode+" pattern="+strength+" opacity="+opacity,()->{
            press(GLFW.GLFW_KEY_B,(mode-BlendClientState.mode+7)%7);
            press(strength>=BlendClientState.strengthStep?GLFW.GLFW_KEY_RIGHT_BRACKET:GLFW.GLFW_KEY_LEFT_BRACKET,Math.abs(strength-BlendClientState.strengthStep));
            press(opacity>=BlendClientState.opacityStep?GLFW.GLFW_KEY_PERIOD:GLFW.GLFW_KEY_COMMA,Math.abs(opacity-BlendClientState.opacityStep));
        },()->BlendClientState.mode==mode&&BlendClientState.strengthStep==strength&&BlendClientState.opacityStep==opacity,3,40);
    }
    private void apply(BlockPos pos,BlendTag expected) {
        step("ordinary apply "+pos+" "+expected,()->interact(pos,false),()->expected.equals(BlendClientCache.get(pos)),20,300);
        server("server tag approved "+pos,p->require(expected.equals(BlendState.get(p.getServerWorld()).get(pos)),"exact server tag"));
    }
    private void erase(BlockPos pos) {
        step("ordinary sneak erase "+pos,()->interact(pos,true),()->BlendClientCache.get(pos)==null,20,300);
        server("server erase approved",p->require(BlendState.get(p.getServerWorld()).get(pos)==null,"erased server tag"));
    }
    private void interact(BlockPos pos,boolean erase) {
        boolean old=client.player.input.sneaking;client.player.input.sneaking=erase;
        try{require(client.player.isSneaking()==erase,"real client shift input");require(client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(.5,81.62,pos.getZ()),Direction.NORTH,pos,false)).isAccepted(),"ordinary interaction accepted");}
        finally{client.player.input.sneaking=old;}
    }
    private void capture(String name) {
        capture(name, 11.5);
    }
    private void capture(String name, double expectedZ) {
        capture(name, new View(.5, 80, expectedZ, 0, 0));
    }
    private void capture(String name, View view) {
        step("frame "+name,()->{
            captureView = view;
            event("camera-before-capture", name+" | "+capturePose());
            restoreCaptureRotation(name);
        },()->{
            var camera = client.gameRenderer.getCamera();
            require(Math.abs(client.player.getX()-view.x())<.01 && Math.abs(client.player.getY()-view.y())<.01
                && Math.abs(client.player.getZ()-view.z())<.01,"fixture position stable for "+name+" | "+capturePose());
            require(Math.abs(camera.getYaw()-view.yaw())<.001 && Math.abs(camera.getPitch()-view.pitch())<.001,
                "fixture camera rotation stable for "+name+" | "+capturePose());
            require(Math.abs(camera.getPos().x-view.x())<.01 && Math.abs(camera.getPos().y-(view.y()+1.62))<.01
                && Math.abs(camera.getPos().z-view.z())<.01 && client.options.getFov().getValue()==70,
                "fixture camera position/FOV stable for "+name+" | "+capturePose());
            event("camera-at-capture", name+" | "+capturePose());
            try(NativeImage image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                int x0=image.getWidth()*48/100,x1=image.getWidth()*52/100,y0=image.getHeight()*48/100,y1=image.getHeight()*52/100;
                int[] pixels=new int[(x1-x0)*(y1-y0)];int i=0;long hash=0xcbf29ce484222325L;
                for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){int color=image.getColor(x,y);pixels[i++]=color;hash=(hash^Integer.toUnsignedLong(color))*0x100000001b3L;}
                frames.put(name,new Frame(pixels,hash));event("frame",name+" | targetROI="+x0+","+y0+","+x1+","+y1+" | hash="+Long.toUnsignedString(hash));
            }
            ScreenshotRecorder.saveScreenshot(client.runDirectory,worldName+"-"+name+".png",client.getFramebuffer(),message->{event("screenshot",message.getString());screenshots++;});return true;
        },60,80);
    }
    private void restoreCaptureRotation(String name) {
        if (client.player == null) return;
        if (Math.abs(client.player.getYaw()-captureView.yaw())>.001 || Math.abs(client.player.getPitch()-captureView.pitch())>.001) {
            event("camera-correction", name+" | "+capturePose());
        }
        // Resource overlays can release/regrab the mouse. Keep the fixture view
        // fixed throughout its settling period instead of comparing different rays.
        client.player.setYaw(captureView.yaw());
        client.player.setPitch(captureView.pitch());
        client.player.prevYaw = captureView.yaw();
        client.player.prevPitch = captureView.pitch();
    }
    private String capturePose() {
        var player = client.player;
        var camera = client.gameRenderer.getCamera();
        return "player="+player.getX()+","+player.getY()+","+player.getZ()
            +" yaw="+player.getYaw()+" pitch="+player.getPitch()
            +" camera="+camera.getPos()+" yaw="+camera.getYaw()+" pitch="+camera.getPitch()
            +" fov="+client.options.getFov().getValue()
            +" framebuffer="+client.getFramebuffer().textureWidth+"x"+client.getFramebuffer().textureHeight;
    }
    private void check(String name,Runnable assertion){step(name,assertion,()->true,1,10);}
    private double distance(String a,String b){var aa=frames.get(a).pixels();var bb=frames.get(b).pixels();require(aa.length==bb.length,"stable framebuffer dimensions");double sum=0;for(int i=0;i<aa.length;i++)for(int c=0;c<3;c++)sum+=Math.abs(channel(aa[i],c)-channel(bb[i],c));return sum/(aa.length*3);}
    private void near(String a,String b,double tolerance){double error=distance(a,b);event("pixel-error",a+" vs "+b+" | mean="+error+" tolerance="+tolerance);require(error<=tolerance,"matching target pixels "+a+" vs "+b+" error="+error);}
    private void midpoint(String actual,String zero,String full,double tolerance){var a=frames.get(actual).pixels();var b=frames.get(zero).pixels();var s=frames.get(full).pixels();double sum=0;for(int i=0;i<a.length;i++)for(int c=0;c<3;c++)sum+=Math.abs(channel(a[i],c)-(channel(b[i],c)+channel(s[i],c))/2);double error=sum/(a.length*3);event("pixel-error","opacity midpoint | mean="+error);require(error<=tolerance,"half opacity midpoint error="+error);}
    private void equation(String actual,String backdrop,String surface,int mode,double opacity,double tolerance){var a=frames.get(actual).pixels();var b=frames.get(backdrop).pixels();var s=frames.get(surface).pixels();double sum=0;for(int i=0;i<a.length;i++)for(int c=0;c<3;c++){double base=channel(b[i],c),paint=channel(s[i],c),expected=base+(blend(base,paint,mode)-base)*opacity;sum+=Math.abs(channel(a[i],c)-expected);}double error=sum/(a.length*3);event("equation-error","mode="+mode+" opacity="+opacity+" mean="+error+" tolerance="+tolerance);require(error<=tolerance,"scene equation mode="+mode+" error="+error);}
    private static double channel(int pixel,int c){return((pixel>>>(c*8))&255)/255d;}
    private static double blend(double b,double s,int mode){return switch(mode){case 0->b*s;case 1->1-(1-b)*(1-s);case 2->b<=.5?2*b*s:1-2*(1-b)*(1-s);case 3->s<=.5?b-(1-2*s)*b*(1-b):b+(2*s-1)*((b<=.25?((16*b-12)*b+4)*b:Math.sqrt(b))-b);case 4->s<=.5?2*b*s:1-2*(1-b)*(1-s);case 5->b==0?0:s==1?1:Math.min(1,b/(1-s));case 6->b==1?1:s==0?0:1-Math.min(1,(1-b)/s);default->throw new AssertionError("mode");};}
    private void server(String name,Consumer<ServerPlayerEntity> action){step(name,()->{UUID id=client.player.getUuid();var server=client.getServer();serverWork=CompletableFuture.runAsync(()->action.accept(Objects.requireNonNull(server.getPlayerManager().getPlayer(id))),server);},()->{if(!serverWork.isDone())return false;serverWork.join();return true;},3,1200);}
    private void step(String name,Runnable action,BooleanSupplier ready,int delay,int timeout){steps.add(new Step(name,action,ready,delay,timeout));}
    private boolean joined(){return client.player!=null&&client.world!=null&&client.getServer()!=null&&client.currentScreen==null;}
    private boolean cachedBoth(){return frontSaved.equals(BlendClientCache.get(FRONT))&&rearSaved.equals(BlendClientCache.get(REAR));}
    private static BlendTag tag(int mode,float strength,float opacity){return new BlendTag(mode,strength,OverlayCatalog.IDS.getFirst(),opacity);}
    private static void press(int code,int count){for(int i=0;i<count;i++)KeyBinding.onKeyPressed(InputUtil.fromKeyCode(code,0));}
    private static void require(boolean condition,String detail){if(!condition)throw new AssertionError(detail);}
    private synchronized void event(String kind,String detail){if(receipt==null)return;String line="{\"time\":\""+Instant.now()+"\",\"kind\":\""+kind+"\",\"detail\":\""+detail.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"}\n";try{Files.writeString(receipt,line,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
    private void disconnect(){if(client.world!=null)client.world.disconnect();client.disconnect(new TitleScreen());}
    private void finish(){event("PASS",passed+" steps; "+screenshots+" screenshots; actual backdrop/occlusion/persistence gates passed; human inspection and account launcher remain separate");finished=true;disconnect();client.scheduleStop();}
}
