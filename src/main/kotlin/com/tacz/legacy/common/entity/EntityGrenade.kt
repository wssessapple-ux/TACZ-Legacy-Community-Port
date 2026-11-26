package com.tacz.legacy.common.entity

import com.tacz.legacy.TACZLegacy
import io.netty.buffer.ByteBuf
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.projectile.EntityThrowable
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.RayTraceResult
import net.minecraft.world.World
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import kotlin.math.hypot

/**
 * frag 破片手雷实体（原创投掷物）。
 *
 * 行为：
 * - 手持右键抛出，低速抛物线，落地弹跳并摩擦衰减；
 * - 定时引信，命中方块/生物都不提前起爆；
 * - 引信到点在当前位置 AOE 爆炸，复用 [LegacyProjectileExplosionHelper]，
 *   承伤（不豁免投掷者）、不摧毁地形。
 *
 * 物理手写：绕过 [EntityThrowable.onUpdate] 自带的碰撞检测、0.99 摩擦与旋转插值，
 * 与无人机同一套服务端权威位移 + spawnData 同步 motion 的模式。
 *
 * 碰撞走分轴 offset 探测（与 FPV 无人机一致，实测不卡墙）：
 * - 用当前干净盒体沿单轴 offset 后查世界碰撞盒，相交即判撞该轴，按轴反射；
 * - 不依赖原版 [net.minecraft.entity.Entity.move] 的逐轴推挤——它对已经嵌入方块的
 *   盒体推不出来（calculateOffset 只在盒体完全位于方块某一侧时生效），反复弹跳的
 *   低幅落地正是嵌入式边界高发区；
 * - 位移后再跑一次脱困兜底：盒体万一嵌入实心方块，先水平试探推离，推不动向上托举。
 * 贴地低速不再反弹，低于阈值即趴住冻结。
 */
internal class EntityGrenade : EntityThrowable, IEntityAdditionalSpawnData {

    private var throwerId: Int = -1
    private var fuseTicks: Int = FUSE_TICKS
    private var lifeTicks: Int = 0
    private var grounded: Boolean = false
    private var resting: Boolean = false

    constructor(world: World) : super(world) {
        setSize(0.3f, 0.3f)
    }

    constructor(world: World, shooter: EntityLivingBase) : super(world, shooter) {
        throwerId = shooter.entityId
        setSize(0.3f, 0.3f)
        setPosition(
            shooter.lastTickPosX + (shooter.posX - shooter.lastTickPosX) / 2.0,
            shooter.lastTickPosY + (shooter.posY - shooter.lastTickPosY) / 2.0 + shooter.eyeHeight,
            shooter.lastTickPosZ + (shooter.posZ - shooter.lastTickPosZ) / 2.0,
        )
    }

    /** 按玩家视线赋抛射初速，带小幅上抛角，叠加投掷者自身速度。 */
    fun shootFromRotation(shooter: EntityLivingBase) {
        val look = shooter.lookVec
        motionX = look.x * THROW_SPEED + shooter.motionX
        motionY = look.y * THROW_SPEED + UPWARD_BOOST + shooter.motionY
        motionZ = look.z * THROW_SPEED + shooter.motionZ
        updateRotationFromMotion()
        prevRotationYaw = rotationYaw
        prevRotationPitch = rotationPitch
    }

    override fun getGravityVelocity(): Float = GRAVITY

    /** 定时引信：命中不触发爆炸，留空实现。 */
    override fun onImpact(result: RayTraceResult) = Unit

    /**
     * 服务端位置包只接受位置，不接受服务端下发的旋转——本地旋转由 motion 精确推算，
     * 避免 256 步压缩角度污染精度。对齐子弹实体的同步兜底。
     */
    @SideOnly(Side.CLIENT)
    override fun setPositionAndRotationDirect(
        x: Double, y: Double, z: Double,
        yaw: Float, pitch: Float, posRotationIncrements: Int, teleport: Boolean,
    ) {
        setPosition(x, y, z)
    }

    /** 阻止服务端速度包覆盖本地 motion；位置由 spawnData + 服务端位置包驱动。 */
    @SideOnly(Side.CLIENT)
    override fun setVelocity(x: Double, y: Double, z: Double) {
        // no-op: 客户端不自行积分，位置跟随服务端位置包
    }

