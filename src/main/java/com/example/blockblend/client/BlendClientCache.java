package com.example.blockblend.client;

import com.example.blockblend.data.BlendTag;
import com.example.blockblend.data.ChunkKey;
import com.example.blockblend.client.render.BlendRenderer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Main-thread mutations publish complete immutable snapshots, never mutable chunk maps. */
public final class BlendClientCache {
    private static ClientWorld world;
    private static volatile Map<BlockPos, BlendTag> snapshot = Map.of();

    public static void clear() {
        world = null;
        snapshot = Map.of();
        BlendRenderer.refreshEligibility();
    }

    public static void ensureWorld() {
        var current = MinecraftClient.getInstance().world;
        if (world != current) {
            snapshot = Map.of();
            world = current;
            BlendRenderer.refreshEligibility();
        }
    }

    public static boolean accepts(Identifier dimension) {
        ensureWorld();
        return world != null && world.getRegistryKey().getValue().equals(dimension);
    }

    public static void chunk(Identifier dimension, ChunkPos pos, Int2ObjectOpenHashMap<BlendTag> entries) {
        if (!accepts(dimension)) return;
        var next = new HashMap<>(snapshot);
        next.keySet().removeIf(p -> new ChunkPos(p).equals(pos));
        for (var entry : entries.int2ObjectEntrySet()) {
            next.put(ChunkKey.unpackLocal(entry.getIntKey(), pos), entry.getValue());
        }
        publish(next);
    }

    public static void unload(ChunkPos pos) {
        ensureWorld();
        var next = new HashMap<>(snapshot);
        next.keySet().removeIf(p -> new ChunkPos(p).equals(pos));
        publish(next);
    }

    public static void update(Identifier dimension, BlockPos pos, BlendTag tag) {
        if (!accepts(dimension)) return;
        var next = new HashMap<>(snapshot);
        if (tag == null) next.remove(pos); else next.put(pos.toImmutable(), tag);
        publish(next);
    }

    private static void publish(Map<BlockPos, BlendTag> next) {
        snapshot = Map.copyOf(next);
        BlendRenderer.refreshEligibility();
    }

    public static Map<BlockPos, BlendTag> snapshot() { return snapshot; }
    public static void forEach(BiConsumer<BlockPos, BlendTag> consumer) { snapshot.forEach(consumer); }
    public static BlendTag get(BlockPos pos) { return snapshot.get(pos); }
    private BlendClientCache() {}
}


