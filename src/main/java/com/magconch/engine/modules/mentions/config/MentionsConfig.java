package com.magconch.engine.modules.mentions.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 聊天提及艾特系统配置映射
 * <p>
 * 负责从核心配置中解析 mentions 模块的启用状态。
 */
public class MentionsConfig {

    private boolean enabled = true;

    /**
     * 从主配置文件加载 mentions 模块状态
     *
     * @param config 主配置文件对象
     */
    public void load(FileConfiguration config) {
        if (config == null) {
            return;
        }
        this.enabled = config.getBoolean("modules.mentions.enabled", true);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
