package com.magconch.engine.modules.toolbox.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 随身工具箱模块配置载体
 * <p>
 * 统一管理工作台与切石机等快捷功能的开关状态。
 */
public class ToolboxConfig {

    // ==========================================
    // 配置状态字段
    // ==========================================

    private boolean enabled = true;
    private boolean craftEnabled = true;
    private boolean stonecutterEnabled = true;
    private boolean enderchestEnabled = true;

    // ==========================================
    // 加载与刷新
    // ==========================================

    /**
     * 从主配置文件中加载随身工具箱配置段
     *
     * @param config 主配置对象
     */
    public void load(FileConfiguration config) {
        if (config == null) {
            return;
        }

        this.enabled = config.getBoolean("modules.toolbox.enabled", true);
        this.craftEnabled = config.getBoolean("modules.toolbox.craft.enabled", true);
        this.stonecutterEnabled = config.getBoolean("modules.toolbox.stonecutter.enabled", true);
        this.enderchestEnabled = config.getBoolean("modules.toolbox.enderchest.enabled", true);
    }

    // ==========================================
    // 属性访问器
    // ==========================================

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCraftEnabled() {
        return enabled && craftEnabled;
    }

    public boolean isStonecutterEnabled() {
        return enabled && stonecutterEnabled;
    }

    public boolean isEnderchestEnabled() {
        return enabled && enderchestEnabled;
    }
}
