package com.tacz.legacy.client.renderer.entity

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.model.bedrock.BedrockModel
import com.tacz.legacy.client.renderer.gltf.GltfMeshRenderer
import com.tacz.legacy.client.resource.pojo.model.BedrockModelPOJO
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion
import com.tacz.legacy.client.resource.pojo.model.CubesItem
import com.tacz.legacy.common.entity.EntityGrenade
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.entity.Render
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.io.StringReader
import java.nio.charset.StandardCharsets
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 破片手雷实体渲染器。内置 bedrock 模型（P0 圆柱弹体），随飞行方向朝向，
 * 飞行中绕前向轴滚动。
 */
@SideOnly(Side.CLIENT)
internal class RenderGrenade(renderManager: RenderManager) : Render<EntityGrenade>(renderManager) {

    companion object {
        private const val MODEL_PATH = "assets/${TACZLegacy.MOD_ID}/models/bedrock/grenade.json"
        private val grenadeTexture = ResourceLocation(TACZLegacy.MOD_ID, "textures/entity/grenade.png")

        private val internalModelGson = GsonBuilder()
            .registerTypeAdapter(CubesItem::class.java, CubesItem.Deserializer())
            .create()

        private var model: BedrockModel? = null
        private var modelResolved = false
        private const val ROLL_SPEED = 12.0
    }

    override fun doRender(
        entity: EntityGrenade,
        x: Double,
        y: Double,
        z: Double,
        entityYaw: Float,
        partialTicks: Float,
    ) {
        Minecraft.getMinecraft().textureManager.bindTexture(grenadeTexture)

        val mx = entity.motionX.toFloat()
        val my = entity.motionY.toFloat()
        val mz = entity.motionZ.toFloat()
        val hDist = sqrt(mx * mx + mz * mz)

        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, z)

        if (hDist > 1.0E-4f) {
            val yaw = Math.toDegrees(atan2(mx, mz).toDouble()).toFloat()
            val pitch = -Math.toDegrees(atan2(my, hDist).toDouble()).toFloat()
            GlStateManager.rotate(yaw, 0f, 1f, 0f)
            GlStateManager.rotate(pitch, 1f, 0f, 0f)
        }

        // 飞行中绕前向轴滚动（frag 出手翻滚）。
        val tickTime = entity.ticksExisted + partialTicks
        GlStateManager.rotate((tickTime * ROLL_SPEED).toFloat(), 1f, 0f, 0f)

        GlStateManager.enableLighting()
        GlStateManager.enableRescaleNormal()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)

        // glTF 网格自带居中与 Y 翻转，不再套 bedrock 的 translate(0,1.5,0)/scale(-1,-1,1)，
        // 否则法线被负缩放二次翻转导致整体发黑。
        if (!GltfMeshRenderer.tryRenderBundled("grenade", scale = 5.0f)) {
            // bedrock 模型专用变换三明治。
            GlStateManager.translate(0.0f, 1.5f, 0.0f)
            GlStateManager.scale(-1.0f, -1.0f, 1.0f)
            val grenadeModel = resolveModel() ?: run {
                GlStateManager.disableBlend()
                GlStateManager.disableRescaleNormal()
                GlStateManager.popMatrix()
                return
            }
            grenadeModel.render()
        }

        GlStateManager.disableBlend()
        GlStateManager.disableRescaleNormal()
        GlStateManager.popMatrix()
    }

    private fun resolveModel(): BedrockModel? {
        if (modelResolved) {
            return model
        }
        modelResolved = true
        val json = RenderGrenade::class.java.classLoader
            .getResourceAsStream(MODEL_PATH)
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
        if (json == null) {
            TACZLegacy.logger.warn("[RenderGrenade] Failed to open bundled grenade model: {}", MODEL_PATH)
            return null
        }
        return runCatching {
            val reader = JsonReader(StringReader(json))
            reader.isLenient = true
            val pojo = internalModelGson.fromJson<BedrockModelPOJO>(reader, BedrockModelPOJO::class.java)
            val version = BedrockVersion.fromPojo(pojo)
                ?: error("unknown bedrock format version ${pojo.formatVersion}")
            BedrockModel(pojo, version)
        }.onFailure { error ->
            TACZLegacy.logger.warn("[RenderGrenade] Failed to parse bundled grenade model: {}", error.message)
        }.getOrNull().also { model = it }
    }

    override fun getEntityTexture(entity: EntityGrenade): ResourceLocation = TextureMap.LOCATION_MISSING_TEXTURE
}
