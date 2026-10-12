package com.magconch.engine.modules.flight.listener;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * 摔落伤害兜底监听器
 * <p>
 * 彻底修复原版漏洞：在玩家拥有 allowFlight 但未处于飞行姿态（自由落体/饿断飞行）摔落地面时，确保摔落伤害正常扣除。
 */
public class FlightDamageListener implements Listener {

    // ==========================================
    // 事件处理
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        // 1. 仅关注玩家与摔落伤害
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        // 2. 创造与旁观模式免疫摔伤
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }

        // 3. 玩家并未在飞行姿态中自由落体，但拥有 allowFlight 权限
        if (player.getAllowFlight() && !player.isFlying()) {
            // 确保事件不被原版 allowFlight 逻辑吞没或取消
            if (event.isCancelled()) {
                event.setCancelled(false);
            }

            // 若伤害被原版置为 0 但掉落高度大于 3 格，按原版规则兜底结算伤害
            float fallDistance = player.getFallDistance();
            if (event.getDamage() <= 0.0 && fallDistance > 3.0f) {
                event.setDamage(fallDistance - 3.0);
            }
        }
    }
}
