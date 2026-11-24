package com.tacz.legacy.client.model.bedrock;

import net.minecraft.client.renderer.BufferBuilder;

/**
 * 参数化 UV 球（椭球）图元。
 *
 * 与 {@link BedrockCubeBox} 同样使用 16 分制模型坐标，{@link #compile} 时除以 16。
 * 按经纬网格生成 quad，法线取球面方向（平滑曲面，不走六方向硬边法线）。
 * 包围盒约定与 box 一致：x/y/z 为最小角，width/height/depth 对应三个轴向半径的两倍。
 */
public class SphereCube implements BedrockCube {
    private final float cx;
    private final float cy;
    private final float cz;
    private final float rx;
    private final float ry;
    private final float rz;
    private final int segments;
    private final int rings;
    private final float u1;
    private final float v1;
    private final float u2;
    private final float v2;
    private final float texWidth;
    private final float texHeight;

    public SphereCube(float x, float y, float z, float width, float height, float depth,
                       int segments, int rings,
                       float u1, float v1, float u2, float v2,
                       float texWidth, float texHeight) {
        this.cx = x + width / 2.0F;
        this.cy = y + height / 2.0F;
        this.cz = z + depth / 2.0F;
        this.rx = width / 2.0F;
        this.ry = height / 2.0F;
        this.rz = depth / 2.0F;
        this.segments = Math.max(3, segments);
        this.rings = Math.max(2, rings);
        this.u1 = u1;
        this.v1 = v1;
        this.u2 = u2;
        this.v2 = v2;
        this.texWidth = texWidth;
        this.texHeight = texHeight;
    }

    @Override
    public void compile(BufferBuilder buffer) {
        double twoPi = Math.PI * 2.0;

        for (int j = 0; j < rings; j++) {
            // phi: -PI/2 (底) -> PI/2 (顶)
            double phi0 = -Math.PI / 2.0 + Math.PI * j / rings;
            double phi1 = -Math.PI / 2.0 + Math.PI * (j + 1) / rings;
            float v0 = remapV(v1, v2, (float) j / rings);
            float v1p = remapV(v1, v2, (float) (j + 1) / rings);

            for (int i = 0; i < segments; i++) {
                double th0 = twoPi * i / segments;
                double th1 = twoPi * (i + 1) / segments;
                float u0 = remapU(u1, u2, (float) i / segments);
                float u1p = remapU(u1, u2, (float) (i + 1) / segments);

                // phi0,th0 -> phi1,th0 -> phi1,th1 -> phi0,th1（CCW 朝外）
                putVertex(buffer, phi0, th0, u0, v0);
                putVertex(buffer, phi1, th0, u0, v1p);
                putVertex(buffer, phi1, th1, u1p, v1p);
                putVertex(buffer, phi0, th1, u1p, v0);
            }
        }
    }

    private void putVertex(BufferBuilder buffer, double phi, double theta, float u, float v) {
        float cosPhi = (float) Math.cos(phi);
        float sinPhi = (float) Math.sin(phi);
        float cosTh = (float) Math.cos(theta);
        float sinTh = (float) Math.sin(theta);

        float x = cx + rx * cosPhi * cosTh;
        float y = cy + ry * sinPhi;
        float z = cz + rz * cosPhi * sinTh;

        // 球面法线（椭圆近似：局部方向归一化）
        float nx = cosPhi * cosTh;
        float ny = sinPhi;
        float nz = cosPhi * sinTh;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0E-6F) {
            nx /= len;
            ny /= len;
            nz /= len;
        }

        buffer.pos(x / 16.0F, y / 16.0F, z / 16.0F)
            .tex(u, v)
            .normal(nx, ny, nz)
            .endVertex();
    }

    private float remapU(float from, float to, float t) {
        return (from + (to - from) * t) / texWidth;
    }

    private float remapV(float from, float to, float t) {
        return (from + (to - from) * t) / texHeight;
    }
}
