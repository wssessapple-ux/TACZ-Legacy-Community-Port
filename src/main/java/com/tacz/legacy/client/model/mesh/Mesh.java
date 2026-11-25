package com.tacz.legacy.client.model.mesh;

import com.tacz.legacy.client.resource.mesh.MeshGroup;
import com.tacz.legacy.client.resource.mesh.StaticMeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

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
    @Nullable
    private MaterialResolver materialResolver;
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

    /**
     * Inject the resolver that maps a group's material key to a texture. A
     * {@code null} resolver (the default) leaves every group on the texture the
     * caller already bound.
     */
    public void setMaterialResolver(@Nullable MaterialResolver resolver) {
        this.materialResolver = resolver;
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
            vbo.draw(this::bindGroupTexture);
            MeshVboStats.INSTANCE.recordHit();
        } else {
            drawImmediate();
            MeshVboStats.INSTANCE.recordFallback();
        }
    }

    /** Per-group texture bind routed through the optional resolver; no-op when there is no override. */
    private void bindGroupTexture(MeshGroup group) {
        if (materialResolver == null) {
            return;
        }
        String key = group.getMaterialKey();
        if (key == null) {
            return;
        }
        ResourceLocation texture = materialResolver.resolve(key);
        if (texture != null) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
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
        for (MeshGroup group : data.getGroups()) {
            if (group.isEmpty()) {
                continue;
            }
            bindGroupTexture(group);
            buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_NORMAL);
            compileGroup(buffer, group);
            tessellator.draw();
        }
    }

    /** Immediate-mode fallback vertex feed for every group, using the shared transform. */
    public void compile(BufferBuilder buffer) {
        for (MeshGroup group : data.getGroups()) {
            if (!group.isEmpty()) {
                compileGroup(buffer, group);
            }
        }
    }

    /** Feed one group's vertices into the immediate-mode batch, using the shared transform. */
    private void compileGroup(BufferBuilder buffer, MeshGroup group) {
        float[] out = new float[6];
        float[] positions = group.getPositions();
        float[] uvs = group.getUvs();
        float[] normals = group.getNormals();
        int[] indices = group.getIndices();

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
