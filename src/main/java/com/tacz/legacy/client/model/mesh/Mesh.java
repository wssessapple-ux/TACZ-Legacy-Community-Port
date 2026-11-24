package com.tacz.legacy.client.model.mesh;

import com.tacz.legacy.client.resource.mesh.StaticMeshData;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Render wrapper around a decoded {@link StaticMeshData}. Shared by the glTF and
 * OBJ mesh sources.
 *
 * <p>Single entry point {@link #draw()} picks the VBO path or the immediate-mode
 * fallback internally; callers do not branch on the upload state. Both paths use
 * the same {@link MeshTransform}, so vertex/normal conversion cannot drift.
 */
public class Mesh {

    private final StaticMeshData data;
    private final MeshVbo vbo = new MeshVbo();
    private final String tag;
    private float scale = 16.0F;
    private boolean flipY = true;

    public Mesh(StaticMeshData data) {
        this(data, "unnamed");
    }

    public Mesh(StaticMeshData data, String tag) {
        this.data = data;
        this.tag = tag == null ? "unnamed" : tag;
    }

    public StaticMeshData getData() {
        return data;
    }

    public void setScale(float scale) {
        if (scale != this.scale) {
            this.scale = scale;
            vbo.markDirty();
        }
    }

    public void setFlipY(boolean flipY) {
        if (flipY != this.flipY) {
            this.flipY = flipY;
            vbo.markDirty();
        }
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    /**
     * Draw the mesh. Uploads/reuses the VBO when possible; falls back to
     * immediate mode when the VBO is not ready or has permanently failed.
     */
    public void draw() {
        if (data.isEmpty()) {
            return;
        }
        if (vbo.ensureUploaded(data, scale, flipY, tag)) {
            vbo.draw();
            MeshVboStats.INSTANCE.recordHit();
        } else {
            drawImmediate();
            MeshVboStats.INSTANCE.recordFallback();
        }
    }

    /** Standalone draw: wraps {@link #draw()} in a matrix push/pop. */
    public void render() {
        if (data.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        draw();
        GlStateManager.popMatrix();
    }

    private void drawImmediate() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_NORMAL);
        compile(buffer);
        tessellator.draw();
    }

    /** Immediate-mode fallback vertex feed, using the shared transform. */
    public void compile(BufferBuilder buffer) {
        float[] positions = data.getPositions();
        float[] uvs = data.getUvs();
        float[] normals = data.getNormals();
        int[] indices = data.getIndices();
        float[] out = new float[6];

        for (int i = 0; i < indices.length; i++) {
            int vertex = indices[i];
            MeshTransform.transform(positions, normals, vertex, scale, flipY, out);
            buffer.pos(out[0], out[1], out[2])
                    .tex(uvs[vertex * 2], uvs[vertex * 2 + 1])
                    .normal(out[3], out[4], out[5])
                    .endVertex();
        }
    }

    /** Release the cached VBO. Called on teardown / future resource reload. */
    public void destroyVbo() {
        vbo.destroy();
    }
}
