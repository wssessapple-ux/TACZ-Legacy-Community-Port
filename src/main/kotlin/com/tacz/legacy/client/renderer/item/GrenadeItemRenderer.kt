package com.tacz.legacy.client.renderer.item

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.model.bedrock.BedrockModel
import com.tacz.legacy.client.renderer.gltf.GltfMeshRenderer
import com.tacz.legacy.client.resource.pojo.model.BedrockModelPOJO
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion
import com.tacz.legacy.client.resource.pojo.model.CubesItem
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.io.StringReader
import java.nio.charset.StandardCharsets

/**
 * 手雷物品 TEISR：背包 / 手里 / 掉落物直接渲染内置 bedrock 手雷模型。
 */
@SideOnly(Side.CLIENT)
internal object GrenadeItemRenderer : TileEntityItemStackRenderer() {

    private const val MODEL_PATH = "assets/${TACZLegacy.MOD_ID}/models/bedrock/grenade.json"
    private val grenadeTexture = ResourceLocation(TACZLegacy.MOD_ID, "textures/entity/grenade.png")

    private val internalModelGson = GsonBuilder()
        .registerTypeAdapter(CubesItem::class.java, CubesItem.Deserializer())
        .create()

    private var model: BedrockModel? = null
    private var modelResolved = false

    override fun renderByItem(stack: ItemStack) {
        renderByItem(stack, 1.0f)
    }

    override fun renderByItem(stack: ItemStack, partialTicks: Float) {
        Minecraft.getMinecraft().textureManager.bindTexture(grenadeTexture)

        GlStateManager.pushMatrix()
        GlStateManager.translate(0.5f, 1.5f, 0.5f)
        GlStateManager.rotate(180f, 0f, 0f, 1f)

        GlStateManager.enableLighting()
        GlStateManager.enableRescaleNormal()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
        )

        // glTF 静态网格优先；缺模型或解析为空时回退内置 bedrock 手雷。
        if (!GltfMeshRenderer.tryRenderBundled("grenade", scale = 5.0f)) {
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
        val json = GrenadeItemRenderer::class.java.classLoader
            .getResourceAsStream(MODEL_PATH)
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
        if (json == null) {
            TACZLegacy.logger.warn("[GrenadeItemRenderer] Failed to open bundled grenade model: {}", MODEL_PATH)
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
            TACZLegacy.logger.warn("[GrenadeItemRenderer] Failed to parse bundled grenade model: {}", error.message)
        }.getOrNull().also { model = it }
    }
}
