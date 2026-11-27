package com.tacz.legacy.api.entity

/**
 * 击退修改接口，由 LivingEntity 通过 Mixin 实现。
 */
public interface KnockBackModifier {
    /** 击退强度复位为默认值（-1.0 表示未设置）。 */
    public fun resetKnockBackStrength()

    /** 读取当前击退强度，-1.0 表示未设置。 */
    public fun getKnockBackStrength(): Double

    /** 设置击退强度。 */
    public fun setKnockBackStrength(strength: Double)
}
