package com.tacz.legacy.common.network.message.client

import com.tacz.legacy.common.block.entity.ChunkLoaderTileEntity
import com.tacz.legacy.common.inventory.ChunkLoaderContainer
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * C2S: 区块加载器面板直接选定加载范围档位。
 */
public class ClientMessageChunkLoaderSelect() : IMessage, IMessageHandler<ClientMessageChunkLoaderSelect, IMessage?> {
    private var windowId: Int = -1
    private var targetRange: Int = -1

    public constructor(windowId: Int, targetRange: Int) : this() {
        this.windowId = windowId
        this.targetRange = targetRange
    }

    override fun fromBytes(buf: ByteBuf) {
        windowId = buf.readInt()
        targetRange = buf.readInt()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(windowId)
        buf.writeInt(targetRange)
    }

    override fun onMessage(message: ClientMessageChunkLoaderSelect, ctx: MessageContext): IMessage? {
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
            if (message.targetRange !in ChunkLoaderTileEntity.RANGES) {
                return@addScheduledTask
            }
            tile.setRange(message.targetRange)
        }
        return null
    }
}
