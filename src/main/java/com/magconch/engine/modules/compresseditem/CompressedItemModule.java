package com.magconch.engine.modules.compresseditem;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.compresseditem.config.CompressedItemConfig;
import com.magconch.engine.modules.compresseditem.listener.CompressedProtectListener;
import com.magconch.engine.modules.compresseditem.listener.CompressionCraftListener;
import org.bukkit.event.HandlerList;

/**
 * 高密压缩物品与全场景仓储保护模块生命周期实现
 * <p>
 * 提供原版工作台 9合1 平滑压缩与 1拆9 随身无损解压引擎，
 * 并全链路阻断危险工匠台误损、防误放与防掉落刷物。
 */
public class CompressedItemModule implements EngineModule {

    // ==========================================
    // 常量与状态字段
    // ==========================================

    private static final String MODULE_ID = "compressed-item";

    private final MagconchEngine plugin;
    private final CompressedItemConfig config = new CompressedItemConfig();

    private boolean enabled;
    private CompressionCraftListener craftListener;
    private CompressedProtectListener protectListener;

    public CompressedItemModule(MagconchEngine plugin) {
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
        // 1. 载入模块配置
        config.load(plugin.getConfig());

        // 2. 注册核心动态合成与解压监听器
        if (craftListener == null) {
            craftListener = new CompressionCraftListener(plugin, config);
            plugin.getServer().getPluginManager().registerEvents(craftListener, plugin);
        }

        // 3. 注册全场景安全与工匠容器隔离监听器
        if (protectListener == null) {
            protectListener = new CompressedProtectListener(plugin);
            plugin.getServer().getPluginManager().registerEvents(protectListener, plugin);
        }

        this.enabled = config.isEnabled();
    }

    @Override
    public void onDisable() {
        // 1. 安全注销事件监听器
        if (craftListener != null) {
            HandlerList.unregisterAll(craftListener);
            craftListener = null;
        }
        if (protectListener != null) {
            HandlerList.unregisterAll(protectListener);
            protectListener = null;
        }

        this.enabled = false;
    }

    @Override
    public void onReload() {
        onDisable();
        onEnable();
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
    // 配置访问器
    // ==========================================

    public CompressedItemConfig getConfig() {
        return config;
    }
}
