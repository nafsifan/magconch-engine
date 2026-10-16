package com.magconch.engine.modules.flight.task;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.flight.FlightModule;
import com.magconch.engine.modules.flight.config.FlightConfig;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞行饱食度检查与消耗单一定时任务
 * <p>
 * 每秒运行一次，负责监测飞行玩家姿态、累积飞行时间、执行饱和度优先扣减并在饥饿时安全中断飞行。
 */
public class FlightHungerTask extends BukkitRunnable {

    // ==========================================
    // 依赖与瞬态状态
    // ==========================================

    private final MagconchEngine plugin;
    private final FlightConfig config;
    private final Map<UUID, Integer> flightSecondsMap = new ConcurrentHashMap<>();

    // CraftPlayer/Paper 网络包强制同步反射缓存
    private static Method sendHealthUpdateMethod;
    private static boolean healthUpdateProbed;

    public FlightHungerTask(MagconchEngine plugin, FlightModule flightModule, FlightConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ==========================================
    // 任务调度逻辑
    // ==========================================

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            // 1. 创造与旁观模式不受饱食度限制，清理瞬态计数
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                flightSecondsMap.remove(uuid);
                continue;
            }

            // 2. 玩家当前未处于飞行姿态：保留累计秒数，等待下一次起飞继续累加
            if (!player.isFlying()) {
                continue;
            }

            // 3. 检查最低饱食度限制：已达临界值则立即断飞下坠
            int minFood = config.getMinFoodLevel();
            if (player.getFoodLevel() <= minFood) {
                player.setFlying(false);
                flightSecondsMap.remove(uuid);
                FlightModule.enableFlyingFallDamage(player);
                plugin.getLanguageManager().sendMessage(player, "flight.exhausted");
                continue;
            }

            // 4. 累计飞行时长并按配置间隔扣减饱食
            int currentSeconds = flightSecondsMap.getOrDefault(uuid, 0) + 1;
            int interval = Math.max(1, config.getIntervalSeconds());

            if (currentSeconds >= interval) {
                // 达到扣除周期，执行原版级饱和度优先扣除并向客户端强制推送数据包
                float cost = config.getCostPerInterval();
                consumeFood(player, cost);
                flightSecondsMap.put(uuid, 0);

                // 扣除后再次复核饱食度，若已触底立即断飞；若仅够最后 1 次消耗则发出 ActionBar 预警
                int foodAfter = player.getFoodLevel();
                if (foodAfter <= minFood) {
                    player.setFlying(false);
                    flightSecondsMap.remove(uuid);
                    FlightModule.enableFlyingFallDamage(player);
                    plugin.getLanguageManager().sendMessage(player, "flight.exhausted");
                } else if (player.getSaturation() <= 0f && foodAfter <= minFood + Math.max(1, (int) Math.ceil(cost))) {
                    plugin.getLanguageManager().sendActionBar(player, "flight.low_hunger");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
                }
            } else {
                flightSecondsMap.put(uuid, currentSeconds);
            }
        }

        // 清理已离线玩家的瞬态缓存
        flightSecondsMap.keySet().removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null);
    }

    /**
     * 清理所有瞬态计时缓存
     */
    public void clear() {
        flightSecondsMap.clear();
    }

    // ==========================================
    // 饱食度原版级消耗算法与网络包同步
    // ==========================================

    /**
     * 原版级饱食扣减算法（贴合 Minecraft 饱和度优先机制，并强制触发网络包同步）
     *
     * @param player 目标玩家
     * @param amount 扣减消耗点数
     */
    public static void consumeFood(Player player, float amount) {
        float saturation = player.getSaturation();
        if (saturation >= amount) {
            player.setSaturation(saturation - amount);
        } else {
            float remaining = amount - saturation;
            player.setSaturation(0f);
            int foodLevel = player.getFoodLevel();
            player.setFoodLevel(Math.max(0, foodLevel - (int) Math.ceil(remaining)));
        }

        // 核心修复：强制向客户端推送 SetHealth 数据包，使 AppleSkin 等模组能够实时渲染饱和度下降
        syncHealthToClient(player);
    }

    /**
     * 向客户端强制同步生命与饱食/饱和度数据包
     *
     * @param player 目标玩家
     */
    public static void syncHealthToClient(Player player) {
        if (player == null) {
            return;
        }

        if (!healthUpdateProbed) {
            probeHealthUpdateApi(player);
        }

        if (sendHealthUpdateMethod != null) {
            try {
                sendHealthUpdateMethod.invoke(player);
            } catch (Throwable ignored) {
                // 降级静默处理
            }
        }
    }

    private static synchronized void probeHealthUpdateApi(Player player) {
        if (healthUpdateProbed) {
            return;
        }
        healthUpdateProbed = true;
        try {
            sendHealthUpdateMethod = player.getClass().getMethod("sendHealthUpdate");
            sendHealthUpdateMethod.setAccessible(true);
        } catch (Throwable ignored) {
            // 纯 Spigot 低版本若缺失该方法则降级由原版默认逻辑同步
        }
    }
}
