package com.example.blockblend.client.render;

import com.example.blockblend.BlockBlendMod;
import com.example.blockblend.client.BlendClientCache;
import com.example.blockblend.data.BlendTag;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import java.util.*;

/** Replaces eligible opaque meshes, then composites patterned front faces against real scene color. */
public final class BlendRenderer {
    private record Key(Identifier sprite, float strength, Identifier pattern) {}
    private record Entry(BlockState state, BlendTag tag) {}
    private record Face(BlockPos pos, Entry entry, BakedQuad quad, double depth) {}
    private static final Map<Key, Identifier> TEXTURES = new HashMap<>();
    private static final Set<Key> FAILED_TEXTURES = new HashSet<>();
    private static final Map<Identifier, Boolean> OPAQUE_SPRITES = new HashMap<>();
    // Only the main/render thread constructs eligibility. Chunk workers only read this immutable map.
    private static volatile Map<BlockPos, Entry> eligible = Map.of();
    private static ShaderProgram shader;
    private static int serial;
    private static int backdropTexture;
    private static int backdropWidth, backdropHeight;
    private static SimpleFramebuffer fabulousScene;
    private static boolean opaqueDepthReady;

    public static void init() {
        CoreShaderRegistrationCallback.EVENT.register(registry ->
            registry.register(Identifier.of("blockblend", "scene_blend"), VertexFormats.POSITION_TEXTURE_COLOR,
                program -> { shader = program; refreshEligibility(); }));
        WorldRenderEvents.START.register(context -> opaqueDepthReady = false);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(BlendRenderer::preserveOpaqueDepth);
        WorldRenderEvents.LAST.register(BlendRenderer::render);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            public Identifier getFabricId() { return Identifier.of("blockblend", "composed_textures"); }
            public void reload(ResourceManager manager) { clear(); }
        });
    }

    public static void clear() {
        RenderSystem.assertOnRenderThread();
        var manager = MinecraftClient.getInstance().getTextureManager();
        for (var id : TEXTURES.values()) manager.destroyTexture(id);
        TEXTURES.clear();
        FAILED_TEXTURES.clear();
        OPAQUE_SPRITES.clear();
        if (backdropTexture != 0) {
            GL11.glDeleteTextures(backdropTexture);
            backdropTexture = 0;
        }
        if (fabulousScene != null) {
            fabulousScene.delete();
            fabulousScene = null;
        }
        opaqueDepthReady = false;
        replaceEligibility(Map.of());
        // Shader lifetime belongs to GameRenderer/CoreShaderRegistrationCallback.
    }

    /** Worker-safe: no singleton, client world, model, texture or resource access. */
    public static boolean suppress(BlockPos pos, BlockState state) {
        Entry entry = eligible.get(pos);
        return entry != null && entry.state() == state;
    }

    public static boolean opensVisibility(BlockPos pos) { return eligible.containsKey(pos); }

    /** Runs only on the client thread. Unsupported/unreadable resources retain vanilla geometry. */
    public static void refreshEligibility() {
        RenderSystem.assertOnRenderThread();
        var client = MinecraftClient.getInstance();
        var next = new HashMap<BlockPos, Entry>();
        Set<Key> neededTextures = new HashSet<>();
        if (client.world != null && shader != null) {
            BlendClientCache.snapshot().forEach((pos, tag) -> {
                if (!client.world.isChunkLoaded(pos)) return;
                BlockState state = client.world.getBlockState(pos);
                if (!supports(state, pos)) return;
                var model = client.getBlockRenderManager().getModel(state);
                boolean ready = true;
                for (Direction face : Direction.values()) {
                    for (var quad : model.getQuads(state, face, Random.create(state.getRenderingSeed(pos)))) {
                        neededTextures.add(textureKey(quad.getSprite(), tag));
                        if (texture(quad.getSprite(), tag) == null) ready = false;
                    }
                }
                for (var quad : model.getQuads(state, null, Random.create(state.getRenderingSeed(pos)))) {
                    neededTextures.add(textureKey(quad.getSprite(), tag));
                    if (texture(quad.getSprite(), tag) == null) ready = false;
                }
                if (ready) next.put(pos, new Entry(state, tag));
            });
        }
        replaceEligibility(Map.copyOf(next));
        var textures = TEXTURES.entrySet().iterator();
        while (textures.hasNext()) {
            var texture = textures.next();
            if (!neededTextures.contains(texture.getKey())) {
                client.getTextureManager().destroyTexture(texture.getValue());
                textures.remove();
            }
        }
        FAILED_TEXTURES.retainAll(neededTextures);
    }

    private static void replaceEligibility(Map<BlockPos, Entry> next) {
        Map<BlockPos, Entry> previous = eligible;
        if (previous.equals(next)) return;
        eligible = next;
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.worldRenderer == null) return;
        Set<BlockPos> changed = new HashSet<>(previous.keySet());
        changed.addAll(next.keySet());
        for (BlockPos pos : changed) {
            if (Objects.equals(previous.get(pos), next.get(pos))) continue;
            // Include neighboring sections, restoring faces and visibility at chunk boundaries.
            client.worldRenderer.scheduleBlockRenders(pos.getX()-1, pos.getY()-1, pos.getZ()-1,
                pos.getX()+1, pos.getY()+1, pos.getZ()+1);
        }
        client.worldRenderer.scheduleTerrainUpdate();
    }

    public static boolean supports(BlockState state, BlockPos pos) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || !state.isOpaqueFullCube(client.world, pos) || state.hasBlockEntity()) return false;
        var model = client.getBlockRenderManager().getModel(state);
        if (!model.getQuads(state, null, Random.create(state.getRenderingSeed(pos))).isEmpty()) return false;
        for (Direction face : Direction.values()) {
            var quads = model.getQuads(state, face, Random.create(state.getRenderingSeed(pos)));
            if (quads.size() != 1) return false;
            var quad = quads.getFirst();
            if (quad.getFace() != face || !CubeFaceGeometry.fullFace(quad.getVertexData(), face)
                    || animated(quad.getSprite()) || !opaqueSprite(quad.getSprite())) return false;
        }
        return true;
    }

    private static boolean opaqueSprite(Sprite sprite) {
        Identifier id = sprite.getContents().getId();
        return OPAQUE_SPRITES.computeIfAbsent(id, key -> {
            Identifier source = key.withPath("textures/" + key.getPath() + ".png");
            try (var stream = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(source).getInputStream();
                 NativeImage image = NativeImage.read(stream)) {
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if (!CubeFaceGeometry.opaquePixel(image.getColor(x, y))) return false;
                    }
                }
                return true;
            } catch (Exception e) {
                BlockBlendMod.LOGGER.warn("Could not inspect opaque surface {}; retaining vanilla block", source, e);
                return false;
            }
        });
    }

    private static boolean animated(Sprite sprite) {
        return sprite.getContents().getMetadata().decode(net.minecraft.client.resource.metadata.AnimationResourceMetadata.READER).isPresent();
    }

    /** Fabulous resolve changes main depth. Keep opaque depth before the transparency passes. */
    private static void preserveOpaqueDepth(WorldRenderContext context) {
        RenderSystem.assertOnRenderThread();
        if (!context.advancedTranslucency() || eligible.isEmpty()) return;
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] textures = new int[3], viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        for (int unit = 0; unit < textures.length; unit++) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
            textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        RenderSystem.activeTexture(active);
        try {
            if (fabulousScene == null) {
                fabulousScene = new SimpleFramebuffer(main.textureWidth, main.textureHeight, true, MinecraftClient.IS_SYSTEM_MAC);
            } else if (fabulousScene.textureWidth != main.textureWidth || fabulousScene.textureHeight != main.textureHeight) {
                fabulousScene.resize(main.textureWidth, main.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
            }
            fabulousScene.copyDepthFrom(main);
            opaqueDepthReady = true;
        } finally {
            RenderSystem.depthMask(depthMask);
            if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            for (int unit = 0; unit < textures.length; unit++) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
                RenderSystem.bindTexture(textures[unit]);
            }
            RenderSystem.activeTexture(active);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        }
    }

    private static void copyColor(Framebuffer source, Framebuffer destination) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.fbo);
        GL30.glBlitFramebuffer(0, 0, source.textureWidth, source.textureHeight,
            0, 0, destination.textureWidth, destination.textureHeight, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static Identifier texture(Sprite sprite, BlendTag tag) {
        RenderSystem.assertOnRenderThread();
        Key key = textureKey(sprite, tag);
        Identifier found = TEXTURES.get(key);
        if (found != null) return found;
        if (FAILED_TEXTURES.contains(key)) return null;
        var client = MinecraftClient.getInstance();
        Identifier source = key.sprite().withPath("textures/" + key.sprite().getPath() + ".png");
        Identifier overlay = tag.overlayId().withPath("textures/" + tag.overlayId().getPath() + ".png");
        try (var baseStream = client.getResourceManager().getResourceOrThrow(source).getInputStream();
             var overlayStream = client.getResourceManager().getResourceOrThrow(overlay).getInputStream();
             NativeImage base = NativeImage.read(baseStream); NativeImage pattern = NativeImage.read(overlayStream)) {
            int width = sprite.getContents().getWidth(), height = sprite.getContents().getHeight();
            NativeImage result = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int b = base.getColor(Math.min(x, base.getWidth()-1), Math.min(y, base.getHeight()-1));
                int o = pattern.getColor(x*pattern.getWidth()/width, y*pattern.getHeight()/height);
                double strength = tag.strength()*((o>>>24)&255)/255d;
                int color = 0xff000000;
                for (int shift = 0; shift <= 16; shift += 8) {
                    int baseChannel = (b>>>shift)&255, patternChannel = (o>>>shift)&255;
                    color |= (int)Math.round(baseChannel + (patternChannel-baseChannel)*strength) << shift;
                }
                result.setColor(x, y, color);
            }
            Identifier id = Identifier.of("blockblend", "generated/blend_" + serial++);
            client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(result));
            TEXTURES.put(key, id);
            return id;
        } catch (Exception e) {
            FAILED_TEXTURES.add(key);
            BlockBlendMod.LOGGER.warn("Could not prepare blend texture {}; retaining vanilla block", source, e);
            return null;
        }
    }

    private static Key textureKey(Sprite sprite, BlendTag tag) {
        return new Key(sprite.getContents().getId(), tag.strength(), tag.overlayId());
    }

    private static void render(WorldRenderContext context) {
        RenderSystem.assertOnRenderThread();
        var client = MinecraftClient.getInstance();
        if (client.world == null || context.matrixStack() == null || shader == null || eligible.isEmpty()) return;
        Vec3d camera = context.camera().getPos();
        var forward = context.camera().getHorizontalPlane();
        List<Face> faces = new ArrayList<>();
        eligible.forEach((pos, entry) -> {
            if (entry.tag().opacity() == 0 || !client.world.isChunkLoaded(pos)) return;
            if (client.world.getBlockState(pos) != entry.state()) return;
            if (context.frustum() != null && !context.frustum().isVisible(new Box(pos))) return;
            var model = client.getBlockRenderManager().getModel(entry.state());
            for (Direction direction : Direction.values()) {
                if (hasVisiblePaintedNeighbor(pos.offset(direction))) continue;
                if (!Block.shouldDrawSide(entry.state(), client.world, pos, direction, pos.offset(direction))) continue;
                for (var quad : model.getQuads(entry.state(), direction, Random.create(entry.state().getRenderingSeed(pos)))) {
                    addFace(faces, pos, entry, quad, camera, forward);
                }
            }
            for (var quad : model.getQuads(entry.state(), null, Random.create(entry.state().getRenderingSeed(pos)))) {
                addFace(faces, pos, entry, quad, camera, forward);
            }
        });
        faces.sort(Comparator.comparingDouble(Face::depth).reversed()
            .thenComparingLong(face -> face.pos().asLong()).thenComparingInt(face -> face.quad().getFace().ordinal()));
        if (faces.isEmpty()) return;
        var framebuffer = client.getFramebuffer();
        if (context.advancedTranslucency() && (!opaqueDepthReady || fabulousScene == null
                || fabulousScene.textureWidth != framebuffer.textureWidth
                || fabulousScene.textureHeight != framebuffer.textureHeight)) return;
        Framebuffer destination = context.advancedTranslucency() ? fabulousScene : framebuffer;
        // LAST runs after vanilla/Fabric framebuffer writes and Fabulous scene resolve.
        ShaderProgram previousShader = RenderSystem.getShader();
        int previousTexture0 = RenderSystem.getShaderTexture(0), previousTexture1 = RenderSystem.getShaderTexture(1);
        int previousTexture2 = RenderSystem.getShaderTexture(2);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] boundTextures = new int[3];
        for (int unit = 0; unit < boundTextures.length; unit++) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
            boundTextures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(context.positionMatrix());
        RenderSystem.applyModelViewMatrix();
        try {
            if (destination != framebuffer) copyColor(framebuffer, destination);
            destination.beginWrite(false);
            ensureBackdrop(framebuffer.textureWidth, framebuffer.textureHeight);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend(); // Shader explicitly computes source-over coverage.
            RenderSystem.enableCull();
            RenderSystem.setShader(() -> shader);
            client.gameRenderer.getLightmapTextureManager().enable();
            shader.getUniformOrDefault("SceneSize").set((float)backdropWidth, (float)backdropHeight);
            MatrixStack matrices = new MatrixStack();
            for (Face face : faces) {
                matrices.push();
                matrices.translate(face.pos().getX()-camera.x, face.pos().getY()-camera.y, face.pos().getZ()-camera.z);
                copyBackdrop(matrices.peek().getPositionMatrix(), face.quad(), destination);
                draw(face, matrices.peek());
                matrices.pop();
            }
            if (destination != framebuffer) copyColor(destination, framebuffer);
        } finally {
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunction);
            if (!depthTest) RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.setShaderTexture(0, previousTexture0);
            RenderSystem.setShaderTexture(1, previousTexture1);
            RenderSystem.setShaderTexture(2, previousTexture2);
            RenderSystem.setShader(() -> previousShader);
            for (int unit = 0; unit < boundTextures.length; unit++) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
                RenderSystem.bindTexture(boundTextures[unit]);
            }
            RenderSystem.activeTexture(activeTexture);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static boolean hasVisiblePaintedNeighbor(BlockPos pos) {
        var world = MinecraftClient.getInstance().world;
        if (world == null || !world.isChunkLoaded(pos)) return false;
        Entry neighbor = eligible.get(pos);
        // Touching painted cubes form one exterior region. Invisible, unsupported,
        // missing or stale neighbors leave the visible cube's boundary exposed.
        return neighbor != null && neighbor.tag().opacity() > 0
            && world.getBlockState(pos) == neighbor.state();
    }

    private static void addFace(List<Face> faces, BlockPos pos, Entry entry, BakedQuad quad,
                                Vec3d camera, org.joml.Vector3f forward) {
        Direction normal = quad.getFace();
        double x = pos.getX()+.5+normal.getOffsetX()*.5;
        double y = pos.getY()+.5+normal.getOffsetY()*.5;
        double z = pos.getZ()+.5+normal.getOffsetZ()*.5;
        // Draw one outward surface per ray; the back of the same cube must not double opacity.
        if ((camera.x-x)*normal.getOffsetX() + (camera.y-y)*normal.getOffsetY() + (camera.z-z)*normal.getOffsetZ() <= 0) return;
        double depth = (x-camera.x)*forward.x + (y-camera.y)*forward.y + (z-camera.z)*forward.z;
        faces.add(new Face(pos, entry, quad, depth));
    }

    private static void ensureBackdrop(int width, int height) {
        if (backdropTexture != 0 && backdropWidth == width && backdropHeight == height) return;
        if (backdropTexture != 0) GL11.glDeleteTextures(backdropTexture);
        backdropTexture = GL11.glGenTextures();
        backdropWidth = width; backdropHeight = height;
        RenderSystem.activeTexture(GL13.GL_TEXTURE1);
        RenderSystem.bindTexture(backdropTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer)null);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
    }
    private static final int GL12_CLAMP_TO_EDGE = 0x812F;

    private static void copyBackdrop(Matrix4f positionMatrix, BakedQuad quad, Framebuffer source) {
        Matrix4f transform = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(positionMatrix);
        int[] vertices = quad.getVertexData();
        int stride = vertices.length/4;
        float minX = backdropWidth, minY = backdropHeight, maxX = 0, maxY = 0;
        boolean full = false;
        for (int i = 0; i < 4; i++) {
            int offset = i*stride;
            Vector4f point = new Vector4f(Float.intBitsToFloat(vertices[offset]),
                Float.intBitsToFloat(vertices[offset+1]), Float.intBitsToFloat(vertices[offset+2]), 1).mul(transform);
            if (point.w <= 0) { full = true; break; }
            float x = (point.x/point.w*.5f+.5f)*backdropWidth;
            float y = (point.y/point.w*.5f+.5f)*backdropHeight;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        int x = full ? 0 : Math.max(0, Math.min(backdropWidth, (int)Math.floor(minX)-2));
        int y = full ? 0 : Math.max(0, Math.min(backdropHeight, (int)Math.floor(minY)-2));
        int right = full ? backdropWidth : Math.max(x, Math.min(backdropWidth, (int)Math.ceil(maxX)+2));
        int top = full ? backdropHeight : Math.max(y, Math.min(backdropHeight, (int)Math.ceil(maxY)+2));
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.fbo);
        RenderSystem.activeTexture(GL13.GL_TEXTURE1);
        RenderSystem.bindTexture(backdropTexture);
        // Copy only the projected face rectangle. Overlapping layers see previous composited faces.
        if (right > x && top > y) GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, x, y, right-x, top-y);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.setShaderTexture(1, backdropTexture);
    }

    private static void draw(Face face, MatrixStack.Entry matrix) {
        var client = MinecraftClient.getInstance();
        var quad = face.quad();
        BlendTag tag = face.entry().tag();
        Identifier id = texture(quad.getSprite(), tag);
        if (id == null) return;
        RenderSystem.setShaderTexture(0, id);
        shader.getUniformOrDefault("BlendMode").set(tag.mode());
        shader.getUniformOrDefault("Opacity").set(tag.opacity());
        Direction direction = quad.getFace();
        float shade = client.world.getBrightness(direction, quad.hasShade());
        int tint = quad.hasColor() ? client.getBlockColors().getColor(face.entry().state(), client.world, face.pos(), quad.getColorIndex()) : 0xffffff;
        int light = WorldRenderer.getLightmapCoordinates(client.world, face.entry().state(), face.pos().offset(direction));
        // Keep vanilla lightmap sampling in the shader rather than approximating sky/block lighting.
        shader.getUniformOrDefault("LightUV").set((light&0xffff)/16, (light>>>16)/16);
        var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        int[] data = quad.getVertexData();
        int stride = data.length/4;
        Sprite sprite = quad.getSprite();
        for (int i = 0; i < 4; i++) {
            int offset = i*stride;
            float x = Float.intBitsToFloat(data[offset]), y = Float.intBitsToFloat(data[offset+1]), z = Float.intBitsToFloat(data[offset+2]);
            float u = (Float.intBitsToFloat(data[offset+4])-sprite.getMinU())/(sprite.getMaxU()-sprite.getMinU());
            float v = (Float.intBitsToFloat(data[offset+5])-sprite.getMinV())/(sprite.getMaxV()-sprite.getMinV());
            buffer.vertex(matrix.getPositionMatrix(), x, y, z)
                .texture(u, v).color(((tint>>>16)&255)/255f*shade, ((tint>>>8)&255)/255f*shade, (tint&255)/255f*shade, 1f);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private BlendRenderer() {}
}
