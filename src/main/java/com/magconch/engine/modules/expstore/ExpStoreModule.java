package com.magconch.engine.modules.expstore;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.expstore.config.ExpStoreConfig;
import com.magconch.engine.modules.expstore.listener.ExpStoreListener;
import org.bukkit.event.HandlerList;

/**
 * 经验存储模块生命周期实现
 * <p>
 * 提供潜行右键附魔台定额灌装与右键无损吸收的实体经验存储方案。
 */
public class ExpStoreModule implements EngineModule {

    // ==========================================
    // 常量与状态字段
    // ==========================================

    private static final String MODULE_ID = "exp-store";

    private final MagconchEngine plugin;
    private final ExpStoreConfig config = new ExpStoreConfig();

    private boolean enabled;
    private ExpStoreListener listener;

    public ExpStoreModule(MagconchEngine plugin) {
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
            listener = new ExpStoreListener(plugin, config);
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

    public ExpStoreConfig getConfig() {
        return config;
    }
}
