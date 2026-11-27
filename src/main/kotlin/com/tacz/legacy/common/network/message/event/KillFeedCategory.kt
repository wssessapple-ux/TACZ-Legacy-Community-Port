package com.tacz.legacy.common.network.message.event

/**
 * 击杀播报事件类别。服务端归类后随报文下发，客户端按档位配置决定是否入列显示。
 *
 * [PVP] 玩家击杀玩家；[PLAYER_KILLS_MOB] 玩家击杀普通怪物（本版默认不播报，档位留待后续）；
 * [BOSS_KILL] 玩家击杀 Boss / 精英怪；[MOB_KILLS_PLAYER] 怪物击杀玩家；
 * [MULTI_KILL] 连杀 / 多杀（占位，后续步骤接入）；[OTHER] 其余情况，不进入播报。
 */
public enum class KillFeedCategory {
    PVP,
    PLAYER_KILLS_MOB,
    BOSS_KILL,
    MOB_KILLS_PLAYER,
    MULTI_KILL,
    OTHER;

    public companion object {
        public fun fromId(id: Int): KillFeedCategory = entries.getOrElse(id) { OTHER }
    }
}
