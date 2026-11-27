package com.tacz.legacy.api.item

import com.tacz.legacy.api.item.attachment.AttachmentType
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

/**
 * 配件 NBT 访问接口。
 * 当前先对齐工匠台与手持过滤所需的最小上游行为：配件 id 与配件类型。
 */
public interface IAttachment {
    /** 读取配件注册 id。 */
    public fun getAttachmentId(stack: ItemStack): ResourceLocation

    /** 写入配件注册 id；传 null 时不改动已有标签。 */
    public fun setAttachmentId(stack: ItemStack, attachmentId: ResourceLocation?)

    /** 读取倍镜档位（变焦序号）。 */
    public fun getZoomNumber(stack: ItemStack): Int

    /** 写入倍镜档位（变焦序号）。 */
    public fun setZoomNumber(stack: ItemStack, zoomNumber: Int)

    /** 读取配件类型。 */
    public fun getType(stack: ItemStack): AttachmentType

    /** 是否使用了自定义激光颜色（否则取默认色）。 */
    public fun hasCustomLaserColor(stack: ItemStack): Boolean

    /** 读取激光颜色（ARGB 整数）。 */
    public fun getLaserColor(stack: ItemStack): Int

    /** 写入激光颜色（ARGB 整数）。 */
    public fun setLaserColor(stack: ItemStack, color: Int)

    public companion object {
        /** 物品实现 [IAttachment] 时返回其接口实例；栈为空或物品未实现时返回 null。 */
        @JvmStatic
        public fun getIAttachmentOrNull(stack: ItemStack?): IAttachment? = stack?.item as? IAttachment
    }
}