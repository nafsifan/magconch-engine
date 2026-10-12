package com.magconch.engine.modules.flight.listener;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.flight.FlightModule;
import com.magconch.engine.modules.flight.config.FlightConfig;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;

/**
 * 飞行姿态切换监听器
 * <p>
 * 监听双击空格起落动作，拦截低饱食度玩家起飞并同步设置摔落伤害标志。
 */
public class FlightToggleListener implements Listener {

    // ==========================================
    // 依赖
    // ==========================================

    private final MagconchEngine plugin;
    private final FlightConfig config;

    public FlightToggleListener(MagconchEngine plugin, FlightConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ==========================================
    // 事件处理
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        // 1. 创造与旁观模式豁免检测
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }

        // 2. 玩家尝试双击起飞
        if (event.isFlying()) {
            // 饱食度过低拦截
            if (player.getFoodLevel() <= config.getMinFoodLevel()) {
                event.setCancelled(true);
                player.setFlying(false);
                plugin.getLanguageManager().sendMessage(player, "flight.too_hungry");
                return;
            }

            // 饱食度充足，准许起飞并适配跨端摔落伤害标记
            FlightModule.enableFlyingFallDamage(player);
        } else {
            // 3. 玩家双击取消飞行（主动落体），确保摔落伤害正常计算
            FlightModule.enableFlyingFallDamage(player);
        }
    }
}
