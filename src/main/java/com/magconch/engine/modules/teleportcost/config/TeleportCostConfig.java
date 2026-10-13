package com.magconch.engine.modules.teleportcost.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 传送距离阶梯扣费模块配置映射与读取
 * <p>
 * 提供对开方费率参数、随机传送门票及跨维度附加费的只读映射。
 */
public class TeleportCostConfig {

    // ==========================================
    // 配置字段与默认值
    // ==========================================

    private boolean enabled = true;
    private int baseCost = 5;
    private double distanceRate = 0.8;
    private int maxCost = 150;
    private int rtpCost = 40;
    private int netherExtraCost = 15;
    private int theEndExtraCost = 25;

    // ==========================================
    // 配置载入
    // ==========================================

    /**
     * 从主配置文件中加载传送扣费模块专属配置段
     *
     * @param config 主配置对象
     */
    public void load(FileConfiguration config) {
        if (config == null) {
            return;
        }

        String root = "modules.teleport-cost.";
        this.enabled = config.getBoolean(root + "enabled", true);
        this.baseCost = Math.max(0, config.getInt(root + "base-cost", 5));
        this.distanceRate = Math.max(0.0, config.getDouble(root + "distance-rate", 0.8));
        this.maxCost = Math.max(0, config.getInt(root + "max-cost", 150));
        this.rtpCost = Math.max(0, config.getInt(root + "rtp-cost", 40));
        this.netherExtraCost = Math.max(0, config.getInt(root + "cross-world.nether", 15));
        this.theEndExtraCost = Math.max(0, config.getInt(root + "cross-world.the-end", 25));
    }

    // ==========================================
    // Getters
    // ==========================================

    public boolean isEnabled() {
        return enabled;
    }

    public int getBaseCost() {
        return baseCost;
    }

    public double getDistanceRate() {
        return distanceRate;
    }

    public int getMaxCost() {
        return maxCost;
    }

    public int getRtpCost() {
        return rtpCost;
    }

    public int getNetherExtraCost() {
        return netherExtraCost;
    }

    public int getTheEndExtraCost() {
        return theEndExtraCost;
    }
}
