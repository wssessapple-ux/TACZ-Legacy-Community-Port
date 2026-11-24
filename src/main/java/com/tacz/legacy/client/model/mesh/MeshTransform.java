package com.tacz.legacy.client.model.mesh;

/**
 * Shared vertex transform used by both the VBO upload path and the immediate-mode
 * fallback, so the two can never drift apart.
 *
 * <p>Source model units are scaled to the 16-per-block bedrock unit, the source
 * Y-up axis is flipped to Minecraft Y-down when requested, and the normal is
 * normalized. UVs are passed through unchanged.
 */
public final class MeshTransform {

    private MeshTransform() {
    }

    /**
     * @param out reusable 6-element output: [px, py, pz, nx, ny, nz]
     */
    public static void transform(float[] positions, float[] normals, int index,
                                 float scale, boolean flipY, float[] out) {
        float x = positions[index * 3];
        float y = positions[index * 3 + 1];
        float z = positions[index * 3 + 2];

        out[0] = x * scale / 16.0F;
        out[1] = (flipY ? -y : y) * scale / 16.0F;
        out[2] = z * scale / 16.0F;

        float nx = normals[index * 3];
        float ny = flipY ? -normals[index * 3 + 1] : normals[index * 3 + 1];
        float nz = normals[index * 3 + 2];
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0E-6F) {
            nx /= len;
            ny /= len;
            nz /= len;
        } else {
            nx = 0;
            ny = flipY ? -1 : 1;
            nz = 0;
        }
        out[3] = nx;
        out[4] = ny;
        out[5] = nz;
    }
}
