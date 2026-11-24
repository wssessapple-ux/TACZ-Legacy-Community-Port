package com.tacz.legacy.client.resource.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.legacy.client.resource.mesh.StaticMeshData;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Minimal glTF 2.0 static mesh parser for Blockbench-exported models. Decodes
 * POSITION / NORMAL / TEXCOORD_0 attributes and triangle indices, merging every
 * primitive of every mesh into one {@link StaticMeshData}.
 *
 * <p>Supported subset:
 * <ul>
 *   <li>buffer: inline base64 {@code data:} URI or sidecar file resolved by the caller</li>
 *   <li>accessor component types: FLOAT for attributes; UNSIGNED_BYTE / SHORT / INT for indices</li>
 *   <li>primitive mode 4 (TRIANGLES) only; STRIP / FAN are skipped</li>
 *   <li>missing normals are computed per-face (flat) by cross product</li>
 * </ul>
 * Skins, morph targets, sparse accessors and multi-material groups are not handled.
 */
public final class GltfMeshParser {

    private static final int GL_FLOAT = 5126;
    private static final int GL_UNSIGNED_BYTE = 5121;
    private static final int GL_UNSIGNED_SHORT = 5123;
    private static final int GL_UNSIGNED_INT = 5125;

    private static final int MODE_TRIANGLES = 4;

    private GltfMeshParser() {
    }

    public interface BufferResolver {
        @Nullable
        byte[] resolve(String uri) throws IOException;
    }

    public static StaticMeshData parse(String json, @Nullable BufferResolver resolver) {
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        JsonArray meshesArray = getArray(root, "meshes");
        JsonArray accessorsArray = getArray(root, "accessors");
        JsonArray bufferViewsArray = getArray(root, "bufferViews");
        JsonArray buffersArray = getArray(root, "buffers");

        List<byte[]> buffers = new ArrayList<>(buffersArray.size());
        for (JsonElement element : buffersArray) {
            buffers.add(decodeBuffer(element.getAsJsonObject(), resolver));
        }

        List<float[]> allPositions = new ArrayList<>();
        List<float[]> allUvs = new ArrayList<>();
        List<float[]> allNormals = new ArrayList<>();
        List<int[]> allIndices = new ArrayList<>();

        int vertexOffset = 0;
        // Per-primitive bookkeeping so flat normals can be filled after the merge.
        List<int[]> flatIndexSegments = new ArrayList<>();
        List<Boolean> primitiveNeedsNormals = new ArrayList<>();

        for (JsonElement meshElement : meshesArray) {
            JsonObject mesh = meshElement.getAsJsonObject();
            JsonArray primitives = getArray(mesh, "primitives");
            for (JsonElement primitiveElement : primitives) {
                JsonObject primitive = primitiveElement.getAsJsonObject();
                int mode = optionalInt(primitive, "mode", MODE_TRIANGLES);
                if (mode != MODE_TRIANGLES) {
                    continue;
                }
                JsonObject attributes = primitive.getAsJsonObject("attributes");
                if (attributes == null || !attributes.has("POSITION")) {
                    continue;
                }

                float[] positions = readFloatAccessor(accessorsArray, bufferViewsArray, buffers,
                        attributes.get("POSITION").getAsInt(), 3);
                int vertexCount = positions.length / 3;
                if (vertexCount == 0) {
                    continue;
                }

                float[] uvs = attributes.has("TEXCOORD_0")
                        ? readFloatAccessor(accessorsArray, bufferViewsArray, buffers,
                                attributes.get("TEXCOORD_0").getAsInt(), 2)
                        : new float[vertexCount * 2];

                boolean hasNormal = attributes.has("NORMAL");
                float[] normals = hasNormal
                        ? readFloatAccessor(accessorsArray, bufferViewsArray, buffers,
                                attributes.get("NORMAL").getAsInt(), 3)
                        : new float[vertexCount * 3];

                int[] indices;
                if (primitive.has("indices")) {
                    indices = readIntAccessor(accessorsArray, bufferViewsArray, buffers,
                            primitive.get("indices").getAsInt());
                } else {
                    indices = new int[vertexCount];
                    for (int i = 0; i < vertexCount; i++) {
                        indices[i] = i;
                    }
                }
                // Remap local indices to the accumulated vertex range.
                for (int i = 0; i < indices.length; i++) {
                    indices[i] += vertexOffset;
                }

                allPositions.add(positions);
                allUvs.add(uvs);
                allNormals.add(normals);
                allIndices.add(indices);
                flatIndexSegments.add(indices);
                primitiveNeedsNormals.add(!hasNormal);

                vertexOffset += vertexCount;
            }
        }

        float[] mergedPositions = concatFloat(allPositions, 3 * vertexOffset);
        float[] mergedUvs = concatFloat(allUvs, 2 * vertexOffset);
        float[] mergedNormals = concatFloat(allNormals, 3 * vertexOffset);
        int[] mergedIndices = concatInt(allIndices);

        fillFlatNormals(mergedPositions, mergedNormals, flatIndexSegments, primitiveNeedsNormals);

        return new StaticMeshData(mergedPositions, mergedUvs, mergedNormals, mergedIndices);
    }

