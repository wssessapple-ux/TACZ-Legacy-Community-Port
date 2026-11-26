package com.tacz.legacy.client.model.functional;

import com.tacz.legacy.TACZLegacy;
import com.tacz.legacy.client.model.BedrockGunModel;
import com.tacz.legacy.client.model.IFunctionalRenderer;
import com.tacz.legacy.client.model.SlotModel;
import com.tacz.legacy.client.resource.TACZClientAssetManager;
import com.tacz.legacy.client.resource.pojo.display.gun.MuzzleFlash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Renders a muzzle flash billboard at the "muzzle_flash" bone position.
 * Port of upstream TACZ MuzzleFlashRender, adapted for 1.12.2 GL immediate mode.
 * <p>
 * The flash is displayed for 50ms after a shot, with a random Z rotation
 * and scale-in animation during the first half of the display period.
 */
public class MuzzleFlashRender implements IFunctionalRenderer {
    private static final SlotModel MUZZLE_FLASH_MODEL = new SlotModel(true);
    /** 50ms display time */
    private static final long TIME_RANGE = 50;
    private static final String FOCUSED_SMOKE_PROPERTY = "tacz.focusedSmoke";

    /**
     * Frame-level context flag: whether this first-person pass belongs to the local player.
     * Set around the own-gun render in {@code FirstPersonRenderGunEvent}; third-person and
     * other players leave it false so their muzzle flash is not drawn. Global by design.
     */
    public static boolean isSelf = false;

    /** Per-instance shot state: which global shot stamp this instance already reacted to, its random Z roll, and the last logged shot. */
    private long instanceShotStamp = -1L;
    private float randomRotate = 0f;
    private long lastVisibleLogShootTimestamp = Long.MIN_VALUE;

    /** Set by the renderer before model.render() each frame. */
    @Nullable
    private MuzzleFlash activeMuzzleFlash;

    private final BedrockGunModel bedrockGunModel;

    public MuzzleFlashRender(BedrockGunModel bedrockGunModel) {
        this.bedrockGunModel = bedrockGunModel;
    }

    /**
     * Feed the global local-player shot timestamp into this instance. The random roll is
     * re-rolled only when a new shot arrives, so each gun model keeps its own flash state
     * instead of sharing one static timestamp/rotation across instances.
     */
    public void syncShotStamp(long globalShotStamp) {
        if (globalShotStamp != instanceShotStamp) {
            instanceShotStamp = globalShotStamp;
            randomRotate = (float) (Math.random() * 360);
        }
    }

    public void setActiveMuzzleFlash(@Nullable MuzzleFlash muzzleFlash) {
        this.activeMuzzleFlash = muzzleFlash;
    }

    @Override
    public void render(int light) {
        if (!isSelf) {
            return;
        }
        long time = System.currentTimeMillis() - instanceShotStamp;
        if (time > TIME_RANGE) {
            return;
        }

        if (activeMuzzleFlash == null) {
            return;
        }
        ResourceLocation texture = activeMuzzleFlash.getTexture();
        if (texture == null) {
            return;
        }
        ResourceLocation registeredTexture = TACZClientAssetManager.INSTANCE.getTextureLocation(texture);
        if (registeredTexture == null) {
            return;
        }

        if (isFocusedSmokeEnabled() && instanceShotStamp > 0L && lastVisibleLogShootTimestamp != instanceShotStamp) {
            lastVisibleLogShootTimestamp = instanceShotStamp;
            TACZLegacy.logger.info(
                    "[FocusedSmoke] MUZZLE_FLASH_VISIBLE texture={} registered={} ageMs={} scale={}",
                    texture,
                    registeredTexture,
                    time,
                    activeMuzzleFlash.getScale()
            );
        }

        float scale = 0.5f * activeMuzzleFlash.getScale();
        float scaleTime = TIME_RANGE / 2.0f;
        scale = time < scaleTime ? (scale * (time / scaleTime)) : scale;
        float previousLightX = OpenGlHelper.lastBrightnessX;
        float previousLightY = OpenGlHelper.lastBrightnessY;

        Minecraft.getMinecraft().getTextureManager().bindTexture(registeredTexture);

        try {
            // Render translucent background layer
            GlStateManager.pushMatrix();
            {
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO
                );
                GlStateManager.scale(scale, scale, scale);
                GlStateManager.rotate(randomRotate, 0f, 0f, 1f);
                GlStateManager.translate(0f, -1f, 0f);
                // Full brightness for the flash
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
                MUZZLE_FLASH_MODEL.render();
            }
            GlStateManager.popMatrix();

            // Render glow layer (smaller, additive)
            GlStateManager.pushMatrix();
            {
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                float glowScale = scale / 2f;
                GlStateManager.scale(glowScale, glowScale, glowScale);
                GlStateManager.rotate(randomRotate, 0f, 0f, 1f);
                GlStateManager.translate(0f, -0.9f, 0f);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
                MUZZLE_FLASH_MODEL.render();
                // Restore blend func
                GlStateManager.tryBlendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO
                );
            }
            GlStateManager.popMatrix();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousLightX, previousLightY);
            GlStateManager.disableBlend();
            ResourceLocation activeGunTexture = bedrockGunModel.getActiveGunTexture();
            if (activeGunTexture != null) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(activeGunTexture);
            }
        }
    }

    private static boolean isFocusedSmokeEnabled() {
        return Boolean.parseBoolean(System.getProperty(FOCUSED_SMOKE_PROPERTY, "false"));
    }
}
