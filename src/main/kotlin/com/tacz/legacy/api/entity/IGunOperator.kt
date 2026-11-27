package com.tacz.legacy.api.entity

import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import java.util.function.Supplier

/**
 * 枪械操作者接口，由 LivingEntity 通过 Mixin 实现。
 * 与上游 TACZ IGunOperator 行为保持一致。
 */
public interface IGunOperator {
    public fun getSynShootCoolDown(): Long
    public fun getSynMeleeCoolDown(): Long
    public fun getSynDrawCoolDown(): Long
    public fun getSynIsBolting(): Boolean
    public fun getSynReloadState(): ReloadState
    public fun getSynAimingProgress(): Float
    public fun getSynIsAiming(): Boolean
    public fun getSynSprintTime(): Float

    /** 当前是否处于趴下（crawl）状态。 */
    public fun getSynIsCrawling(): Boolean

    /** 射击时间基准戳（毫秒），用于计算相对射击时间。 */
    public fun getBaseTimestamp(): Long

    /** 当前手持枪械的物品栈；未持枪或未登记时返回 null。 */
    public fun getCurrentGunItem(): ItemStack?

    public fun initialData()

    /** 清理当前登记的手持枪械（切枪 / 非持枪复位）。 */
    public fun clearCurrentGunItem()

    /** 同步射击时间基准戳（毫秒），用于两端时间对齐。 */
    public fun syncBaseTimestamp(timestamp: Long)

    /** 结束 / 中断当前拉拴状态。 */
    public fun cancelBolt()

    /**
     * 记录一次射击的相对时间戳：把当前射击时间平移为上次射击时间，
     * 再写入新的相对时间戳（相对 [getBaseTimestamp] 的毫秒值）。
     */
    public fun recordShotTimestamp(relativeTimestamp: Long)

    public fun draw(itemStackSupplier: Supplier<ItemStack>)
    public fun bolt()
    public fun reload()
    public fun cancelReload()
    public fun fireSelect()
    public fun melee()
    public fun shoot(pitch: Supplier<Float>, yaw: Supplier<Float>, timestamp: Long): ShootResult

    public fun needCheckAmmo(): Boolean
    public fun consumesAmmoOrNot(): Boolean
    public fun getProcessedSprintStatus(): Float
    public fun aim(isAim: Boolean)

    /**
     * 服务端每 tick 调用，驱动所有枪械子系统更新。
     */
    public fun tick()

    public companion object {
        @JvmStatic
        public fun fromLivingEntity(entity: EntityLivingBase): IGunOperator = entity as IGunOperator
    }
}
