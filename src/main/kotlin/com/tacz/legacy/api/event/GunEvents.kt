package com.tacz.legacy.api.event

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.eventhandler.Cancelable
import net.minecraftforge.fml.common.eventhandler.Event
import net.minecraftforge.fml.relauncher.Side

/**
 * 扣动扳机时触发（每次扳机仅触发一次，与 [GunFireEvent] 不同）。
 *
 * 监听方式：在 [MinecraftForge.EVENT_BUS] 上注册带 `@SubscribeEvent` 的方法即可接收。
 * 服务端权威射击与客户端本地预测都会 post，服务端结果回包后也会在客户端回放一次，
 * 用 [logicalSide] 区分两端。本事件**可取消**：监听返回取消后本次射击被中止。
 *
 * @property shooter 发起射击的实体。
 * @property gunId 枪械注册名。
 * @property logicalSide 事件触发所在逻辑端。
 */
@Cancelable
public class GunShootEvent(
    public val shooter: EntityLivingBase,
    public val gunId: ResourceLocation,
    public val logicalSide: Side,
) : Event()

/**
 * 每发子弹击发时触发（Burst 模式一次扳机可多次触发）。
 *
 * 监听方式：在 [MinecraftForge.EVENT_BUS] 上注册带 `@SubscribeEvent` 的方法即可接收。
 * 服务端、客户端预测与服务端回包回放都会 post，用 [logicalSide] 区分两端。
 * 本事件**可取消**：监听返回取消后该发子弹不再击发。
 *
 * @property shooter 发起射击的实体。
 * @property gunId 枪械注册名。
 * @property logicalSide 事件触发所在逻辑端。
 */
@Cancelable
public class GunFireEvent(
    public val shooter: EntityLivingBase,
    public val gunId: ResourceLocation,
    public val logicalSide: Side,
) : Event()

/**
 * 开始换弹时触发。
 *
 * 监听方式：在 [MinecraftForge.EVENT_BUS] 上注册带 `@SubscribeEvent` 的方法即可接收。
 * 客户端本地预测与服务端都会 post，服务端回包后也会在客户端回放，用 [logicalSide] 区分两端。
 * 本事件**可取消**：监听返回取消后本次换弹被中止。
 *
 * @property entity 发起换弹的实体。
 * @property gunItemStack 正在换弹的枪物品栈。
 * @property logicalSide 事件触发所在逻辑端。
 */
@Cancelable
public class GunReloadEvent(
    public val entity: EntityLivingBase,
    public val gunItemStack: ItemStack,
    public val logicalSide: Side,
) : Event()

/**
 * 持枪械近战时触发。
 *
 * 监听方式：在 [MinecraftForge.EVENT_BUS] 上注册带 `@SubscribeEvent` 的方法即可接收。
 * 服务端发起近战，结果回包后在客户端回放，用 [logicalSide] 区分两端。
 * 本事件**可取消**：监听返回取消后本次近战被中止。
 *
 * @property shooter 发起近战的实体。
 * @property gunItemStack 近战所持枪物品栈。
 * @property logicalSide 事件触发所在逻辑端。
 */
@Cancelable
public class GunMeleeEvent(
    public val shooter: EntityLivingBase,
    public val gunItemStack: ItemStack,
    public val logicalSide: Side,
) : Event()

/**
 * 切枪时触发（收起旧枪、取出新枪）。
 *
 * 监听方式：在 [MinecraftForge.EVENT_BUS] 上注册带 `@SubscribeEvent` 的方法即可接收。
 * 服务端与客户端都会 post，服务端回包后也会在客户端回放，用 [logicalSide] 区分两端。
 * 本事件**不可取消**，仅用于观测切枪流程。
 *
 * @property entity 切枪的实体。
 * @property previousGunItem 切出前手持的枪物品栈。
 * @property currentGunItem 切入后手持的枪物品栈。
 * @property logicalSide 事件触发所在逻辑端。
 */
public class GunDrawEvent(
    public val entity: EntityLivingBase,
    public val previousGunItem: ItemStack,
    public val currentGunItem: ItemStack,
    public val logicalSide: Side,
) : Event()

