package com.tacz.legacy.api.item.attachment

import com.google.gson.annotations.SerializedName
import java.util.Locale

/** 配件槽位类型，对应枪包 json 中的配件分组名。 */
public enum class AttachmentType(
    /** 枪包 json 中使用的序列化名。 */
    public val serializedName: String,
) {
    @SerializedName("scope")
    SCOPE("scope"),

    @SerializedName("muzzle")
    MUZZLE("muzzle"),

    @SerializedName("stock")
    STOCK("stock"),

    @SerializedName("grip")
    GRIP("grip"),

    @SerializedName("laser")
    LASER("laser"),

    @SerializedName("extended_mag")
    EXTENDED_MAG("extended_mag"),

    NONE("none");

    public companion object {
        /** 按枪包 json 名解析配件类型；空值或未知类型归为 [NONE]。 */
        @JvmStatic
        public fun fromSerializedName(rawValue: String?): AttachmentType {
            val normalized = rawValue?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return values().firstOrNull { it.serializedName == normalized } ?: NONE
        }
    }
}