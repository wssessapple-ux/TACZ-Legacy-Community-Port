package com.tacz.legacy.client.model.mesh;

import com.tacz.legacy.TACZLegacy;
import com.tacz.legacy.client.resource.mesh.StaticMeshData;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.OpenGLException;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * GPU-side cached static mesh with an explicit upload state machine.
 *
 * <ul>
 *   <li>{@link State#NEW} — not uploaded yet (one-time not-ready).</li>
 *   <li>{@link State#READY} — buffers uploaded, draw from GPU memory.</li>
 *   <li>{@link State#DIRTY} — a recoverable failure dropped the buffers; retry
 *       the upload next frame.</li>
 *   <li>{@link State#FAILED} — persistent failure; permanently degraded to the
 *       immediate-mode fallback and never retried.</li>
 * </ul>
 *
 * <p>All GL calls run on the render thread. Telemetry is reported through
 * {@link MeshVboStats}; a persistent failure logs one WARN carrying the GL
 * version, renderer and error code, while recoverable failures are only counted.
 */
public final class MeshVbo {

    public enum State {
        NEW, READY, DIRTY, FAILED
    }

    /** Interleaved stride: 3 position + 2 UV + 3 normal = 8 floats = 32 bytes. */
    private static final int STRIDE_BYTES = 8 * 4;
    private static final int OFFSET_TEX = 3 * 4;
    private static final int OFFSET_NORMAL = (3 + 2) * 4;

    /** GL_OUT_OF_MEMORY — recoverable: drop buffers and retry next frame. */
    private static final int GL_OUT_OF_MEMORY = 0x0505;

    private State state = State.NEW;
    private int vboId = 0;
    private int iboId = 0;
    private int indexCount = 0;
    private int indexType = GL11.GL_UNSIGNED_SHORT;
    private int vertexCount = 0;
    private long liveBytes = 0;
    private float cachedScale = Float.NaN;
    private boolean cachedFlipY;

    public State getState() {
        return state;
    }

    public boolean isReady() {
        return state == State.READY;
    }

    public boolean isFailed() {
        return state == State.FAILED;
    }

    /** Mark the cached buffers stale (e.g. scale/flipY changed). READY goes DIRTY; FAILED stays degraded. */
    public void markDirty() {
        if (state == State.READY) {
            destroyBuffers();
            state = State.DIRTY;
        }
    }

    /**
     * Ensure buffers are uploaded for the given transform.
     *
     * @return true when the mesh is ready to draw from the VBO; false when the
     *         caller must use the immediate-mode fallback (recoverable retry next
     *         frame, or permanent degradation).
     */
    public boolean ensureUploaded(StaticMeshData data, float scale, boolean flipY, String tag) {
        if (state == State.FAILED) {
            return false;
        }
        if (state == State.READY && cachedScale == scale && cachedFlipY == flipY) {
            return true;
        }
        if (state == State.READY) {
            destroyBuffers();
        }

        try {
            upload(data, scale, flipY);
            state = State.READY;
            cachedScale = scale;
            cachedFlipY = flipY;
            MeshVboStats.INSTANCE.recordRebuild();
            return true;
        } catch (Throwable t) {
            int err = GL11.glGetError();
            boolean recoverable = (t instanceof OpenGLException) && err == GL_OUT_OF_MEMORY;
            destroyBuffers();
            if (recoverable) {
                state = State.DIRTY;
                MeshVboStats.INSTANCE.recordRecoverableFailure();
            } else {
                state = State.FAILED;
                MeshVboStats.INSTANCE.recordPersistentFailure();
                logPersistentFailure(tag, t, err);
            }
            return false;
        }
    }

    /** Draw the cached mesh. Caller must have bound the texture and set up lighting. */
    public void draw() {
        if (state != State.READY) {
            return;
        }

        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, STRIDE_BYTES, 0L);
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, STRIDE_BYTES, OFFSET_TEX);
        GL11.glNormalPointer(GL11.GL_FLOAT, STRIDE_BYTES, OFFSET_NORMAL);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, iboId);
        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, indexType, 0L);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    /** Release the cached VBO/IBO and reset to NEW. */
    public void destroy() {
        destroyBuffers();
        state = State.NEW;
        cachedScale = Float.NaN;
    }

    private void upload(StaticMeshData data, float scale, boolean flipY) {
        vertexCount = data.getVertexCount();
        if (vertexCount == 0 || data.getIndices().length == 0) {
            throw new IllegalStateException("empty mesh");
        }

        float[] positions = data.getPositions();
        float[] uvs = data.getUvs();
        float[] normals = data.getNormals();
        int[] indices = data.getIndices();
        float[] out = new float[6];

        FloatBuffer vertices = BufferUtils.createFloatBuffer(vertexCount * 8);
        for (int i = 0; i < vertexCount; i++) {
            MeshTransform.transform(positions, normals, i, scale, flipY, out);
            vertices.put(out[0]).put(out[1]).put(out[2]);
            vertices.put(uvs[i * 2]).put(uvs[i * 2 + 1]);
            vertices.put(out[3]).put(out[4]).put(out[5]);
        }
        vertices.flip();

        vboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);

        iboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, iboId);

        if (vertexCount <= 65535) {
            ShortBuffer indexBuffer = BufferUtils.createShortBuffer(indices.length);
            for (int index : indices) {
                indexBuffer.put((short) index);
            }
            indexBuffer.flip();
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_STATIC_DRAW);
            indexType = GL11.GL_UNSIGNED_SHORT;
        } else {
            IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices.length);
            for (int index : indices) {
                indexBuffer.put(index);
            }
            indexBuffer.flip();
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_STATIC_DRAW);
            indexType = GL11.GL_UNSIGNED_INT;
        }

        indexCount = indices.length;
        liveBytes = vertexCount * 8L * 4L + indexCount * (indexType == GL11.GL_UNSIGNED_SHORT ? 2L : 4L);
        MeshVboStats.INSTANCE.addLive(liveBytes);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private void logPersistentFailure(String tag, Throwable t, int err) {
        String glVersion = safeGlString(GL11.GL_VERSION);
        String glRenderer = safeGlString(GL11.GL_RENDERER);
        TACZLegacy.INSTANCE.getLogger().warn(
                "[MeshVbo] Persistent immediate-mode fallback for '{}' | GL {} | renderer {} | glError 0x{} | {}",
                tag, glVersion, glRenderer, Integer.toHexString(err), t.toString());
    }

    private static String safeGlString(int name) {
        try {
            return GL11.glGetString(name);
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private void destroyBuffers() {
        if (vboId != 0) {
            GL15.glDeleteBuffers(vboId);
            vboId = 0;
        }
        if (iboId != 0) {
            GL15.glDeleteBuffers(iboId);
            iboId = 0;
        }
        if (liveBytes > 0) {
            MeshVboStats.INSTANCE.removeLive(liveBytes);
            liveBytes = 0;
        }
        indexCount = 0;
        vertexCount = 0;
    }
}
