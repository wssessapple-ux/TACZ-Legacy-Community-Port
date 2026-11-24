package com.tacz.legacy.client.gui

import com.tacz.legacy.common.block.entity.ChunkLoaderTileEntity
import com.tacz.legacy.common.inventory.ChunkLoaderContainer
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.client.ClientMessageChunkLoaderMode
import com.tacz.legacy.common.network.message.client.ClientMessageChunkLoaderSelect
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.resources.I18n
import net.minecraft.entity.player.InventoryPlayer
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

@SideOnly(Side.CLIENT)
internal class ChunkLoaderScreen(
    private val menu: ChunkLoaderContainer,
    playerInventory: InventoryPlayer,
) : GuiContainer(menu) {

    init {
        xSize = 176
        ySize = 120
    }

    private val ranges: IntArray get() = ChunkLoaderTileEntity.RANGES

    private lateinit var modeButton: GuiButton

    override fun initGui() {
        super.initGui()
        val btnWidth = 52
        val gap = 7
        val startX = guiLeft + 3
        ranges.forEachIndexed { index, range ->
            val button = GuiButton(
                10 + index,
                startX + index * (btnWidth + gap),
                guiTop + 68,
                btnWidth,
                20,
                "${range}×${range}",
            )
            buttonList.add(button)
        }
        modeButton = GuiButton(
            20,
            guiLeft + 38,
            guiTop + 92,
            100,
            20,
            "",
        )
        buttonList.add(modeButton)
    }

    override fun actionPerformed(button: GuiButton) {
        if (button.id == 20) {
            val current = menu.tile?.mode ?: ChunkLoaderTileEntity.MODE_REDSTONE
            val next = if (current == ChunkLoaderTileEntity.MODE_ALWAYS_ON) {
                ChunkLoaderTileEntity.MODE_REDSTONE
            } else {
                ChunkLoaderTileEntity.MODE_ALWAYS_ON
            }
            TACZNetworkHandler.sendToServer(ClientMessageChunkLoaderMode(menu.windowId, next))
            return
        }
        val index = button.id - 10
        if (index < 0 || index >= ranges.size) return
        TACZNetworkHandler.sendToServer(ClientMessageChunkLoaderSelect(menu.windowId, ranges[index]))
    }

    /** 客户端归属预判：无主 / 本人 / OP / 创造；硬校验在服务端报文里。 */
    private fun hasAccess(): Boolean {
        val te = menu.tile ?: return true
        val player = mc.player ?: return true
        val uuid = te.ownerUuid ?: return true
        return player.uniqueID == uuid || player.canUseCommand(1, "") || player.capabilities.isCreativeMode
    }

    override fun drawGuiContainerBackgroundLayer(partialTicks: Float, mouseX: Int, mouseY: Int) {
        GlStateManager.color(1f, 1f, 1f, 1f)
        // 整幅底色
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xC01E1E1E.toInt())
        // 顶部标题条
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 16, 0xFF262A31.toInt())
        drawRect(guiLeft, guiTop + 16, guiLeft + xSize, guiTop + 17, 0xFF101216.toInt())
        // 内边框：左上亮边，右下暗边
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 1, 0xFF454B57.toInt())
        drawRect(guiLeft, guiTop, guiLeft + 1, guiTop + ySize, 0xFF454B57.toInt())
        drawRect(guiLeft, guiTop + ySize - 1, guiLeft + xSize, guiTop + ySize, 0xFF0E1013.toInt())
        drawRect(guiLeft + xSize - 1, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF0E1013.toInt())
    }

    override fun drawGuiContainerForegroundLayer(mouseX: Int, mouseY: Int) {
        val te = menu.tile

        // 标题
        fontRenderer.drawStringWithShadow(
            I18n.format("block.tacz.chunk_loader"),
            10f,
            5f,
            0xFFFFFF,
        )

        // 加载范围
        val range = te?.range ?: ChunkLoaderTileEntity.DEFAULT_RANGE
        fontRenderer.drawStringWithShadow(
            I18n.format("gui.tacz.chunk_loader.range", range, range),
            10f,
            23f,
            0xFFFFFF,
        )

        // 状态：前置色块 + 状态文字
        val active = te?.active == true
        val status = I18n.format(
            if (active) "gui.tacz.chunk_loader.active" else "gui.tacz.chunk_loader.inactive",
        )
        val statusColor = if (active) 0xFF55FF55.toInt() else 0xFF8A8F98.toInt()
        drawRect(10, 34, 16, 40, statusColor)
        fontRenderer.drawStringWithShadow(status, 22f, 33f, statusColor)

        // 拥有者
        val owner = te?.ownerName ?: "—"
        fontRenderer.drawStringWithShadow(
            I18n.format("gui.tacz.chunk_loader.owner", owner),
            10f,
            46f,
            0xCCCCCC,
        )

        // 方块坐标
        val posText = te?.getPos()?.let {
            I18n.format("gui.tacz.chunk_loader.pos", it.x, it.y, it.z)
        } ?: "—"
        fontRenderer.drawStringWithShadow(posText, 10f, 59f, 0xFF9CA4B2.toInt())

        // 档位按钮：当前档禁用（高亮态），无权限全部禁用
        val access = hasAccess()
        buttonList.filter { it.id != 20 }.forEachIndexed { index, button ->
            val target = ranges[index]
            button.enabled = access && target != range
            button.displayString = "${target}×${target}"
        }

        // 模式按钮：显示当前模式，无权限禁用
        val mode = te?.mode ?: ChunkLoaderTileEntity.MODE_REDSTONE
        val modeKey = if (mode == ChunkLoaderTileEntity.MODE_ALWAYS_ON) {
            "gui.tacz.chunk_loader.mode_always_on"
        } else {
            "gui.tacz.chunk_loader.mode_redstone"
        }
        modeButton.displayString = I18n.format(
            "gui.tacz.chunk_loader.mode_label",
            I18n.format(modeKey),
        )
        modeButton.enabled = access

        // 底部版权署名（居中，暗灰）
        val copyright = I18n.format("gui.tacz.chunk_loader.copyright")
        val copyrightWidth = fontRenderer.getStringWidth(copyright)
        fontRenderer.drawStringWithShadow(
            copyright,
            (xSize - copyrightWidth) / 2f,
            106f,
            0xFF6A707C.toInt(),
        )
    }
}
