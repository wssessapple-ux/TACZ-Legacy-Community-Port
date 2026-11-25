package com.tacz.legacy.client.resource.mesh;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Decoded static mesh reduced to the subset the 1.12.2 fixed pipeline can draw:
 * position / UV / normal arrays plus a triangle index buffer, split into one or
 * more {@link MeshGroup material groups}.
 *
 * <p>Format-agnostic: produced by both the glTF mesh parser and the OBJ parser,
 * and consumed by the shared mesh render wrapper.
 *
 * <p>Legacy single-texture consumers keep using the array getters, which return
 * the first group's data verbatim. Multi-material models expose their full group
 * list via {@link #getGroups()}.
 *
 * <p>Still dropped: skins, morph targets, node hierarchy animation and
 * tangent/bitangent attributes.
 */
public final class StaticMeshData {

    private final List<MeshGroup> groups;

    /** First-group arrays kept for legacy single-texture consumers. */
    private final float[] positions;
    private final float[] uvs;
    private final float[] normals;
    private final int[] indices;

    /**
     * Legacy single-group constructor: wraps the supplied arrays in one
     * material-less group ({@code materialKey = null}, caller-bound texture).
     */
    public StaticMeshData(float[] positions, float[] uvs, float[] normals, int[] indices) {
        this(Collections.singletonList(
                new MeshGroup(null, positions, uvs, normals, indices)));
    }

    /**
     * Multi-group constructor: every supplied group keeps its own geometry and
     * optional material key.
     */
    public StaticMeshData(@Nonnull List<MeshGroup> groups) {
        this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
        MeshGroup first = this.groups.isEmpty() ? null : this.groups.get(0);
        this.positions = first == null ? new float[0] : first.getPositions();
        this.uvs = first == null ? new float[0] : first.getUvs();
        this.normals = first == null ? new float[0] : first.getNormals();
        this.indices = first == null ? new int[0] : first.getIndices();
    }

    /** All material groups; never null, but may be empty. */
    @Nonnull
    public List<MeshGroup> getGroups() {
        return groups;
    }

    public int getGroupCount() {
        return groups.size();
    }

    @Nullable
    public MeshGroup getGroup(int index) {
        return index >= 0 && index < groups.size() ? groups.get(index) : null;
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
        for (MeshGroup group : groups) {
            if (!group.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "StaticMeshData{groups=" + groups.size() + ", vertices=" + getVertexCount()
                + ", triangles=" + (indices.length / 3) + '}';
    }
}
