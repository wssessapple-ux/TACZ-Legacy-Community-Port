package com.tacz.legacy.client.model.bedrock;

import net.minecraft.client.renderer.BufferBuilder;

/**
 * 参数化圆柱（椭圆柱）图元，沿 Y 轴。
 *
 * 与 {@link BedrockCubeBox} 同样使用 16 分制模型坐标，{@link #compile} 时除以 16。
 * 侧面按 {@code segments} 个扇区展开，法线取水平径向（平滑曲面，不走 BedrockPolygon 的六方向硬边法线）；
 * 顶盖/底盖用退化 quad 三角扇封口。UV 第一版按柱面展开：侧面 u 沿环绕、v 沿高度；盖面按圆形径向映射。
 *
 * 包围盒约定与 box 一致：x/y/z 为最小角，width/depth 决定椭圆半径，height 决定柱高。
 */
public class CylinderCube implements BedrockCube {
    private final float cx;
    private final float cy;
    private final float cz;
    private final float rx;
    private final float rz;
    private final float height;
    private final int segments;
    private final boolean caps;
    private final float u1;
    private final float v1;
    private final float u2;
    private final float v2;
    private final float texWidth;
    private final float texHeight;

    public CylinderCube(float x, float y, float z, float width, float height, float depth,
                        int segments, boolean caps,
                        float u1, float v1, float u2, float v2,
                        float texWidth, float texHeight) {
        this.cx = x + width / 2.0F;
        this.cy = y;
        this.cz = z + depth / 2.0F;
        this.rx = width / 2.0F;
        this.rz = depth / 2.0F;
        this.height = height;
        this.segments = Math.max(3, segments);
        this.caps = caps;
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

        // 侧面：相邻两圈圆环点组成扇区 quad
        for (int i = 0; i < segments; i++) {
            double a0 = twoPi * i / segments;
            double a1 = twoPi * (i + 1) / segments;
            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);

            float bx0 = cx + rx * cos0;
            float bz0 = cz + rz * sin0;
            float bx1 = cx + rx * cos1;
            float bz1 = cz + rz * sin1;
            float ty = cy + height;

            float u0 = remapU(u1, u2, (float) i / segments);
            float u1p = remapU(u1, u2, (float) (i + 1) / segments);
            float vBot = remapV(v1, v2, 0.0F);
            float vTop = remapV(v1, v2, 1.0F);

            // 外法线径向（椭圆近似）
            float n0x = cos0;
            float n0z = sin0;
            float n1x = cos1;
            float n1z = sin1;

            // 底 a0 -> 顶 a0 -> 顶 a1 -> 底 a1（CCW 朝外，背面剔除下正面可见）
            putVertex(buffer, bx0, cy, bz0, u0, vBot, n0x, 0.0F, n0z);
            putVertex(buffer, bx0, ty, bz0, u0, vTop, n0x, 0.0F, n0z);
            putVertex(buffer, bx1, ty, bz1, u1p, vTop, n1x, 0.0F, n1z);
            putVertex(buffer, bx1, cy, bz1, u1p, vBot, n1x, 0.0F, n1z);
        }

        if (caps) {
            compileCap(buffer, true);
            compileCap(buffer, false);
        }
    }

    /** 顶盖/底盖三角扇：退化 quad（中心、边 B、边 A、中心）。 */
    private void compileCap(BufferBuilder buffer, boolean top) {
        float cyCap = top ? cy + height : cy;
        float nx = 0.0F;
        float ny = top ? 1.0F : -1.0F;
        float nz = 0.0F;
        double twoPi = Math.PI * 2.0;
        float uCenter = remapU(u1, u2, 0.5F);
        float vCenter = remapV(v1, v2, 0.5F);

        for (int i = 0; i < segments; i++) {
            double a0 = twoPi * i / segments;
            double a1 = twoPi * (i + 1) / segments;
            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);

            float x0 = cx + rx * cos0;
            float z0 = cz + rz * sin0;
            float x1 = cx + rx * cos1;
            float z1 = cz + rz * sin1;

            float u0 = remapU(u1, u2, cos0 * 0.5F + 0.5F);
            float v0 = remapV(v1, v2, sin0 * 0.5F + 0.5F);
            float u1p = remapU(u1, u2, cos1 * 0.5F + 0.5F);
            float v1p = remapV(v1, v2, sin1 * 0.5F + 0.5F);

            if (top) {
                // 中心 -> 边1 -> 边0 -> 中心
                putVertex(buffer, cx, cyCap, cz, uCenter, vCenter, nx, ny, nz);
                putVertex(buffer, x1, cyCap, z1, u1p, v1p, nx, ny, nz);
                putVertex(buffer, x0, cyCap, z0, u0, v0, nx, ny, nz);
                putVertex(buffer, cx, cyCap, cz, uCenter, vCenter, nx, ny, nz);
            } else {
                putVertex(buffer, cx, cyCap, cz, uCenter, vCenter, nx, ny, nz);
                putVertex(buffer, x0, cyCap, z0, u0, v0, nx, ny, nz);
                putVertex(buffer, x1, cyCap, z1, u1p, v1p, nx, ny, nz);
                putVertex(buffer, cx, cyCap, cz, uCenter, vCenter, nx, ny, nz);
            }
        }
    }

    private void putVertex(BufferBuilder buffer, float x, float y, float z,
                           float u, float v, float nx, float ny, float nz) {
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
