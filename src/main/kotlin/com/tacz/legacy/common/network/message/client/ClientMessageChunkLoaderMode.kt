package com.tacz.legacy.common.network.message.client

import com.tacz.legacy.common.block.entity.ChunkLoaderTileEntity
import com.tacz.legacy.common.inventory.ChunkLoaderContainer
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * C2S: 区块加载器面板切换工作模式（红石控制 / 常开）。
 */
public class ClientMessageChunkLoaderMode() : IMessage, IMessageHandler<ClientMessageChunkLoaderMode, IMessage?> {
    private var windowId: Int = -1
    private var mode: Int = ChunkLoaderTileEntity.MODE_REDSTONE

    public constructor(windowId: Int, mode: Int) : this() {
        this.windowId = windowId
        this.mode = mode
    }

    override fun fromBytes(buf: ByteBuf) {
        windowId = buf.readInt()
        mode = buf.readInt()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(windowId)
        buf.writeInt(mode)
    }

    override fun onMessage(message: ClientMessageChunkLoaderMode, ctx: MessageContext): IMessage? {
        val player: EntityPlayerMP = ctx.serverHandler.player
        player.serverWorld.addScheduledTask {
            val container = player.openContainer as? ChunkLoaderContainer ?: return@addScheduledTask
            if (container.windowId != message.windowId || !container.canInteractWith(player)) {
                return@addScheduledTask
            }
            val tile = container.tile ?: return@addScheduledTask
            if (!tile.hasAccess(player)) {
                return@addScheduledTask
            }
            if (message.mode != ChunkLoaderTileEntity.MODE_REDSTONE &&
                message.mode != ChunkLoaderTileEntity.MODE_ALWAYS_ON
            ) {
                return@addScheduledTask
            }
            tile.setMode(message.mode)
        }
        return null
    }
}
