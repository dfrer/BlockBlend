package com.example.blockblend.client.render;

import net.minecraft.util.math.Direction;

/** The compositor supports one complete outward surface for each cube face. */
final class CubeFaceGeometry {
    private static final float EPSILON = 0.0001f;

    static boolean fullFace(int[] vertices, Direction face) {
        if (vertices.length < 24 || vertices.length % 4 != 0) return false;
        int stride = vertices.length / 4;
        int axis = switch (face.getAxis()) {
            case X -> 0;
            case Y -> 1;
            case Z -> 2;
        };
        float boundary = face.getDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
        int corners = 0, firstCorner = -1, previousCorner = -1;
        for (int vertex = 0; vertex < 4; vertex++) {
            int corner = 0, bit = 1;
            for (int coordinate = 0; coordinate < 3; coordinate++) {
                float value = Float.intBitsToFloat(vertices[vertex * stride + coordinate]);
                if (!Float.isFinite(value)) return false;
                if (coordinate == axis) {
                    if (Math.abs(value - boundary) > EPSILON) return false;
                } else {
                    if (Math.abs(value - 1) <= EPSILON) corner |= bit;
                    else if (Math.abs(value) > EPSILON) return false;
                    bit <<= 1;
                }
            }
            int flag = 1 << corner;
            if ((corners & flag) != 0) return false;
            if (previousCorner >= 0 && Integer.bitCount(previousCorner ^ corner) != 1) return false;
            if (firstCorner < 0) firstCorner = corner;
            previousCorner = corner;
            corners |= flag;
        }
        if (corners != 15 || Integer.bitCount(previousCorner ^ firstCorner) != 1) return false;
        float[] firstEdge = new float[3], secondEdge = new float[3];
        for (int coordinate = 0; coordinate < 3; coordinate++) {
            float origin = Float.intBitsToFloat(vertices[coordinate]);
            firstEdge[coordinate] = Float.intBitsToFloat(vertices[stride + coordinate]) - origin;
            secondEdge[coordinate] = Float.intBitsToFloat(vertices[2 * stride + coordinate]) - origin;
        }
        int u = (axis + 1) % 3, v = (axis + 2) % 3;
        float normal = firstEdge[u] * secondEdge[v] - firstEdge[v] * secondEdge[u];
        return boundary == 1 ? normal > EPSILON : normal < -EPSILON;
    }

    static boolean opaquePixel(int color) {
        return (color >>> 24) == 255;
    }

    private CubeFaceGeometry() {}
}
