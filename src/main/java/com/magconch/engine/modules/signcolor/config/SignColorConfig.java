package com.magconch.engine.modules.signcolor.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 告示牌彩色文字模块配置
 */
public class SignColorConfig {

    private boolean enabled = true;

    /**
     * 从主配置文件加载告示牌彩色文字模块状态
     *
     * @param config 主配置文件对象
     */
    public void load(FileConfiguration config) {
        if (config == null) {
            return;
        }
        this.enabled = config.getBoolean("modules.signcolor.enabled", true);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
