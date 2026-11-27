package com.tacz.legacy.common.config

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityList
import net.minecraft.util.ResourceLocation

/**
 * Boss / 精英怪判定：内置 1.12.2 原版 Boss，外加配置名单（实体注册名，`modid:entity`）。
 * 命中其一即归入 [com.tacz.legacy.common.network.message.event.KillFeedCategory.BOSS_KILL]。
 */
internal object BossKillConfigRead {
    private val builtIn: Set<ResourceLocation> = setOf(
        ResourceLocation("minecraft", "ender_dragon"),
        ResourceLocation("minecraft", "wither"),
    )

    private val configured: MutableSet<ResourceLocation> = LinkedHashSet()

    internal fun reload(entries: List<String>) {
        configured.clear()
        entries.forEach { raw ->
            runCatching { ResourceLocation(raw.trim()) }.onSuccess(configured::add)
        }
    }

    internal fun isBoss(entity: Entity?): Boolean {
        val id = entity?.let(EntityList::getKey) ?: return false
        return id in builtIn || id in configured
    }
}
