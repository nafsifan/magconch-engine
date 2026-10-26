package com.magconch.engine.modules.enderchest.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 扩展末影箱模块配置载体
 * <p>
 * 统一管理 54 格扩展末影箱功能的启用与运行时选项。
 */
public class EnderChestConfig {

    // ==========================================
    // 状态字段
    // ==========================================

    private boolean enabled = true;

    // ==========================================
    // 加载与刷新
    // ==========================================

    /**
     * 从主配置文件中加载扩展末影箱配置
     *
     * @param config 主配置对象
     */
    public void load(FileConfiguration config) {
        if (config == null) {
            return;
        }

        this.enabled = config.getBoolean("modules.enderchest.enabled", true);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
