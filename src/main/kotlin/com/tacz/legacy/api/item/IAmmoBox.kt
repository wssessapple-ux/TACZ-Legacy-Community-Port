package com.tacz.legacy.api.item

import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

/**
 * 子弹盒接口。与上游 TACZ IAmmoBox 一致。
 */
public interface IAmmoBox {
    /** 读取弹盒内弹药的注册 id。 */
    public fun getAmmoId(ammoBox: ItemStack): ResourceLocation

    /** 写入弹盒内弹药的注册 id。 */
    public fun setAmmoId(ammoBox: ItemStack, ammoId: ResourceLocation)

    /** 读取弹盒内弹药数量。 */
    public fun getAmmoCount(ammoBox: ItemStack): Int

    /** 写入弹盒内弹药数量。 */
    public fun setAmmoCount(ammoBox: ItemStack, count: Int)

    /** 该弹盒是否装填适配这把枪的弹药。 */
    public fun isAmmoBoxOfGun(gun: ItemStack, ammoBox: ItemStack): Boolean

    /** 是否为创造弹盒（取出弹药不消耗弹盒存量）。 */
    public fun isCreative(ammoBox: ItemStack): Boolean

    /** 是否为全类型创造弹盒（可装填任意弹药且不消耗）。 */
    public fun isAllTypeCreative(ammoBox: ItemStack): Boolean
}
