package com.magconch.engine.modules.trash.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 垃圾桶模块配置封装
 */
public class TrashConfig {

    public static final int INVENTORY_SIZE = 36;

    private boolean enabled = true;

    /**
     * 从核心配置文件中加载垃圾桶模块相关参数
     *
     * @param config 根配置对象
     */
    public void load(FileConfiguration config) {
        this.enabled = config.getBoolean("modules.trash.enabled", true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSize() {
        return INVENTORY_SIZE;
    }
}