    override fun onUpdate() {
        // 渲染位置插值基准（与子弹一致，必须在 super 前记录上一 tick 位置）。
        lastTickPosX = posX
        lastTickPosY = posY
        lastTickPosZ = posZ
        // 只跑计时器和骑乘逻辑，绕过 Throwable 自带的碰撞/摩擦/旋转插值。
        super.onEntityUpdate()

        if (world.isRemote) {
            // 客户端位置由服务端位置包驱动（见 setPositionAndRotationDirect），不再自行积分 motion；
            // 只本地推算朝向，趴住后连姿态一起冻结。
            if (!resting) {
                updateRotationFromMotion()
            }
            return
        }

        lifeTicks++
        fuseTicks--
        if (fuseTicks <= 0 || lifeTicks >= MAX_LIFE) {
            explode()
            setDead()
            return
        }

        // 已趴住静止：不再改 motion，姿态与位置冻结到爆炸。
        if (resting) {
            return
        }

        val bb = entityBoundingBox
        var hitV = false
        var hitX = false
        var hitZ = false

        // 竖直碰撞：盒体沿 Y 偏移后探脚下方块/头顶方块，相交即撞。
        // 不依赖 move 推挤标志——原版 calculateOffset 对已嵌入盒体推不出来。
        hitV = collidesBox(bb.offset(0.0, motionY, 0.0))
        if (hitV) {
            motionY = when {
                motionY < -REST_THRESHOLD -> -motionY * RESTITUTION   // 向下砸地：向上反弹
                motionY > REST_THRESHOLD -> -motionY * RESTITUTION   // 向上撞顶：向下反弹
                else -> 0.0
            }
            grounded = motionY <= 0.0
        } else {
            grounded = false
        }

        // 水平分轴碰撞：撞哪轴翻哪轴，带墙面摩擦衰减。
        hitX = collidesBox(bb.offset(motionX, 0.0, 0.0))
        if (hitX) {
            motionX = -motionX * RESTITUTION * BOUNCE_FRICTION
        }
        hitZ = collidesBox(bb.offset(0.0, 0.0, motionZ))
        if (hitZ) {
            motionZ = -motionZ * RESTITUTION * BOUNCE_FRICTION
        }

        // 重力（对齐原版抛射物，每 tick 直接减）。
        motionY -= GRAVITY.toDouble()
        // 空气摩擦。
        motionX *= (1.0 - AIR_FRICTION)
        motionY *= (1.0 - AIR_FRICTION)
        motionZ *= (1.0 - AIR_FRICTION)

        // 贴地强摩擦刹车：竖直分量清零，水平快速衰减，低于阈值即趴住。
        if (grounded) {
            motionY = 0.0
            motionX *= (1.0 - GROUND_FRICTION)
            motionZ *= (1.0 - GROUND_FRICTION)
            val horizontal = hypot(motionX, motionZ)
            if (horizontal < REST_THRESHOLD) {
                motionX = 0.0
                motionY = 0.0
                motionZ = 0.0
                resting = true
            }
        }

        // 位移并按新位置重建盒体。
        posX += motionX
        posY += motionY
        posZ += motionZ
        setPosition(posX, posY, posZ)

        // 兜底脱困：盒体万一嵌入实心/碰撞方块，先水平推离，推不动再向上托举。
        freeFromSolid()

        if (DEBUG_PHYSICS) {
            TACZLegacy.logger.info(
                "[GrenadeDebug] t={} m=({},{}) pY={} bb=({}~{}) hit=V{} X{} Z{} grounded={} resting={}",
                lifeTicks,
                String.format("%.3f", motionX),
                String.format("%.3f", motionY),
                String.format("%.3f", motionZ),
                String.format("%.3f", posY),
                String.format("%.3f", entityBoundingBox.minY),
                String.format("%.3f", entityBoundingBox.maxY),
                hitV, hitX, hitZ, grounded, resting,
            )
        }

        updateRotationFromMotion()
    }

    /** 探测指定包围盒是否与世界方块碰撞盒相交（不含实体碰撞）。 */
    private fun collidesBox(bb: AxisAlignedBB): Boolean =
        world.getCollisionBoxes(null, bb).isNotEmpty()

