package com.tacz.legacy.client.resource.mesh;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One material group inside a decoded static mesh: a slice of vertices/indices
 * plus an optional material key the render layer later resolves to a texture.
 *
 * <p>Groups let one model carry several atlases/materials without baking them into
 * a single texture. A {@code null} materialKey means "use the texture already
 * bound by the caller" — the legacy single-texture path.
 *
 * <p>Geometric arrays use the same layout as {@link StaticMeshData}: interleaved
 * conceptually as position(3) / uv(2) / normal(3), indexed by a triangle index
 * buffer.
 */
public final class MeshGroup {

    @Nullable
    private final String materialKey;
    private final float[] positions;
    private final float[] uvs;
    private final float[] normals;
    private final int[] indices;

    public MeshGroup(@Nullable String materialKey,
                     float[] positions, float[] uvs, float[] normals, int[] indices) {
        this.materialKey = materialKey;
        this.positions = positions;
        this.uvs = uvs;
        this.normals = normals;
        this.indices = indices;
    }

    @Nullable
    public String getMaterialKey() {
        return materialKey;
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

    public int getVertexCount() {
        return positions.length / 3;
    }

    public boolean isEmpty() {
        return indices.length == 0 || positions.length == 0;
    }

    @Override
    public String toString() {
        return "MeshGroup{key=" + materialKey + ", vertices=" + getVertexCount()
                + ", triangles=" + indices.length / 3 + '}';
    }
}
