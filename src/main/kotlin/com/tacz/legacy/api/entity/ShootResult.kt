package com.tacz.legacy.api.entity

/**
 * 射击结果枚举，与上游 TACZ 保持一致。
 */
public enum class ShootResult {
    /** 射击成功。 */
    SUCCESS,
    /** 未知失败原因。 */
    UNKNOWN_FAIL,
    /** 射击冷却尚未结束。 */
    COOL_DOWN,
    /** 弹药不足。 */
    NO_AMMO,
    /** 枪械尚未取出完成。 */
    NOT_DRAW,
    /** 手持物品不是枪械。 */
    NOT_GUN,
    /** 枪械注册 id 不存在于枪包。 */
    ID_NOT_EXIST,
    /** 手动栓动枪需要先拉拴。 */
    NEED_BOLT,
    /** 正在换弹，拦截射击。 */
    IS_RELOADING,
    /** 正在切枪，拦截射击。 */
    IS_DRAWING,
    /** 正在拉拴，拦截射击。 */
    IS_BOLTING,
    /** 正在近战，拦截射击。 */
    IS_MELEE,
    /** 正在疾跑，拦截射击。 */
    IS_SPRINTING,
    /** 两端时间戳校验未通过（网络超时 / 偏差过大）。 */
    NETWORK_FAIL,
    /** Forge 事件监听取消了本次射击。 */
    FORGE_EVENT_CANCEL,
    /** 枪械过热锁定，拦截射击。 */
    OVERHEATED,
}
