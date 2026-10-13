package com.magconch.engine.modules.trash.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 垃圾桶模块配置封装
 */
public class TrashConfig {

    private boolean enabled = true;
    private int rows = 4;
    private boolean soundEnabled = true;

    /**
     * 从核心配置文件中加载垃圾桶模块相关参数
     *
     * @param config 根配置对象
     */
    public void load(FileConfiguration config) {
        this.enabled = config.getBoolean("modules.trash.enabled", true);
        this.rows = Math.max(1, Math.min(6, config.getInt("modules.trash.rows", 4)));
        this.soundEnabled = config.getBoolean("modules.trash.sound-enabled", true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getRows() {
        return rows;
    }

    public int getSize() {
        return rows * 9;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }
}