    /** 陷入碰撞方块时脱困：先四个水平方向各推一段，推不动再向上逐格托举；脱困后清零速度防压回。 */
    private fun freeFromSolid() {
        if (!collidesBox(entityBoundingBox)) return

        val horizontal = listOf(
            0.35 to 0.0, -0.35 to 0.0, 0.0 to 0.35, 0.0 to -0.35,
        )
        for ((dx, dz) in horizontal) {
            val probe = entityBoundingBox.offset(dx, 0.0, dz)
            if (!collidesBox(probe)) {
                posX += dx
                posZ += dz
                setPosition(posX, posY, posZ)
                motionX = 0.0
                motionY = 0.0
                motionZ = 0.0
                return
            }
        }

        // 水平推不动（横卡墙缝/夹层），向上托举最多 4 格。
        var guard = 0
        while (collidesBox(entityBoundingBox) && guard < 4) {
            posY += 1.0
            setPosition(posX, posY, posZ)
            guard++
        }
        motionX = 0.0
        motionY = 0.0
        motionZ = 0.0
    }

    private fun updateRotationFromMotion() {
        val horizontal = hypot(motionX, motionZ)
        rotationYaw = Math.toDegrees(Math.atan2(motionX, motionZ)).toFloat()
        rotationPitch = Math.toDegrees(Math.atan2(motionY, horizontal)).toFloat()
    }

    private fun explode() {
        if (world.isRemote) return
        val owner = thrower ?: (world.getEntityByID(throwerId) as? EntityLivingBase)
        LegacyProjectileExplosionHelper.createExplosion(
            owner = owner,
            exploder = this,
            x = posX,
            y = posY,
            z = posZ,
            damage = DAMAGE,
            radius = RADIUS,
            knockback = true,
            destroyBlock = false,
        )
    }

    override fun writeEntityToNBT(compound: NBTTagCompound) {
        super.writeEntityToNBT(compound)
        compound.setInteger("ThrowerId", throwerId)
        compound.setInteger("FuseTicks", fuseTicks)
        compound.setInteger("LifeTicks", lifeTicks)
    }

    override fun readEntityFromNBT(compound: NBTTagCompound) {
        super.readEntityFromNBT(compound)
        throwerId = compound.getInteger("ThrowerId")
        fuseTicks = compound.getInteger("FuseTicks")
        lifeTicks = compound.getInteger("LifeTicks")
    }

    override fun writeSpawnData(buffer: ByteBuf) {
        buffer.writeInt(throwerId)
        buffer.writeInt(fuseTicks)
        buffer.writeDouble(motionX)
        buffer.writeDouble(motionY)
        buffer.writeDouble(motionZ)
    }

    override fun readSpawnData(buffer: ByteBuf) {
        throwerId = buffer.readInt()
        fuseTicks = buffer.readInt()
        motionX = buffer.readDouble()
        motionY = buffer.readDouble()
        motionZ = buffer.readDouble()
        updateRotationFromMotion()
    }

    private companion object {
        /** 物理诊断开关：JVM 属性 -Dtacz.grenade.debug=true 打开，默认关，不刷正式日志。 */
        val DEBUG_PHYSICS: Boolean =
            System.getProperty("tacz.grenade.debug", "false").toBoolean()

        /** 抛射初速（格/tick）。 */
        const val THROW_SPEED = 1.2
        /** 出手额外上抛分量。 */
        const val UPWARD_BOOST = 0.12
        /** 重力加速度（格/tick²，介于原版抛射物 0.03 与 Bedrock 官方 0.05 之间，雷更有分量）。 */
        const val GRAVITY = 0.035f
        /** 空气摩擦系数/tick（0.99 保留）。 */
        const val AIR_FRICTION = 0.01
        /** 碰撞反弹系数（restitution，0 不弹，1 弹回原高）。 */
        const val RESTITUTION = 0.42
        /** 命中后水平速度保留比例。 */
        const val BOUNCE_FRICTION = 0.7
        /** 贴地水平摩擦系数/tick（强刹车）。 */
        const val GROUND_FRICTION = 0.22
        /** 速度低于该值即视为趴住静止。 */
        const val REST_THRESHOLD = 0.05
        /** 引信时长（tick，2.5 秒）。 */
        const val FUSE_TICKS = 50
        /** 寿命兜底，防止卡墙后无限存在。 */
        const val MAX_LIFE = 100
        /** 爆炸半径（格）。 */
        const val RADIUS = 2.5f
        /** 爆炸基础伤害。 */
        const val DAMAGE = 10.0f
    }
}
