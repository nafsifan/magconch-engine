package com.magconch.engine.modules.mark.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 标点模块配置
 */
public class MarkConfig {

    private boolean enabled = true;
    private int duration = 5;
    private double cooldown = 1.0;
    private double range = 64.0;

    /**
     * 从主配置文件加载标记模块配置
     *
     * @param config 主配置实例
     */
    public void load(FileConfiguration config) {
        if (config == null) {
            return;
        }

        String path = "modules.mark.";
        this.enabled = config.getBoolean(path + "enabled", true);
        this.duration = config.getInt(path + "duration", 5);
        this.cooldown = config.getDouble(path + "cooldown", 1.0);
        this.range = config.getDouble(path + "range", 64.0);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getDuration() {
        return duration;
    }

    public double getCooldown() {
        return cooldown;
    }

    public double getRange() {
        return range;
    }
}
