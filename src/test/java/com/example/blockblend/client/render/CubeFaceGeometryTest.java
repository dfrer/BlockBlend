package com.example.blockblend.client.render;

import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CubeFaceGeometryTest {
    @Test void acceptsAllSixFullCubeFaces() {
        for (Direction face : Direction.values()) {
            assertTrue(CubeFaceGeometry.fullFace(vertices(face), face), face.toString());
        }
    }

    @Test void rejectsPartialMalformedAndWrongFaces() {
        assertFalse(CubeFaceGeometry.fullFace(vertices(Direction.EAST), Direction.WEST));
        var inset = vertices(Direction.UP);
        inset[1] = Float.floatToIntBits(.9f);
        assertFalse(CubeFaceGeometry.fullFace(inset, Direction.UP));
        var partial = vertices(Direction.UP);
        partial[0] = Float.floatToIntBits(.5f);
        assertFalse(CubeFaceGeometry.fullFace(partial, Direction.UP));
        var duplicate = vertices(Direction.UP);
        System.arraycopy(duplicate, 0, duplicate, 8, 3);
        assertFalse(CubeFaceGeometry.fullFace(duplicate, Direction.UP));
        var nonfinite = vertices(Direction.UP);
        nonfinite[0] = Float.floatToIntBits(Float.NaN);
        assertFalse(CubeFaceGeometry.fullFace(nonfinite, Direction.UP));
        var crossed = vertices(Direction.UP);
        swapVertices(crossed, 1, 2);
        assertFalse(CubeFaceGeometry.fullFace(crossed, Direction.UP));
        var inward = vertices(Direction.UP);
        swapVertices(inward, 1, 3);
        assertFalse(CubeFaceGeometry.fullFace(inward, Direction.UP));
        assertFalse(CubeFaceGeometry.fullFace(new int[12], Direction.UP));
        assertFalse(CubeFaceGeometry.fullFace(new int[31], Direction.UP));
    }

    @Test void rejectsAllPartialAlphaLevels() {
        for (int alpha = 0; alpha < 255; alpha++) {
            assertFalse(CubeFaceGeometry.opaquePixel((alpha << 24) | 0x123456));
        }
        assertTrue(CubeFaceGeometry.opaquePixel(0xff123456));
        assertTrue(CubeFaceGeometry.opaquePixel(0xff000000));
        assertTrue(CubeFaceGeometry.opaquePixel(0xffffffff));
    }

    private static int[] vertices(Direction face) {
        int axis = switch (face.getAxis()) {
            case X -> 0;
            case Y -> 1;
            case Z -> 2;
        };
        int[] data = new int[32];
        boolean positive = face.getDirection() == Direction.AxisDirection.POSITIVE;
        int[] corners = (axis == 1) == positive ? new int[]{0,2,3,1} : new int[]{0,1,3,2};
        for (int vertex = 0; vertex < 4; vertex++) {
            int bit = 1;
            for (int coordinate = 0; coordinate < 3; coordinate++) {
                float value;
                if (coordinate == axis) {
                    value = face.getDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
                } else {
                    value = (corners[vertex] & bit) == 0 ? 0 : 1;
                    bit <<= 1;
                }
                data[vertex * 8 + coordinate] = Float.floatToIntBits(value);
            }
        }
        return data;
    }

    private static void swapVertices(int[] data, int first, int second) {
        for (int coordinate = 0; coordinate < 3; coordinate++) {
            int value = data[first*8+coordinate];
            data[first*8+coordinate] = data[second*8+coordinate];
            data[second*8+coordinate] = value;
        }
    }
}
