package com.tacz.legacy.common.entity

import io.netty.buffer.ByteBuf
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.projectile.EntityThrowable
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData
import kotlin.math.sqrt

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
 * 与子弹同一套服务端权威位移 + spawnData 同步 motion 的模式。
 */
internal class EntityGrenade : EntityThrowable, IEntityAdditionalSpawnData {

    private var throwerId: Int = -1
    private var fuseTicks: Int = FUSE_TICKS
    private var lifeTicks: Int = 0

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

    override fun onUpdate() {
        // 渲染位置插值基准（与子弹一致，必须在 super 前记录上一 tick 位置）。
        lastTickPosX = posX
        lastTickPosY = posY
        lastTickPosZ = posZ
        // 只跑计时器与骑乘逻辑，绕过 Throwable 自带的碰撞/摩擦/旋转插值。
        super.onEntityUpdate()

        if (world.isRemote) {
            posX += motionX
            posY += motionY
            posZ += motionZ
            setPosition(posX, posY, posZ)
            updateRotationFromMotion()
            return
        }

        lifeTicks++
        fuseTicks--
        if (fuseTicks <= 0 || lifeTicks >= MAX_LIFE) {
            explode()
            setDead()
            return
        }

        val start = Vec3d(posX, posY, posZ)
        val end = Vec3d(posX + motionX, posY + motionY, posZ + motionZ)
        val blockHit = world.rayTraceBlocks(start, end, false, false, false)

        if (blockHit != null) {
            // 落地弹跳：竖直速度反弹衰减，水平方向额外摩擦；不摧毁方块、不提前起爆。
            motionY = -motionY * BOUNCE
            motionX *= BOUNCE_FRICTION
            motionZ *= BOUNCE_FRICTION
            val hit = blockHit.hitVec
            setPosition(hit.x, hit.y, hit.z)
            if (Math.abs(motionY) < REST_THRESHOLD) {
                motionY = 0.0
            }
        } else {
            posX += motionX
            posY += motionY
            posZ += motionZ
            setPosition(posX, posY, posZ)
        }

        motionY -= GRAVITY * 0.05f
        motionX *= (1.0 - AIR_FRICTION)
        motionY *= (1.0 - AIR_FRICTION)
        motionZ *= (1.0 - AIR_FRICTION)

        updateRotationFromMotion()
    }

    private fun updateRotationFromMotion() {
        val horizontal = sqrt(motionX * motionX + motionZ * motionZ)
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
        /** 抛射初速（格/tick）。 */
        const val THROW_SPEED = 1.45
        /** 出手额外上抛分量。 */
        const val UPWARD_BOOST = 0.15
        /** 重力加速度（与子弹 gravity 同语义，onUpdate 内 ×0.05）。 */
        const val GRAVITY = 0.04f
        /** 空气摩擦系数/tick。 */
        const val AIR_FRICTION = 0.02
        /** 落地竖直反弹系数。 */
        const val BOUNCE = 0.2
        /** 落地后水平速度保留比例。 */
        const val BOUNCE_FRICTION = 0.55
        /** 竖直速度低于该值即视为躺平，不再反弹。 */
        const val REST_THRESHOLD = 0.05f
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
