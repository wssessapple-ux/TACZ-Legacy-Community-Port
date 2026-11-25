package com.tacz.legacy.client.resource.mesh;

import com.tacz.legacy.client.model.bedrock.BedrockCube;
import com.tacz.legacy.client.model.bedrock.BedrockCubeBox;
import com.tacz.legacy.client.model.bedrock.BedrockCubePerFace;
import com.tacz.legacy.client.model.bedrock.BedrockModel;
import com.tacz.legacy.client.model.bedrock.BedrockPart;
import com.tacz.legacy.client.model.bedrock.BedrockPolygon;
import com.tacz.legacy.client.model.bedrock.BedrockVertex;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Bakes a static bedrock model's box/per-face cubes into a {@link StaticMeshData}
 * the shared VBO pipeline can upload, so non-first-person static scenes do not
 * have to walk the bone hierarchy through the immediate-mode batch every frame.
 *
 * <p>Scope (first cut):
 * <ul>
 *   <li>Only {@link BedrockCubeBox} and {@link BedrockCubePerFace} geometry is
 *       extracted. Parametric cylinders / spheres / prisms are unsupported: the
 *       whole model bakes to {@code null} and the caller falls back to immediate
 *       mode for it.</li>
 *   <li>The bake replays the bone matrix stack on the CPU (translate offset,
 *       translate pivot/16, rotate Z/Y/X, extra quaternion, scale). Vertices are
 *       stored in block units (divided by 16, matching the bedrock immediate-mode
 *       path) and are NOT Y-flipped; the wrapped {@link com.tacz.legacy.client.model.mesh.Mesh}
 *       should be configured with flipY disabled.</li>
 *   <li>Initial {@code visible = false} subtrees are skipped; per-frame animated
 *       hides, temporary part reveals, bloom-only illuminants, beams and shell
 *       ejectors are not part of the baked geometry.</li>
 * </ul>
 */
public final class BedrockMeshBaker {

    private BedrockMeshBaker() {
    }

    /**
     * @return baked mesh, or {@code null} when the model is empty or contains an
     *         unsupported (curved) cube the caller must render via immediate mode.
     */
    @Nullable
    public static StaticMeshData bake(BedrockModel model) {
        return bakeRoots(model.getShouldRender());
    }

    /** Package-visible root overload for direct part-tree tests. */
    @Nullable
    static StaticMeshData bakeRoots(List<BedrockPart> roots) {
        List<Float> positions = new ArrayList<>();
        List<Float> uvs = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        boolean[] unsupported = {false};

        Matrix4f identity = new Matrix4f();
        for (BedrockPart root : roots) {
            walk(root, identity, positions, uvs, normals, indices, unsupported);
            if (unsupported[0]) {
                return null;
            }
        }

        if (indices.isEmpty()) {
            return null;
        }
        return new StaticMeshData(
                toFloatArray(positions, 3),
                toFloatArray(uvs, 2),
                toFloatArray(normals, 3),
                toIntArray(indices));
    }

    private static void walk(BedrockPart part, Matrix4f parent,
                             List<Float> positions, List<Float> uvs, List<Float> normals,
                             List<Integer> indices, boolean[] unsupported) {
        if (!part.visible || unsupported[0]) {
            return;
        }

        // Replicate BedrockPart.translateAndRotateAndScale() on the CPU matrix stack.
        Matrix4f local = new Matrix4f();
        local.translate(part.offsetX, part.offsetY, part.offsetZ);
        local.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
        local.rotateZ(part.zRot);
        local.rotateY(part.yRot);
        local.rotateX(part.xRot);
        if (!(part.additionalQuaternion.x == 0 && part.additionalQuaternion.y == 0
                && part.additionalQuaternion.z == 0 && part.additionalQuaternion.w == 1)) {
            local.rotate(part.additionalQuaternion);
        }
        local.scale(part.xScale, part.yScale, part.zScale);

        Matrix4f world = new Matrix4f(parent).mul(local);

        for (BedrockCube cube : part.cubes) {
            BedrockPolygon[] polygons;
            if (cube instanceof BedrockCubeBox) {
                polygons = ((BedrockCubeBox) cube).getPolygons();
            } else if (cube instanceof BedrockCubePerFace) {
                polygons = ((BedrockCubePerFace) cube).getPolygons();
            } else {
                unsupported[0] = true;
                return;
            }
            extractPolygons(polygons, world, positions, uvs, normals, indices);
        }

        for (BedrockPart child : part.children) {
            walk(child, world, positions, uvs, normals, indices, unsupported);
        }
    }

    private static void extractPolygons(BedrockPolygon[] polygons, Matrix4f world,
                                        List<Float> positions, List<Float> uvs, List<Float> normals,
                                        List<Integer> indices) {
        for (BedrockPolygon polygon : polygons) {
            if (polygon == null) {
                continue;
            }
            BedrockVertex[] verts = polygon.vertices;
            if (verts == null || verts.length < 4 || isDegenerate(verts)) {
                continue;
            }
            int base = positions.size() / 3;
            Vector3f normal = polygon.normal;

            for (BedrockVertex vertex : polygon.vertices) {
                Vector3f point = new Vector3f(
                        vertex.pos.x() / 16.0F,
                        vertex.pos.y() / 16.0F,
                        vertex.pos.z() / 16.0F);
                world.transformPosition(point);

                Vector3f worldNormal = new Vector3f(normal);
                world.transformDirection(worldNormal);
                if (worldNormal.lengthSquared() <= 1.0E-8F) {
                    worldNormal.set(0.0F, 1.0F, 0.0F);
                } else {
                    worldNormal.normalize();
                }

                positions.add(point.x);
                positions.add(point.y);
                positions.add(point.z);
                uvs.add(vertex.u);
                uvs.add(vertex.v);
                normals.add(worldNormal.x);
                normals.add(worldNormal.y);
                normals.add(worldNormal.z);
            }

            // Quad fan split: (0,1,2) and (0,2,3).
            indices.add(base);
            indices.add(base + 1);
            indices.add(base + 2);
            indices.add(base);
            indices.add(base + 2);
            indices.add(base + 3);
        }
    }

    /** Per-face cubes mark an unused face with four coincident EMPTY vertices; skip those zero-area quads. */
    private static boolean isDegenerate(BedrockVertex[] verts) {
        Vector3f a = verts[0].pos;
        for (int i = 1; i < verts.length; i++) {
            Vector3f b = verts[i].pos;
            if (a.x() != b.x() || a.y() != b.y() || a.z() != b.z()) {
                return false;
            }
        }
        return true;
    }

    private static float[] toFloatArray(List<Float> list, int stride) {
        float[] result = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        if (stride != 0 && result.length % stride != 0) {
            throw new IllegalStateException("Mesh float array length not a multiple of stride " + stride);
        }
        return result;
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] result = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }
}