/**
 * 实体被枪械子弹命中后的事件。
 *
 * 监听方式：在 [MinecraftForge.EVENT_BUS] 上注册带 `@SubscribeEvent` 的方法即可接收。
 * 服务端命中判定与客户端收到回包后都会 post，用 [logicalSide] 区分两端。
 * 本事件**不可取消**，仅用于命中反馈（HUD 标记、音效等），不阻断伤害流程。
 * 实际对外投递的是 [Post] 子类；基类保留供扩展。
 *
 * @property bullet 命中子弹实体，可能为 null。
 * @property hurtEntity 被命中的实体，可能为 null。
 * @property attacker 攻击者，可能为 null（环境伤害 / 无主子弹）。
 * @property gunId 枪械注册名。
 * @property gunDisplayId 枪械展示注册名。
 * @property baseAmount 基础伤害（未乘爆头倍率）。
 * @property isHeadShot 是否爆头。
 * @property headShotMultiplier 爆头倍率。
 * @property logicalSide 事件触发所在逻辑端。
 * @property amount 实际伤害：爆头时为 `baseAmount * headShotMultiplier`，否则为 `baseAmount`。
 */
public open class EntityHurtByGunEvent(
    public val bullet: Entity?,
    public val hurtEntity: Entity?,
    public val attacker: EntityLivingBase?,
    public val gunId: ResourceLocation,
    public val gunDisplayId: ResourceLocation,
    public val baseAmount: Float,
    public val isHeadShot: Boolean,
    public val headShotMultiplier: Float,
    public val logicalSide: Side,
) : Event() {
    public val amount: Float
        get() = if (isHeadShot) baseAmount * headShotMultiplier else baseAmount

    /** 命中完成后的回调事件，供客户端命中标记 / 伤害数字等反馈监听。 */
    public class Post(
        bullet: Entity?,
        hurtEntity: Entity?,
        attacker: EntityLivingBase?,
        gunId: ResourceLocation,
        gunDisplayId: ResourceLocation,
        baseAmount: Float,
        isHeadShot: Boolean,
        headShotMultiplier: Float,
        logicalSide: Side,
    ) : EntityHurtByGunEvent(
        bullet = bullet,
        hurtEntity = hurtEntity,
        attacker = attacker,
        gunId = gunId,
        gunDisplayId = gunDisplayId,
        baseAmount = baseAmount,
        isHeadShot = isHeadShot,
        headShotMultiplier = headShotMultiplier,
        logicalSide = logicalSide,
    )
}

/**
 * 实体被枪械子弹击杀时触发。
 *
 * 监听方式：在 [MinecraftForge.EVENT_BUS] 上注册带 `@SubscribeEvent` 的方法即可接收；
 * 服务端击杀时与客户端收到播报报文后各 post 一次，用 [logicalSide] 区分两端。
 * 本事件不可取消（非 `@Cancelable`），仅用于观测击杀数据，不阻断击杀流程。
 *
 * @property bullet 造成击杀的子弹实体，可能为 null。
 * @property killedEntity 被击杀的实体，可能为 null。
 * @property attacker 攻击者，可能为 null（环境伤害/无主子弹）。
 * @property gunId 枪械注册名。
 * @property gunDisplayId 枪械展示注册名。
 * @property baseDamage 基础伤害（未乘爆头倍率）。
 * @property isHeadShot 是否爆头。
 * @property headShotMultiplier 爆头倍率。
 * @property multiKillCount 同一攻击者在多杀时间窗口内的连续击杀计数，1 = 首杀，2 = 双杀，依次递增。
 * @property logicalSide 事件触发所在逻辑端。
 */
public class EntityKillByGunEvent(
    public val bullet: Entity?,
    public val killedEntity: EntityLivingBase?,
    public val attacker: EntityLivingBase?,
    public val gunId: ResourceLocation,
    public val gunDisplayId: ResourceLocation,
    public val baseDamage: Float,
    public val isHeadShot: Boolean,
    public val headShotMultiplier: Float,
    public val logicalSide: Side,
    public val multiKillCount: Int = 1,
) : Event() {
    public val amount: Float
        get() = if (isHeadShot) baseDamage * headShotMultiplier else baseDamage
}
