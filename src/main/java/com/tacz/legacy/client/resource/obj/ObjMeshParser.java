package com.tacz.legacy.client.resource.obj;

import com.tacz.legacy.client.resource.mesh.StaticMeshData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Wavefront OBJ static mesh parser. Decodes {@code v} / {@code vt} /
 * {@code vn} / {@code f} and produces the same {@link StaticMeshData} the glTF
 * parser emits, so the shared mesh render wrapper draws both formats unchanged.
 *
 * <p>Supported subset:
 * <ul>
 *   <li>face references {@code v}, {@code v/vt}, {@code v/vt/vn}, {@code v//vn};
 *       1-based and negative (relative-to-end) indices</li>
 *   <li>polygon faces triangulated by fan split ({@code 0, k, k+1})</li>
 *   <li>missing normals are computed per-face (flat) by cross product</li>
 *   <li>missing texture coordinates default to (0, 0)</li>
 * </ul>
 * Ignored: {@code o} / {@code g} groups, smoothing groups {@code s},
 * {@code mtllib} / {@code usemtl} materials, lines and points. Multi-material
 * groups and subdivision are not handled.
 */
public final class ObjMeshParser {

    private ObjMeshParser() {
    }

    public static StaticMeshData parse(@Nonnull String objText) {
        List<float[]> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();

        List<Float> outPos = new ArrayList<>();
        List<Float> outUv = new ArrayList<>();
        List<Float> outNormal = new ArrayList<>();
        List<Integer> outIndices = new ArrayList<>();
        Map<Long, Integer> vertexMap = new HashMap<>();

        for (String rawLine : objText.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            String tag = parts[0];
            switch (tag) {
                case "v":
                    positions.add(new float[]{
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])});
                    break;
                case "vt":
                    uvs.add(new float[]{
                            Float.parseFloat(parts[1]),
                            parts.length > 2 ? Float.parseFloat(parts[2]) : 0.0F});
                    break;
                case "vn":
                    normals.add(new float[]{
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])});
                    break;
                case "f":
                    addFace(parts, positions, uvs, normals, vertexMap,
                            outPos, outUv, outNormal, outIndices);
                    break;
                default:
                    // o, g, s, mtllib, usemtl, l, bevel, curv and other directives are ignored.
                    break;
            }
        }

        if (normals.isEmpty()) {
            fillFlatNormals(outPos, outNormal, outIndices);
        }

        return new StaticMeshData(
                toFloatArray(outPos, 3),
                toFloatArray(outUv, 2),
                toFloatArray(outNormal, 3),
                toIntArray(outIndices));
    }

    private static void addFace(String[] parts, List<float[]> positions, List<float[]> uvs,
                                List<float[]> normals, Map<Long, Integer> vertexMap,
                                List<Float> outPos, List<Float> outUv, List<Float> outNormal,
                                List<Integer> outIndices) {
        int faceCount = parts.length - 1;
        if (faceCount < 3) {
            return;
        }

        int[][] refs = new int[faceCount][3];
        for (int i = 0; i < faceCount; i++) {
            String[] segs = parts[i + 1].split("/", -1);
            int pi = parseIndex(segs[0], positions.size());
            int ti = segs.length > 1 ? parseIndex(segs[1], uvs.size()) : -1;
            int ni = segs.length > 2 ? parseIndex(segs[2], normals.size()) : -1;
            if (pi < 0 || pi >= positions.size()) {
                return; // face references an out-of-range position index; skip it
            }
            refs[i] = new int[]{pi, ti, ni};
        }

        // Fan triangulation: (0, 1, 2), (0, 2, 3), ...
        for (int k = 1; k + 1 < faceCount; k++) {
            outIndices.add(mergeVertex(refs[0], positions, uvs, normals, vertexMap, outPos, outUv, outNormal));
            outIndices.add(mergeVertex(refs[k], positions, uvs, normals, vertexMap, outPos, outUv, outNormal));
            outIndices.add(mergeVertex(refs[k + 1], positions, uvs, normals, vertexMap, outPos, outUv, outNormal));
        }
    }

    private static int mergeVertex(int[] ref, List<float[]> positions, List<float[]> uvs,
                                   List<float[]> normals, Map<Long, Integer> vertexMap,
                                   List<Float> outPos, List<Float> outUv, List<Float> outNormal) {
        int pi = ref[0];
        int ti = ref[1];
        int ni = ref[2];

        // ti / ni are -1 when absent; offset by one so the key stays non-negative.
        long key = ((long) pi << 40) | ((long) (ti + 1) << 20) | (ni + 1);
        Integer existing = vertexMap.get(key);
        if (existing != null) {
            return existing;
        }

        float[] p = positions.get(pi);
        outPos.add(p[0]);
        outPos.add(p[1]);
        outPos.add(p[2]);

        float[] uv = ti >= 0 && ti < uvs.size() ? uvs.get(ti) : new float[]{0.0F, 0.0F};
        outUv.add(uv[0]);
        outUv.add(uv[1]);

        float[] n = ni >= 0 && ni < normals.size() ? normals.get(ni) : new float[]{0.0F, 1.0F, 0.0F};
        outNormal.add(n[0]);
        outNormal.add(n[1]);
        outNormal.add(n[2]);

        int index = outPos.size() / 3 - 1;
        vertexMap.put(key, index);
        return index;
    }

    /** Resolve a 1-based OBJ index; negative values count back from the end. */
    private static int parseIndex(String token, int count) {
        if (token == null || token.isEmpty()) {
            return -1;
        }
        int value = Integer.parseInt(token.trim());
        if (count == 0) {
            return -1;
        }
        return value < 0 ? count + value : value - 1;
    }

    /** Compute a face normal per triangle and write it to the three vertices. */
    private static void fillFlatNormals(List<Float> pos, List<Float> normal, List<Integer> indices) {
        for (int i = 0; i + 2 < indices.size(); i += 3) {
            int a = indices.get(i);
            int b = indices.get(i + 1);
            int c = indices.get(i + 2);

            float ax = pos.get(a * 3), ay = pos.get(a * 3 + 1), az = pos.get(a * 3 + 2);
            float bx = pos.get(b * 3), by = pos.get(b * 3 + 1), bz = pos.get(b * 3 + 2);
            float cx = pos.get(c * 3), cy = pos.get(c * 3 + 1), cz = pos.get(c * 3 + 2);

            float ux = bx - ax, uy = by - ay, uz = bz - az;
            float vx = cx - ax, vy = cy - ay, vz = cz - az;

            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len <= 1.0E-6F) {
                nx = 0;
                ny = 1;
                nz = 0;
            } else {
                nx /= len;
                ny /= len;
                nz /= len;
            }

            for (int v : new int[]{a, b, c}) {
                normal.set(v * 3, nx);
                normal.set(v * 3 + 1, ny);
                normal.set(v * 3 + 2, nz);
            }
        }
    }

    private static float[] toFloatArray(List<Float> list, int stride) {
        float[] result = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        // stride is retained for symmetry with the glTF parser; vertex count = length / stride.
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
