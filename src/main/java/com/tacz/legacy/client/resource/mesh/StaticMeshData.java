package com.tacz.legacy.client.resource.mesh;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * Decoded static mesh reduced to the subset the 1.12.2 fixed pipeline can draw:
 * position / UV / normal arrays plus a triangle index buffer.
 *
 * <p>Format-agnostic: produced by both the glTF mesh parser and the OBJ parser,
 * and consumed by the shared mesh render wrapper.
 *
 * <p>First version intentionally drops everything the pipeline cannot express:
 * skins, morph targets, node hierarchy animation, multi-material groups and
 * tangent/bitangent attributes.
 */
public final class StaticMeshData {

    private final float[] positions;
    private final float[] uvs;
    private final float[] normals;
    private final int[] indices;

    public StaticMeshData(float[] positions, float[] uvs, float[] normals, int[] indices) {
        this.positions = positions;
        this.uvs = uvs;
        this.normals = normals;
        this.indices = indices;
    }

    public int getVertexCount() {
        return positions.length / 3;
    }

    @Nonnull
    public float[] getPositions() {
        return positions;
    }

    @Nonnull
    public float[] getUvs() {
        return uvs;
    }

    @Nonnull
    public float[] getNormals() {
        return normals;
    }

    @Nonnull
    public int[] getIndices() {
        return indices;
    }

    public boolean isEmpty() {
        return indices.length == 0 || positions.length == 0;
    }

    @Override
    public String toString() {
        return "StaticMeshData{vertices=" + getVertexCount() + ", triangles=" + (indices.length / 3)
                + ", positions=" + Arrays.hashCode(positions) + '}';
    }
}
