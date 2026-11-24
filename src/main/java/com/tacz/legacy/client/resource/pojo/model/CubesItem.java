package com.tacz.legacy.client.resource.pojo.model;

import com.google.common.collect.Lists;
import com.google.gson.*;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.List;

public class CubesItem {
    private List<Float> uv;
    private FaceUVsItem faceUv;
    private boolean mirror = false;
    private boolean hasMirror = false;

    @Expose
    @SerializedName("inflate")
    private float inflate;

    @Expose
    @SerializedName("size")
    private List<Float> size;

    @Expose
    @SerializedName("origin")
    private List<Float> origin;

    @Expose
    @SerializedName("rotation")
    private List<Float> rotation;

    @Expose
    @SerializedName("pivot")
    private List<Float> pivot;

    /** 几何类型：box（默认）/ cylinder（平滑圆柱）/ sphere（球）/ prism（硬边多边形棱柱）。参数化曲面图元用。 */
    @Expose
    @SerializedName("shape")
    private String shape = "box";

    /** 圆柱/球/棱柱水平分段数（棱柱即边数）。 */
    @Expose
    @SerializedName("segments")
    private int segments = 8;

    /** 球垂直纬线圈数。 */
    @Expose
    @SerializedName("rings")
    private int rings = 6;

    public List<Float> getUv() {
        return uv;
    }

    @Nullable
    public FaceUVsItem getFaceUv() {
        return faceUv;
    }

    public boolean isMirror() {
        return mirror;
    }

    public void setMirror(boolean mirror) {
        this.mirror = mirror;
    }

    public boolean isHasMirror() {
        return hasMirror;
    }

    public float getInflate() {
        return inflate;
    }

    public List<Float> getSize() {
        return size;
    }

    public List<Float> getOrigin() {
        return origin;
    }

    @Nullable
    public List<Float> getRotation() {
        return rotation;
    }

    @Nullable
    public List<Float> getPivot() {
        return pivot;
    }

    public String getShape() {
        return shape == null ? "box" : shape;
    }

    public int getSegments() {
        return segments;
    }

    public int getRings() {
        return rings;
    }

    public static class Deserializer implements JsonDeserializer<CubesItem> {
        @Override
        public CubesItem deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            CubesItem cube = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(json, CubesItem.class);
            if (json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();

                JsonElement uvElement = obj.get("uv");
                if (uvElement.isJsonArray()) {
                    cube.uv = Lists.newArrayList();
                    JsonArray array = uvElement.getAsJsonArray();
                    for (int i = 0; i < array.size(); i++) {
                        cube.uv.add(array.get(i).getAsFloat());
                    }
                }
                if (uvElement.isJsonObject()) {
                    cube.faceUv = new Gson().fromJson(uvElement, FaceUVsItem.class);
                }

                JsonElement mirrorElement = obj.get("mirror");
                if (mirrorElement != null && mirrorElement.isJsonPrimitive()) {
                    JsonPrimitive primitive = mirrorElement.getAsJsonPrimitive();
                    if (primitive.isBoolean()) {
                        cube.mirror = primitive.getAsBoolean();
                        cube.hasMirror = true;
                    }
                }
            }
            return cube;
        }
    }
}
