package com.magconch.engine.modules.showitem.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 物品展示模块配置封装
 */
public class ShowItemConfig {

    private boolean enabled = true;

    /**
     * 从核心配置文件中加载物品展示模块相关参数
     *
     * @param config 根配置对象
     */
    public void load(FileConfiguration config) {
        this.enabled = config.getBoolean("modules.show-item.enabled", true);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
