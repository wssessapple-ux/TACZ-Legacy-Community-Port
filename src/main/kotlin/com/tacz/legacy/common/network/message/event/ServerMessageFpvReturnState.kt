package com.tacz.legacy.common.network.message.event

import com.tacz.legacy.client.fpv.FpvController
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * S2C: 同步某架 FPV 是否处于自主返航状态，供客户端切换返航键的 toggle 方向与 HUD 提示。
 */
public class ServerMessageFpvReturnState() : IMessage {
    private var droneId: Int = -1
    private var returning: Boolean = false

    public constructor(droneId: Int, returning: Boolean) : this() {
        this.droneId = droneId
        this.returning = returning
    }

    override fun fromBytes(buf: ByteBuf) {
        droneId = buf.readInt()
        returning = buf.readBoolean()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(droneId)
        buf.writeBoolean(returning)
    }

    public class Handler : IMessageHandler<ServerMessageFpvReturnState, IMessage?> {
        override fun onMessage(message: ServerMessageFpvReturnState, ctx: MessageContext): IMessage? {
            val mc = Minecraft.getMinecraft()
            mc.addScheduledTask {
                FpvController.setReturning(message.returning)
            }
            return null
        }
    }
}
