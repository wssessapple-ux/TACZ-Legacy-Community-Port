package com.tacz.legacy.client.fpv

import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderPlayerEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * FPV 期间让宿主本体模型转头/转身追随自己的无人机，营造"人站在地面抬头看飞机"的视线闭环。
 *
 * 朝向只在渲染前临时覆盖、渲染后立即还原，不写回实体持久 rotation，避免和服务端视角同步打架；
 * 相机本身挂在无人机上，改本体朝向不影响 FPV 视角。
 */
@SideOnly(Side.CLIENT)
internal object FpvSelfRenderHandler {

    private var saved = false
    private var prevRenderYawOffset: Float = 0f
    private var renderYawOffset: Float = 0f
    private var prevRotationYawHead: Float = 0f
    private var rotationYawHead: Float = 0f
    private var prevRotationPitch: Float = 0f
    private var rotationPitch: Float = 0f

    @SubscribeEvent
    fun onPre(event: RenderPlayerEvent.Pre) {
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        if (event.entity !== player || !FpvController.isFpvActive()) return
        val drone = FpvController.getActiveDrone() ?: return

        saved = true
        prevRenderYawOffset = player.prevRenderYawOffset
        renderYawOffset = player.renderYawOffset
        prevRotationYawHead = player.prevRotationYawHead
        rotationYawHead = player.rotationYawHead
        prevRotationPitch = player.prevRotationPitch
        rotationPitch = player.rotationPitch

        val dx = drone.posX - player.posX
        val dz = drone.posZ - player.posZ
        val dy = drone.posY - (player.posY + player.eyeHeight)
        val horiz = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = -Math.toDegrees(atan2(dy, horiz)).toFloat()

        player.renderYawOffset = yaw
        player.prevRenderYawOffset = yaw
        player.rotationYawHead = yaw
        player.prevRotationYawHead = yaw
        player.rotationPitch = pitch
        player.prevRotationPitch = pitch
    }

    @SubscribeEvent
    fun onPost(event: RenderPlayerEvent.Post) {
        if (!saved) return
        val player = Minecraft.getMinecraft().player ?: return
        player.prevRenderYawOffset = prevRenderYawOffset
        player.renderYawOffset = renderYawOffset
        player.prevRotationYawHead = prevRotationYawHead
        player.rotationYawHead = rotationYawHead
        player.prevRotationPitch = prevRotationPitch
        player.rotationPitch = rotationPitch
        saved = false
    }
}
