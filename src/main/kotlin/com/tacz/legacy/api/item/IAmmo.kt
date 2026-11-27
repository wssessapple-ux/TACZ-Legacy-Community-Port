package com.tacz.legacy.api.item

import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

/**
 * 弹药物品接口。与上游 TACZ IAmmo 一致。
 */
public interface IAmmo {
    /** 读取弹药注册 id。 */
    public fun getAmmoId(ammo: ItemStack): ResourceLocation

    /** 写入弹药注册 id；传 null 时不改动已有标签。 */
    public fun setAmmoId(ammo: ItemStack, ammoId: ResourceLocation?)

    /** 该弹药是否适配这把枪。 */
    public fun isAmmoOfGun(gun: ItemStack, ammo: ItemStack): Boolean

    public companion object {
        /** 物品实现 [IAmmo] 时返回其接口实例；栈为空或物品未实现时返回 null。 */
        @JvmStatic
        public fun getIAmmoOrNull(stack: ItemStack?): IAmmo? {
            if (stack == null || stack.isEmpty) return null
            val item = stack.item
            return item as? IAmmo
        }
    }
}