    /** Compute a face normal per triangle and write it to the three vertices when the source lacked normals. */
    private static void fillFlatNormals(float[] positions, float[] normals,
                                        List<int[]> segments, List<Boolean> needsNormals) {
        for (int s = 0; s < segments.size(); s++) {
            if (!Boolean.TRUE.equals(needsNormals.get(s))) {
                continue;
            }
            int[] indices = segments.get(s);
            for (int i = 0; i + 2 < indices.length; i += 3) {
                int a = indices[i];
                int b = indices[i + 1];
                int c = indices[i + 2];

                float ax = positions[a * 3], ay = positions[a * 3 + 1], az = positions[a * 3 + 2];
                float bx = positions[b * 3], by = positions[b * 3 + 1], bz = positions[b * 3 + 2];
                float cx = positions[c * 3], cy = positions[c * 3 + 1], cz = positions[c * 3 + 2];

                float ux = bx - ax, uy = by - ay, uz = bz - az;
                float vx = cx - ax, vy = cy - ay, vz = cz - az;

                float nx = uy * vz - uz * vy;
                float ny = uz * vx - ux * vz;
                float nz = ux * vy - uy * vx;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len <= 1.0E-6F) {
                    nx = 0; ny = 1; nz = 0;
                } else {
                    nx /= len; ny /= len; nz /= len;
                }

                for (int v : new int[]{a, b, c}) {
                    normals[v * 3] = nx;
                    normals[v * 3 + 1] = ny;
                    normals[v * 3 + 2] = nz;
                }
            }
        }
    }

    private static byte[] decodeBuffer(JsonObject bufferObject, @Nullable BufferResolver resolver) {
        String uri = optionalString(bufferObject, "uri", "");
        if (uri.startsWith("data:")) {
            int commaIndex = uri.indexOf(',');
            if (commaIndex < 0) {
                throw new IllegalArgumentException("Invalid data URI in glTF buffer");
            }
            String metadata = uri.substring(0, commaIndex);
            String payload = uri.substring(commaIndex + 1);
            if (metadata.contains(";base64")) {
                return Base64.getDecoder().decode(payload);
            }
            return payload.getBytes(StandardCharsets.UTF_8);
        }
        if (resolver != null) {
            try {
                byte[] resolved = resolver.resolve(uri);
                if (resolved != null) {
                    return resolved;
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to resolve glTF buffer URI: " + uri, e);
            }
        }
        throw new IllegalArgumentException("Unsupported glTF buffer URI: " + uri);
    }

    private static float[] readFloatAccessor(JsonArray accessorsArray, JsonArray bufferViewsArray,
                                             List<byte[]> buffers, int accessorIndex, int componentCount) {
        JsonObject accessor = accessorsArray.get(accessorIndex).getAsJsonObject();
        if (accessor.has("sparse")) {
            throw new IllegalArgumentException("Sparse glTF accessors are not supported yet");
        }
        int componentType = accessor.get("componentType").getAsInt();
        if (componentType != GL_FLOAT) {
            throw new IllegalArgumentException("Unsupported glTF float component type: " + componentType);
        }
        int count = accessor.get("count").getAsInt();
        if (!accessor.has("bufferView")) {
            return new float[count * componentCount];
        }

        JsonObject bufferView = bufferViewsArray.get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
        byte[] buffer = buffers.get(bufferView.get("buffer").getAsInt());
        int accessorOffset = optionalInt(accessor, "byteOffset", 0);
        int bufferViewOffset = optionalInt(bufferView, "byteOffset", 0);
        int stride = optionalInt(bufferView, "byteStride", componentCount * 4);
        int elementSize = componentCount * 4;
        if (stride < elementSize) {
            stride = elementSize;
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        float[] result = new float[count * componentCount];
        for (int element = 0; element < count; element++) {
            int base = bufferViewOffset + accessorOffset + element * stride;
            for (int component = 0; component < componentCount; component++) {
                result[element * componentCount + component] = byteBuffer.getFloat(base + component * 4);
            }
        }
        return result;
    }

    private static int[] readIntAccessor(JsonArray accessorsArray, JsonArray bufferViewsArray,
                                          List<byte[]> buffers, int accessorIndex) {
        JsonObject accessor = accessorsArray.get(accessorIndex).getAsJsonObject();
        int componentType = accessor.get("componentType").getAsInt();
        int count = accessor.get("count").getAsInt();
        if (!accessor.has("bufferView")) {
            int[] fallback = new int[count];
            for (int i = 0; i < count; i++) {
                fallback[i] = i;
            }
            return fallback;
        }

        JsonObject bufferView = bufferViewsArray.get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
        byte[] buffer = buffers.get(bufferView.get("buffer").getAsInt());
        int accessorOffset = optionalInt(accessor, "byteOffset", 0);
        int bufferViewOffset = optionalInt(bufferView, "byteOffset", 0);
        int stride;
        int bytesPerIndex;
        switch (componentType) {
            case GL_UNSIGNED_BYTE:
                bytesPerIndex = 1;
                stride = optionalInt(bufferView, "byteStride", 1);
                break;
            case GL_UNSIGNED_SHORT:
                bytesPerIndex = 2;
                stride = optionalInt(bufferView, "byteStride", 2);
                break;
            case GL_UNSIGNED_INT:
                bytesPerIndex = 4;
                stride = optionalInt(bufferView, "byteStride", 4);
                break;
            default:
                throw new IllegalArgumentException("Unsupported glTF index component type: " + componentType);
        }
        if (stride < bytesPerIndex) {
            stride = bytesPerIndex;
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            int base = bufferViewOffset + accessorOffset + i * stride;
            switch (componentType) {
                case GL_UNSIGNED_BYTE:
                    result[i] = byteBuffer.get(base) & 0xFF;
                    break;
                case GL_UNSIGNED_SHORT:
                    result[i] = byteBuffer.getShort(base) & 0xFFFF;
                    break;
                default:
                    result[i] = byteBuffer.getInt(base);
                    break;
            }
        }
        return result;
    }

    private static float[] concatFloat(List<float[]> parts, int totalLength) {
        float[] result = new float[totalLength];
        int offset = 0;
        for (float[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static int[] concatInt(List<int[]> parts) {
        int total = 0;
        for (int[] part : parts) {
            total += part.length;
        }
        int[] result = new int[total];
        int offset = 0;
        for (int[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static JsonArray getArray(JsonObject object, String key) {
        JsonArray array = object.getAsJsonArray(key);
        return array == null ? new JsonArray() : array;
    }

    private static String optionalString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private static int optionalInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsInt();
    }

    @SuppressWarnings("unused")
    private static String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ENGLISH);
    }
}
