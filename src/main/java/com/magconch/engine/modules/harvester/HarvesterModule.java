package com.magconch.engine.modules.harvester;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.harvester.config.HarvesterConfig;
import com.magconch.engine.modules.harvester.listener.HarvesterListener;
import org.bukkit.event.HandlerList;

/**
 * 快速右键收菜模块生命周期实现
 * <p>
 * 提供成熟农作物右键即收并自动补种功能，提升玩家农业生产与生存体验。
 */
public class HarvesterModule implements EngineModule {

    // ==========================================
    // 常量与状态字段
    // ==========================================

    private static final String MODULE_ID = "harvester";

    private final MagconchEngine plugin;
    private final HarvesterConfig config = new HarvesterConfig();

    private boolean enabled;
    private HarvesterListener listener;

    public HarvesterModule(MagconchEngine plugin) {
        this.plugin = plugin;
    }

    // ==========================================
    // EngineModule 生命周期契约
    // ==========================================

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        // 1. 加载模块配置
        config.load(plugin.getConfig());

        // 2. 注册交互监听器
        if (listener == null) {
            listener = new HarvesterListener(plugin, config);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }

        this.enabled = config.isEnabled();
    }

    @Override
    public void onDisable() {
        // 注销事件监听器
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }

        this.enabled = false;
    }

    @Override
    public void onReload() {
        config.load(plugin.getConfig());
        this.enabled = config.isEnabled();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // ==========================================
    // 访问器 (Getters)
    // ==========================================

    public HarvesterConfig getConfig() {
        return config;
    }
}
