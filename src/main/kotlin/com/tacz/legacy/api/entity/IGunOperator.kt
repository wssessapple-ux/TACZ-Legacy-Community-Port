package com.tacz.legacy.api.entity

import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import java.util.function.Supplier

/**
 * 枪械操作者接口，由 LivingEntity 通过 Mixin 实现。
 * 与上游 TACZ IGunOperator 行为保持一致。
 */
public interface IGunOperator {
    /** 射击冷却剩余毫秒数（两端同步值）。 */
    public fun getSynShootCoolDown(): Long

    /** 近战冷却剩余毫秒数。 */
    public fun getSynMeleeCoolDown(): Long

    /** 切枪（取出 / 收起）冷却剩余毫秒数。 */
    public fun getSynDrawCoolDown(): Long

    /** 当前是否正在拉拴。 */
    public fun getSynIsBolting(): Boolean

    /** 当前换弹状态。 */
    public fun getSynReloadState(): ReloadState

    /** 瞄准进度，0 为未瞄准，1 为完全瞄准。 */
    public fun getSynAimingProgress(): Float

    /** 当前是否正在瞄准。 */
    public fun getSynIsAiming(): Boolean

    /** 持续疾跑时长（秒）。 */
    public fun getSynSprintTime(): Float

    /** 当前是否处于趴下（crawl）状态。 */
    public fun getSynIsCrawling(): Boolean

    /** 射击时间基准戳（毫秒），用于计算相对射击时间。 */
    public fun getBaseTimestamp(): Long

    /** 当前手持枪械的物品栈；未持枪或未登记时返回 null。 */
    public fun getCurrentGunItem(): ItemStack?

    /** 复位操作者的全部枪械状态（时间戳、瞄准、换弹、疾跑、拉拴等）。 */
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

    /** 取出 / 切到指定枪械，[itemStackSupplier] 提供当前手持物品栈。 */
    public fun draw(itemStackSupplier: Supplier<ItemStack>)

    /** 手动拉拴（仅闭膛 / 手动栓动枪生效）。 */
    public fun bolt()

    /** 开始换弹。 */
    public fun reload()

    /** 取消进行中的换弹。 */
    public fun cancelReload()

    /** 切换到下一个可用射击模式。 */
    public fun fireSelect()

    /** 持枪近战攻击。 */
    public fun melee()

    /**
     * 执行一次射击。
     *
     * @param pitch 视角俯仰角提供者。
     * @param yaw 视角偏航角提供者。
     * @param timestamp 射击时间戳（相对基准戳的毫秒值）。
     * @return 射击结果（成功 / 各类拦截原因）。
     */
    public fun shoot(pitch: Supplier<Float>, yaw: Supplier<Float>, timestamp: Long): ShootResult

    /** 当前是否需要做弹药数量校验。 */
    public fun needCheckAmmo(): Boolean

    /** 当前动作是否会消耗弹药。 */
    public fun consumesAmmoOrNot(): Boolean

    /** 处理后的疾跑状态（供移动速度 / 动画读取）。 */
    public fun getProcessedSprintStatus(): Float

    /** 设置是否进入瞄准状态。 */
    public fun aim(isAim: Boolean)

    /**
     * 服务端每 tick 调用，驱动所有枪械子系统更新。
     */
    public fun tick()

    public companion object {
        /** 把实体强转为枪械操作者接口；实体由 Mixin 实现该接口。 */
        @JvmStatic
        public fun fromLivingEntity(entity: EntityLivingBase): IGunOperator = entity as IGunOperator
    }
}
