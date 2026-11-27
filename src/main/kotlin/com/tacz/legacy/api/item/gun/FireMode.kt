package com.tacz.legacy.api.item.gun

import com.google.gson.annotations.SerializedName

/**
 * 开火模式枚举，与上游 TACZ 保持一致。
 */
public enum class FireMode {
    /** 全自动：按住扳机连续射击。 */
    @SerializedName("auto")
    AUTO,

    /** 半自动：每按一次扳机发射一发。 */
    @SerializedName("semi")
    SEMI,

    /** 点射：每次扳机发射固定连发数。 */
    @SerializedName("burst")
    BURST,

    /** 未配置或无法识别的射击模式。 */
    @SerializedName("unknown")
    UNKNOWN,
}
