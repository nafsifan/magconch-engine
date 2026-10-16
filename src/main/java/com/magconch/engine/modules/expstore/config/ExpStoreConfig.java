package com.magconch.engine.modules.expstore.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 经验存储模块配置管理器
 * <p>
 * 负责解析与持久化经验灌装相关的单瓶点数、音效开关、粒子效果及模型参数。
 */
public class ExpStoreConfig {

    // ==========================================
    // 配置字段与默认值
    // ==========================================

    private boolean enabled = true;
    private int pointsPerBottle = 100;
    private boolean soundEnabled = true;
    private boolean particleEnabled = true;
    private int customModelData = 0;

    // ==========================================
    // 配置加载
    // ==========================================

    /**
     * 从核心配置文件读取配置参数
     *
     * @param config 主配置文件对象
     */
    public void load(FileConfiguration config) {
        if (config == null) {
            return;
        }

        String path = "modules.exp-store.";
        this.enabled = config.getBoolean(path + "enabled", true);
        this.pointsPerBottle = Math.max(1, config.getInt(path + "points-per-bottle", 100));
        this.soundEnabled = config.getBoolean(path + "sound-enabled", true);
        this.particleEnabled = config.getBoolean(path + "particle-enabled", true);
        this.customModelData = Math.max(0, config.getInt(path + "custom-model-data", 0));
    }

    // ==========================================
    // 访问器 (Getters)
    // ==========================================

    public boolean isEnabled() {
        return enabled;
    }

    public int getPointsPerBottle() {
        return pointsPerBottle;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public boolean isParticleEnabled() {
        return particleEnabled;
    }

    public int getCustomModelData() {
        return customModelData;
    }
}
