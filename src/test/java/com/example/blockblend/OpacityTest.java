package com.example.blockblend;

import com.example.blockblend.data.*;
import com.example.blockblend.registry.*;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpacityTest {
    @Test void opacityIsIndependentAndLegacyDefaultsOpaque() {
        var legacy = new BlendTag(2, .7f, OverlayCatalog.IDS.getFirst());
        assertEquals(1f, legacy.opacity());
        var half = new BlendTag(2, .7f, legacy.overlayId(), .5f);
        assertEquals(legacy.strength(), half.strength());
        assertEquals(legacy.mode(), half.mode());
        assertEquals(.5f, half.opacity());
        NbtCompound nbt = BlendTagCodec.toNbt(half);
        assertEquals(half, BlendTagCodec.fromNbt(nbt));
        nbt.remove("opacity");
        assertEquals(legacy, BlendTagCodec.fromNbt(nbt));
    }

    @Test void invalidOpacityCannotBecomeAnInvisibleBlock() {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.01f, 1.01f}) {
            assertThrows(IllegalArgumentException.class,
                () -> new BlendTag(0, .5f, OverlayCatalog.IDS.getFirst(), value));
            NbtCompound nbt = BlendTagCodec.toNbt(new BlendTag(0, .5f, OverlayCatalog.IDS.getFirst()));
            nbt.putFloat("opacity", value);
            assertThrows(IllegalArgumentException.class, () -> BlendTagCodec.fromNbt(nbt));
            var buffer = new PacketByteBuf(Unpooled.buffer());
            try {
                buffer.writeByte(0).writeFloat(.5f).writeByte(0).writeFloat(value);
                assertThrows(IllegalArgumentException.class, () -> BlendTagCodec.read(buffer));
            } finally { buffer.release(); }
        }
        var wrongType = BlendTagCodec.toNbt(new BlendTag(0, .5f, OverlayCatalog.IDS.getFirst()));
        wrongType.putString("opacity", "invalid");
        assertThrows(IllegalArgumentException.class, () -> BlendTagCodec.fromNbt(wrongType));
    }

    @Test void formatTwoMigratesOpacityAndFutureDataStaysReadOnly() {
        var position = new BlockPos(15, -64, 16);
        var tag = new BlendTag(4, .8f, OverlayCatalog.IDS.get(2), .3f);
        var state = new BlendState();
        assertTrue(state.set(position, tag));
        var saved = state.writeNbt(new NbtCompound(), null);
        assertEquals(3, saved.getInt("format"));
        assertEquals(tag, BlendState.fromNbt(saved, null).get(position));
        saved.putInt("format", 2);
        saved.getList("chunks", NbtElement.COMPOUND_TYPE).getCompound(0)
            .getList("entries", NbtElement.COMPOUND_TYPE).getCompound(0).remove("opacity");
        assertEquals(1f, BlendState.fromNbt(saved, null).get(position).opacity());
        saved.putInt("format", 4);
        var future = BlendState.fromNbt(saved, null);
        assertFalse(future.isWritable());
        assertFalse(future.set(position, tag));
        assertEquals(saved, future.writeNbt(new NbtCompound(), null));
    }

    @Test void changedWireFormatUsesDifferentChannels() {
        for (var id : new net.minecraft.network.packet.CustomPayload.Id<?>[]{
                ApplyBlendC2SPayload.ID, ModNetworking.ChunkRequest.ID,
                ModNetworking.Update.ID, ModNetworking.ChunkData.ID}) {
            assertTrue(id.id().getPath().startsWith("v3/"));
        }
        for (float opacity : new float[]{0f, .5f, 1f}) {
            var tag = new BlendTag(6, 0f, OverlayCatalog.IDS.get(1), opacity);
            var buffer = new PacketByteBuf(Unpooled.buffer());
            try {
                BlendTagCodec.write(buffer, tag);
                assertEquals(tag, BlendTagCodec.read(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }

    @Test void sceneModesUseActualBackdropAndCoverage() {
        for (int mode = 0; mode < BlendTag.MODE_COUNT; mode++) {
            assertEquals(.3, BlendMath.sceneChannel(.3, .7, mode, 0), 1e-12);
            double opaque = BlendMath.sceneChannel(.3, .7, mode, 1);
            assertEquals((.3 + opaque)/2, BlendMath.sceneChannel(.3, .7, mode, .5), 1e-12);
        }
        assertEquals(.21, BlendMath.sceneChannel(.3, .7, 0, 1), 1e-12);
        assertEquals(.79, BlendMath.sceneChannel(.3, .7, 1, 1), 1e-12);
        assertNotEquals(BlendMath.sceneChannel(.3, .7, 0, .5), BlendMath.sceneChannel(.8, .7, 0, .5));
    }
}
