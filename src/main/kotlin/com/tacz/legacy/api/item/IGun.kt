package com.tacz.legacy.api.item

import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.api.item.gun.FireMode
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation

/**
 * 枪械 NBT 访问接口。不包含枪械逻辑，仅提供数据访问。
 * 与上游 TACZ IGun 行为保持一致。
 */
public interface IGun {
    // ---- 身份与射击模式 ----

    /** 读取枪械注册 id；缺省时回退到默认枪 id。 */
    public fun getGunId(stack: ItemStack): ResourceLocation

    /** 写入枪械注册 id；传 null 时不改动已有标签。 */
    public fun setGunId(stack: ItemStack, gunId: ResourceLocation?)

    /** 当前瞄具提供的倍率（无瞄具时取机瞄倍率），下限为 1.0。 */
    public fun getAimingZoom(stack: ItemStack): Float

    /** 当前射击模式；未配置或无法识别时返回 [FireMode.UNKNOWN]。 */
    public fun getFireMode(stack: ItemStack): FireMode

    /** 写入射击模式；传 null 时归为 [FireMode.UNKNOWN]。 */
    public fun setFireMode(stack: ItemStack, fireMode: FireMode?)

    /** 持该枪是否允许趴下射击（读枪械数据 `can_crawl`，无数据返回 false）。 */
    public fun isCanCrawl(stack: ItemStack): Boolean

    // ---- 弹药 ----

    /** 弹匣内当前弹药数（无标签时为 0）。 */
    public fun getCurrentAmmoCount(stack: ItemStack): Int

    /** 写入弹匣弹药数，负值按 0 处理。 */
    public fun setCurrentAmmoCount(stack: ItemStack, ammoCount: Int)

    /** 弹匣弹药数减一（下限由实现约束）。 */
    public fun reduceCurrentAmmoCount(stack: ItemStack)

    /** 膛内是否已有一颗待击发的子弹。 */
    public fun hasBulletInBarrel(stack: ItemStack): Boolean

    /** 设置膛内待击发子弹标记。 */
    public fun setBulletInBarrel(stack: ItemStack, bulletInBarrel: Boolean)

    /** 供弹时是否从玩家背包取弹药。 */
    public fun useInventoryAmmo(stack: ItemStack): Boolean

    /** 玩家背包是否还有可用于该枪的弹药；[needCheckAmmo] 表示是否需要做严格弹药校验。 */
    public fun hasInventoryAmmo(shooter: EntityLivingBase, stack: ItemStack, needCheckAmmo: Boolean): Boolean

    /** 是否使用虚拟（演示）弹药，不消耗真实背包弹药。 */
    public fun useDummyAmmo(stack: ItemStack): Boolean

    /** 虚拟弹药剩余数量。 */
    public fun getDummyAmmoAmount(stack: ItemStack): Int

    /** 写入虚拟弹药数量。 */
    public fun setDummyAmmoAmount(stack: ItemStack, amount: Int)

    /** 叠加虚拟弹药数量。 */
    public fun addDummyAmmoAmount(stack: ItemStack, amount: Int)

    // ---- 配件 ----

    /** 读取指定类型配件的 NBT 标签；未安装时返回 null。 */
    public fun getAttachmentTag(stack: ItemStack, type: AttachmentType): NBTTagCompound?

    /** 读取指定类型的已安装配件物品栈。 */
    public fun getAttachment(stack: ItemStack, type: AttachmentType): ItemStack

    /** 读取指定类型的内置配件物品栈（枪自带、不可拆卸）。 */
    public fun getBuiltinAttachment(stack: ItemStack, type: AttachmentType): ItemStack

    /** 读取已安装配件的注册 id；未安装时返回空配件 id。 */
    public fun getAttachmentId(stack: ItemStack, type: AttachmentType): ResourceLocation

    /** 读取内置配件的注册 id；无内置时返回空配件 id。 */
    public fun getBuiltInAttachmentId(stack: ItemStack, type: AttachmentType): ResourceLocation

    /** 把配件物品安装到枪上对应槽位。 */
    public fun installAttachment(gun: ItemStack, attachment: ItemStack)

    /** 卸下指定类型的配件。 */
    public fun unloadAttachment(gun: ItemStack, type: AttachmentType)

    /** 该配件物品是否允许安装到这把枪上。 */
    public fun allowAttachment(gun: ItemStack, attachmentItem: ItemStack): Boolean

    /** 该配件类型是否允许在这把枪上使用。 */
    public fun allowAttachmentType(gun: ItemStack, type: AttachmentType): Boolean

    // ---- 激光 / 配件锁 / 过热 ----

    /** 是否使用了自定义激光颜色（否则取配件默认色）。 */
    public fun hasCustomLaserColor(stack: ItemStack): Boolean

    /** 读取激光颜色（ARGB 整数）。 */
    public fun getLaserColor(stack: ItemStack): Int

    /** 写入激光颜色（ARGB 整数）。 */
    public fun setLaserColor(stack: ItemStack, color: Int)

    /** 配件槽是否处于锁定状态（锁定后不可拆卸）。 */
    public fun hasAttachmentLock(stack: ItemStack): Boolean

    /** 设置配件槽锁定标记。 */
    public fun setAttachmentLock(stack: ItemStack, locked: Boolean)

    /** 是否处于过热锁定状态（锁定后需冷却才能再射击）。 */
    public fun isOverheatLocked(stack: ItemStack): Boolean

    /** 设置过热锁定标记。 */
    public fun setOverheatLocked(stack: ItemStack, locked: Boolean)

    /** 当前热量值（0 为未过热）。 */
    public fun getHeatAmount(stack: ItemStack): Float

    /** 写入当前热量值。 */
    public fun setHeatAmount(stack: ItemStack, amount: Float)

    public companion object {
        public const val ATTACHMENT_BASE_TAG: String = "Attachment"
        public const val ATTACHMENT_LOCK_TAG: String = "AttachmentLock"
        public const val DUMMY_AMMO_TAG: String = "DummyAmmo"
        public const val GUN_ID_TAG: String = "GunId"
        public const val FIRE_MODE_TAG: String = "GunFireMode"
        public const val AMMO_COUNT_TAG: String = "GunCurrentAmmoCount"
        public const val BULLET_IN_BARREL_TAG: String = "HasBulletInBarrel"
        public const val LASER_COLOR_TAG: String = "LaserColor"
        public const val OVERHEAT_LOCK_TAG: String = "OverHeated"
        public const val HEAT_AMOUNT_TAG: String = "HeatAmount"

        /** 物品实现 [IGun] 时返回其接口实例，否则返回 null。 */
        @JvmStatic
        public fun getIGunOrNull(stack: ItemStack): IGun? = stack.item as? IGun

        /** 实体主手是否持有枪械物品。 */
        @JvmStatic
        public fun mainHandHoldGun(entity: EntityLivingBase): Boolean =
            entity.heldItemMainhand.item is IGun

        /** 读取主手枪械的射击模式；主手非枪时返回 [FireMode.UNKNOWN]。 */
        @JvmStatic
        public fun getMainHandFireMode(entity: EntityLivingBase): FireMode {
            val stack = entity.heldItemMainhand
            val iGun = stack.item as? IGun ?: return FireMode.UNKNOWN
            return iGun.getFireMode(stack)
        }
    }
}
