package com.magconch.engine.modules.signedit.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 告示牌编辑模块配置管理器
 * <p>
 * 负责解析模块启用状态。
 */
public class SignEditConfig {

    private boolean enabled = true;

    /**
     * 从主配置文件加载告示牌编辑模块状态
     *
     * @param config 主配置文件对象
     */
    public void load(FileConfiguration config) {
        if (config == null) {
            return;
        }
        this.enabled = config.getBoolean("modules.signedit.enabled", true);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
