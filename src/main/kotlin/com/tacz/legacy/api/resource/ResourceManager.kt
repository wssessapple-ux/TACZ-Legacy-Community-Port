package com.tacz.legacy.api.resource

import com.tacz.legacy.TACZLegacy
import java.nio.file.Paths

/**
 * 用于注册需要导出的枪包资源，供附属模组复用。
 *
 * 外部附属模组只应通过 [registerExportResource] 登记资源；内部登记表不对外暴露，
 * 避免外部直接增删破坏导出链路。
 */
public object ResourceManager {
    private val extraEntries: MutableList<ExtraEntry> = mutableListOf()

    /** 内部只读快照，供导出链路遍历，不对外暴露可变表。 */
    internal fun snapshot(): List<ExtraEntry> = extraEntries.toList()

    /**
     * @deprecated 旧版枪包登记入口，仅保留兼容；新代码请改用 [registerExportResource]。
     * 该方法只记录告警，不再登记资源。
     */
    @Deprecated("Use registerExportResource instead", ReplaceWith("registerExportResource(modMainClass, extraFolderPath)"))
    @JvmStatic
    public fun registerExtraGunPack(modMainClass: Class<*>, extraFolderPath: String): Unit {
        TACZLegacy.logger.warn(
            "Deprecated gun pack export API used by {}: {}. Please migrate to registerExportResource().",
            modMainClass.name,
            extraFolderPath,
        )
    }

    /** 登记需要随枪包一并导出的附属资源目录。 */
    @JvmStatic
    public fun registerExportResource(modMainClass: Class<*>, extraFolderPath: String): Unit {
        extraEntries += ExtraEntry(
            modMainClass = modMainClass,
            srcPath = extraFolderPath,
            extraDirName = Paths.get(extraFolderPath).fileName.toString(),
        )
    }

    internal data class ExtraEntry(
        val modMainClass: Class<*>,
        val srcPath: String,
        val extraDirName: String,
    )
}
