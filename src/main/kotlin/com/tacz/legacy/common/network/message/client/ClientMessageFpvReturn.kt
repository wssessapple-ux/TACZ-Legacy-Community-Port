package com.tacz.legacy.common.network.message.client

import com.tacz.legacy.common.entity.EntityFpvDrone
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.event.ServerMessageFpvReturnState
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.text.TextComponentTranslation
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * C2S: 一键返航 / 取消返航。
 *
 * - [cancel] = false：受控态发起自主返航，无人机分段飞回家人附近，到家悬停待接管；
 * - [cancel] = true：返航途中取消，切回手动操控，视角保持不变。
 *
 * 服务端校验归属与状态后，回 [ServerMessageFpvReturnState] 同步客户端返航标志。
 */
public class ClientMessageFpvReturn() : IMessage, IMessageHandler<ClientMessageFpvReturn, IMessage?> {
    private var droneId: Int = -1
    private var cancel: Boolean = false

    public constructor(droneId: Int, cancel: Boolean) : this() {
        this.droneId = droneId
        this.cancel = cancel
    }

    override fun fromBytes(buf: ByteBuf) {
        droneId = buf.readInt()
        cancel = buf.readBoolean()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(droneId)
        buf.writeBoolean(cancel)
    }

    override fun onMessage(message: ClientMessageFpvReturn, ctx: MessageContext): IMessage? {
        val player: EntityPlayerMP = ctx.serverHandler.player
        player.serverWorld.addScheduledTask {
            val drone = player.serverWorld.getEntityByID(message.droneId) as? EntityFpvDrone ?: return@addScheduledTask
            if (!drone.isOwner(player)) return@addScheduledTask

            if (message.cancel) {
                if (drone.cancelReturn()) {
                    TACZNetworkHandler.sendToPlayer(ServerMessageFpvReturnState(drone.entityId, returning = false), player)
                    player.sendMessage(TextComponentTranslation("message.tacz.fpv.return_cancelled"))
                }
            } else {
                if (drone.startReturn()) {
                    TACZNetworkHandler.sendToPlayer(ServerMessageFpvReturnState(drone.entityId, returning = true), player)
                    player.sendMessage(TextComponentTranslation("message.tacz.fpv.returning"))
                }
            }
        }
        return null
    }
}
