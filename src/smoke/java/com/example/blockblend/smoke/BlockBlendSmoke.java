package com.example.blockblend.smoke;

import com.example.blockblend.client.BlendClientCache;
import com.example.blockblend.client.BlendClientState;
import com.example.blockblend.data.BlendState;
import com.example.blockblend.data.BlendTag;
import com.example.blockblend.data.OverlayCatalog;
import com.example.blockblend.registry.ApplyBlendC2SPayload;
import com.example.blockblend.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.tutorial.TutorialStep;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Dev-only mod: no external server, existing saves, credentials, or production seams. */
public final class BlockBlendSmoke implements ClientModInitializer {
    private static final BlockPos TARGET = new BlockPos(0, 81, 2);
    private static final BlockPos FAR = new BlockPos(0, 81, 20);
    private record Step(String name, Runnable action, BooleanSupplier ready, int delay, int timeout) {}
    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private final List<String> assertions = new ArrayList<>();
    private final Map<String, Long> frames = new HashMap<>();
    private MinecraftClient client;
    private Path receipt;
    private String worldName;
    private Step active;
    private int elapsed, totalTicks;
    private boolean started, finished;
    private CompletableFuture<Void> serverWork;
    private IntegratedServer stoppingServer;
    private BlendTag retained;
    private long baseline;
    private volatile int screenshots;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("blockblend.smoke")) return;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient mc) {
        if (finished) return;
        client = mc;
        try {
            if (!started) {
                if (!mc.isFinishedLoading() || mc.getOverlay() != null) return;
                // A fresh unique folder on every run; never reuse or overwrite a save.
                Path run = mc.runDirectory.toPath().toAbsolutePath().normalize();
                String configured = System.getProperty("blockblend.smoke.runDir");
                require(configured != null && run.equals(Path.of(configured).toAbsolutePath().normalize()), "explicit isolated smoke run directory");
                worldName = "blockblend-smoke-" + UUID.randomUUID();
                receipt = Path.of(System.getProperty("blockblend.smoke.evidence",run.resolveSibling("evidence").toString())).resolve(worldName + ".jsonl");
                Files.createDirectories(receipt.getParent());
                event("start", worldName + " | dev runtime; no authenticated launcher qualification");
                mc.options.pauseOnLostFocus = false;
                mc.options.getViewDistance().setValue(5);
                mc.options.getSimulationDistance().setValue(5);
                mc.options.getBobView().setValue(false);
                mc.options.setAccessibilityOnboarded();
                mc.getTutorialManager().setStep(TutorialStep.NONE);
                started = true;
                enqueue();
                mc.createIntegratedServerLoader().createAndStart(worldName,
                    new LevelInfo("BlockBlend smoke", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(178123L, false, false),
                    registries -> registries.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder(),
                    new TitleScreen());
            }
            if (++totalTicks > 18000) throw new AssertionError("whole harness exceeded 15 minutes");
            if (active == null) {
                active = steps.poll();
                if (active == null) { finish(); return; }
                elapsed = 0;
                event("step", active.name());
                active.action().run();
            }
            elapsed++;
            if (elapsed >= active.delay() && active.ready().getAsBoolean()) {
                assertions.add(active.name()); event("pass", active.name()); active = null;
            } else if (elapsed > active.timeout()) throw new AssertionError("Timed out: " + active.name());
        } catch (Throwable failure) {
            finished = true;
            try { event("FAIL", failure.toString()); } catch (Exception ignored) {}
            failure.printStackTrace();
            mc.scheduleStop();
        }
    }

    private void enqueue() {
        step("client and integrated server joined", () -> {}, this::joined, 20, 2400);
        serverStep("fixture and applicator granted", player -> {
            var world = player.getServerWorld();
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, player.server);
            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, player.server);
            world.setTimeOfDay(6000);
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 4; z++)
                world.setBlockState(new BlockPos(x, 79, z), Blocks.SMOOTH_STONE.getDefaultState());
            for (int x = -1; x <= 1; x++) for (int y = 80; y <= 82; y++)
                world.setBlockState(new BlockPos(x, y, 2), Blocks.WHITE_WOOL.getDefaultState());
            world.setBlockState(TARGET, Blocks.OAK_PLANKS.getDefaultState());
            world.setBlockState(FAR, Blocks.WHITE_WOOL.getDefaultState());
            player.teleport(world, .5, 80, -1.5, 0, 0);
            player.getInventory().selectedSlot = 0;
            player.getInventory().setStack(0, new ItemStack(ModItems.BLEND_APPLICATOR));
            player.currentScreenHandler.sendContentUpdates();
        });
        step("applicator reaches client inventory", () -> client.setScreen(null), () -> joined()
            && client.player.getMainHandStack().isOf(ModItems.BLEND_APPLICATOR)
            && client.world.getBlockState(TARGET).isOf(Blocks.OAK_PLANKS), 40, 600);
        step("B N brackets control production state", () -> {
            require(BlendClientState.mode == 0 && BlendClientState.overlay == 0 && BlendClientState.strengthStep == 5,
                "fresh client defaults");
            press(GLFW.GLFW_KEY_B, 1); press(GLFW.GLFW_KEY_N, 1); press(GLFW.GLFW_KEY_RIGHT_BRACKET, 1);
        }, () -> BlendClientState.mode == 1 && BlendClientState.overlay == 1 && BlendClientState.strengthStep == 6, 3, 40);
        controls(0, 0, 0);
        apply("zero strength", tag(0, 0, 0));
        shot("baseline-strength-0", false);
        controls(0, 0, 5);
        apply("half strength", tag(0, 0, .5f));
        shot("multiply-noise-strength-50", true);
        for (int mode = 0; mode < 7; mode++) for (int overlay = 0; overlay < 3; overlay++) {
            controls(mode, overlay, 10);
            apply("mode " + mode + " overlay " + overlay, tag(mode, overlay, 1));
            shot("mode-" + mode + "-overlay-" + overlay + "-strength-100", true);
        }
        step("rendered modes and textures are distinct", () -> {
            var modes = new HashSet<Long>();
            var textures = new HashSet<Long>();
            for (int m = 0; m < 7; m++) modes.add(frames.get("mode-" + m + "-overlay-0-strength-100"));
            for (int o = 0; o < 3; o++) textures.add(frames.get("mode-0-overlay-" + o + "-strength-100"));
            require(modes.size() >= 4, "at least four distinct rendered mode samples");
            require(textures.size() == 3, "three distinct rendered overlay samples");
            event("visual-samples", modes.size() + " distinct modes on noise; " + textures.size() + " distinct textures on multiply");
        }, () -> true, 1, 10);
        retained = tag(6, 2, 1);
        step("ordinary sneak interaction erases", () -> interact(true), () -> BlendClientCache.get(TARGET) == null, 20, 200);
        serverStep("erase persisted on server", p -> require(BlendState.get(p.getServerWorld()).get(TARGET) == null, "server erase"));
        apply("ordinary interaction reapplies", retained);
        step("out of reach payload sent", () -> ClientPlayNetworking.send(new ApplyBlendC2SPayload(World.OVERWORLD.getValue(), FAR, retained)), () -> true, 20, 40);
        serverStep("out of reach request rejected", p -> require(BlendState.get(p.getServerWorld()).get(FAR) == null, "far target unchanged"));
        serverStep("tool removed for rejection test", p -> {
            p.getInventory().setStack(0, ItemStack.EMPTY); p.currentScreenHandler.sendContentUpdates();
        });
        step("no tool request sent", () -> ClientPlayNetworking.send(new ApplyBlendC2SPayload(World.OVERWORLD.getValue(), TARGET, null)), () -> true, 20, 40);
        serverStep("no tool erase rejected", p -> {
            require(retained.equals(BlendState.get(p.getServerWorld()).get(TARGET)), "protected edit survives no tool");
            p.getInventory().setStack(0, new ItemStack(ModItems.BLEND_APPLICATOR)); p.currentScreenHandler.sendContentUpdates();
        });
        step("stale dimension erase sent", () -> ClientPlayNetworking.send(new ApplyBlendC2SPayload(World.NETHER.getValue(), TARGET, null)), () -> true, 20, 40);
        serverStep("stale dimension request rejected", p -> require(retained.equals(BlendState.get(p.getServerWorld()).get(TARGET)), "wrong dimension unchanged"));
        serverStep("leave tracked fixture chunk", p -> p.teleport(p.getServerWorld(), 1024.5, 80, .5, 0, 0));
        step("chunk unload removes client edit", () -> {}, () -> client.player.getX() > 1000 && BlendClientCache.get(TARGET) == null, 100, 1200);
        serverStep("return to saved fixture chunk", p -> p.teleport(p.getServerWorld(), .5, 80, -1.5, 0, 0));
        step("chunk reload resynchronizes edit", () -> {}, () -> retained.equals(BlendClientCache.get(TARGET)), 60, 1200);
        serverStep("travel to Nether", p -> {
            var world = p.server.getWorld(World.NETHER);
            world.setBlockState(new BlockPos(0, 79, 0), Blocks.BEDROCK.getDefaultState());
            p.teleport(world, .5, 80, .5, 0, 0);
        });
        step("dimension change clears old edit", () -> {}, () -> client.world != null
            && client.world.getRegistryKey().equals(World.NETHER) && BlendClientCache.get(TARGET) == null, 80, 1600);
        serverStep("return from Nether", p -> p.teleport(p.server.getOverworld(), .5, 80, -1.5, 0, 0));
        step("dimension return resynchronizes edit", () -> {}, () -> client.world != null
            && client.world.getRegistryKey().equals(World.OVERWORLD) && retained.equals(BlendClientCache.get(TARGET)), 80, 1600);
        step("save and disconnect disposable world", () -> {
            stoppingServer = client.getServer();
            disconnect();
        }, () -> client.getServer() == null && stoppingServer.isStopped(), 40, 1200);
        step("restart same disposable world", () -> client.createIntegratedServerLoader().start(worldName, () -> {}), this::joined, 40, 2400);
        step("saved edit reaches client after restart", () -> client.setScreen(null), () -> retained.equals(BlendClientCache.get(TARGET)), 60, 1200);
        serverStep("saved edit reloaded on server", p -> require(retained.equals(BlendState.get(p.getServerWorld()).get(TARGET)), "persistent state survives restart"));
        shot("after-world-restart", true);
    }

    private void controls(int mode, int overlay, int strength) {
        step("select controls " + mode + "/" + overlay + "/" + strength, () -> {
            press(GLFW.GLFW_KEY_B, (mode - BlendClientState.mode + 7) % 7);
            press(GLFW.GLFW_KEY_N, (overlay - BlendClientState.overlay + 3) % 3);
            press(strength >= BlendClientState.strengthStep ? GLFW.GLFW_KEY_RIGHT_BRACKET : GLFW.GLFW_KEY_LEFT_BRACKET,
                Math.abs(strength - BlendClientState.strengthStep));
        }, () -> BlendClientState.mode == mode && BlendClientState.overlay == overlay && BlendClientState.strengthStep == strength, 3, 40);
    }

    private void apply(String label, BlendTag expected) {
        step("interaction synchronized: " + label, () -> interact(false), () -> expected.equals(BlendClientCache.get(TARGET)), 20, 200);
        serverStep("server approved: " + label, p -> require(expected.equals(BlendState.get(p.getServerWorld()).get(TARGET)), "authoritative tag matches"));
    }

    private void interact(boolean erase) {
        // ClientPlayerEntity.isSneaking() reads Input.sneaking, not Entity's flag.
        boolean oldSneaking = client.player.input.sneaking;
        client.player.input.sneaking = erase;
        try {
            require(client.player.isSneaking() == erase, "client shift input representation");
            var result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                new BlockHitResult(new Vec3d(.5, 81.62, 2), Direction.NORTH, TARGET, false));
            require(result.isAccepted(), "normal interactBlock accepted");
        } finally { client.player.input.sneaking = oldSneaking; }
    }

    private void shot(String name, boolean expectDifference) {
        step("render and screenshot: " + name, () -> {}, () -> {
            try (NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                long hash = 0xcbf29ce484222325L;
                for (int y = image.getHeight() * 2 / 5; y < image.getHeight() * 3 / 5; y++)
                    for (int x = image.getWidth() * 2 / 5; x < image.getWidth() * 3 / 5; x++)
                        hash = (hash ^ Integer.toUnsignedLong(image.getColor(x, y))) * 0x100000001b3L;
                if (!expectDifference) baseline = hash;
                else require(hash != baseline, "rendered center pixels differ from zero strength");
                frames.put(name, hash);
                event("frame", name + " | centerPixelHash=" + Long.toUnsignedString(hash));
            }
            ScreenshotRecorder.saveScreenshot(client.runDirectory, worldName + "-" + name + ".png", client.getFramebuffer(),
                message -> { try { event("screenshot", message.getString()); screenshots++; } catch (Exception e) { e.printStackTrace(); } });
            return true;
        }, 40, 60);
    }

    private void serverStep(String name, Consumer<ServerPlayerEntity> operation) {
        step(name, () -> {
            UUID playerId = client.player.getUuid();
            var server = client.getServer();
            serverWork = CompletableFuture.runAsync(() -> operation.accept(Objects.requireNonNull(server.getPlayerManager().getPlayer(playerId))), server);
        }, () -> { if (!serverWork.isDone()) return false; serverWork.join(); return true; }, 3, 1200);
    }

    private void step(String name, Runnable action, BooleanSupplier ready, int delay, int timeout) {
        steps.add(new Step(name, action, ready, delay, timeout));
    }
    private boolean joined() { return client.player != null && client.world != null && client.getServer() != null && client.currentScreen == null; }
    private static BlendTag tag(int mode, int overlay, float strength) { return new BlendTag(mode, strength, OverlayCatalog.IDS.get(overlay)); }
    private static void press(int code, int count) { for (int i = 0; i < count; i++) KeyBinding.onKeyPressed(InputUtil.fromKeyCode(code, 0)); }
    private static void require(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
    private synchronized void event(String kind, String detail) {
        if (receipt == null) return;
        String line = "{\"time\":\"" + Instant.now() + "\",\"kind\":\"" + kind + "\",\"detail\":\""
            + detail.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"}\n";
        try { Files.writeString(receipt, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    private void finish() throws java.io.IOException {
        event("PASS", assertions.size() + " steps; " + screenshots + " screenshots; visual inspection and authenticated launcher use remain separate");
        finished = true;
        disconnect();
        client.scheduleStop();
    }
    private void disconnect() {
        // Match vanilla's Disconnect button: close the connection before waiting for server stop.
        if (client.world != null) client.world.disconnect();
        client.disconnect(new TitleScreen());
    }
}
