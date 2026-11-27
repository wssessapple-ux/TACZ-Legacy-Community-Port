package com.tacz.legacy.api.item.gun

import net.minecraft.item.Item
import java.util.LinkedHashMap

/** 枪械物品注册表：按类型名登记对应的物品实例，供附属模组查询已注册枪械。 */
public object GunItemManager {
    private val gunItems: MutableMap<String, Item> = LinkedHashMap()

    /** 登记一个枪械物品类型。 */
    @JvmStatic
    public fun registerGunItem(typeName: String, item: Item): Unit {
        gunItems[typeName] = item
    }

    /** 按类型名查询已注册枪械物品；未登记时返回 null。 */
    @JvmStatic
    public fun getRegisteredGunItem(typeName: String): Item? = gunItems[typeName]

    /** 返回当前注册表的只读快照（类型名 → 物品实例）。 */
    @JvmStatic
    public fun snapshot(): Map<String, Item> = LinkedHashMap(gunItems)

    /** 清空注册表（重载枪包时调用）。 */
    @JvmStatic
    public fun clear(): Unit {
        gunItems.clear()
    }
}
