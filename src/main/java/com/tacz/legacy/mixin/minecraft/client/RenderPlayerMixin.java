package com.tacz.legacy.mixin.minecraft.client;

import com.tacz.legacy.client.fpv.FpvController;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * FPV 视角挂在无人机上时，原版 {@code RenderPlayer#doRender} 会把自身玩家身体当作第一人称隐藏
 * （isUser 且视角实体不是自己就跳过渲染），导致从飞机视角回看地面看不到自己人。
 *
 * 这里重定向 {@code isUser()}：FPV 活动期间让自身玩家以第三方实体正常渲染身体；
 * 无人机贴到本体很近时再隐藏，避免模型怼镜头穿模。
 */
@Mixin(RenderPlayer.class)
public abstract class RenderPlayerMixin {

    @Redirect(
            method = "doRender",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;isUser()Z")
    )
    private boolean tacz$renderFpvSelfBody(AbstractClientPlayer player) {
        if (!FpvController.isFpvActive()) {
            return player.isUser();
        }
        Entity drone = FpvController.getActiveDrone();
        if (drone != null && drone.getDistance(player) < 1.5) {
            // 贴镜头：保持隐藏，避免机体模型和本体模型互相穿插。
            return true;
        }
        // FPV 期间自身玩家作为第三方实体显示身体。
        return false;
    }
}
