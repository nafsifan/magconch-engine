package com.magconch.engine.modules.showitem.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 物品展示模块配置封装
 */
public class ShowItemConfig {

    private boolean enabled = true;
    private int cooldown = 3;

    /**
     * 从核心配置文件中加载物品展示模块相关参数
     *
     * @param config 根配置对象
     */
    public void load(FileConfiguration config) {
        this.enabled = config.getBoolean("modules.show-item.enabled", true);
        this.cooldown = Math.max(0, config.getInt("modules.show-item.cooldown", 3));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getCooldown() {
        return cooldown;
    }
}
