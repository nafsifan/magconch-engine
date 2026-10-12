package com.magconch.engine.modules.flight.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 飞行模块配置映射与读取
 * <p>
 * 提供对饱食度门槛、消耗间隔及单次消耗点数的只读映射。
 */
public class FlightConfig {

    // ==========================================
    // 配置字段与默认值
    // ==========================================

    private int minFoodLevel = 6;
    private int intervalSeconds = 20;
    private float costPerInterval = 1.0f;

    // ==========================================
    // 配置载入
    // ==========================================

    /**
     * 从主配置文件中重新加载飞行模块专属配置段
     *
     * @param config 主配置对象
     */
    public void load(FileConfiguration config) {
        if (config == null) {
            return;
        }

        String path = "modules.flight.hunger.";
        this.minFoodLevel = config.getInt(path + "min-food-level", 6);
        this.intervalSeconds = config.getInt(path + "interval-seconds", 20);
        this.costPerInterval = (float) config.getDouble(path + "cost-per-interval", 1.0);
    }

    // ==========================================
    // Getters
    // ==========================================

    public int getMinFoodLevel() {
        return minFoodLevel;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public float getCostPerInterval() {
        return costPerInterval;
    }
}
