package com.magconch.engine.modules.teleportcost;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.teleportcost.config.TeleportCostConfig;
import com.magconch.engine.modules.teleportcost.listener.HuskHomesTeleportListener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

/**
 * 传送距离阶梯扣费模块生命周期实现
 * <p>
 * 负责软依赖 HuskHomes 的安全探测、隔离加载与热重载启停，
 * 严禁在本类顶层直接引用任何 HuskHomes 专有类，确保未安装前置时 100% 零报错跳过。
 */
public class TeleportCostModule implements EngineModule {

    // ==========================================
    // 常量与状态字段
    // ==========================================

    private static final String MODULE_ID = "teleport-cost";
    private static final String HUSKHOMES_PLUGIN_NAME = "HuskHomes";

    private final MagconchEngine plugin;
    private final TeleportCostConfig config = new TeleportCostConfig();

    private boolean enabled;
    private Listener teleportListener;

    public TeleportCostModule(MagconchEngine plugin) {
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
        config.load(plugin.getConfig());

        if (!config.isEnabled()) {
            this.enabled = false;
            plugin.getLogger().info("模块 [" + MODULE_ID + "] 已在配置中禁用，跳过加载。");
            return;
        }

        if (!isHuskHomesAvailable()) {
            this.enabled = false;
            plugin.getLogger().info("未检测到 HuskHomes 前置插件，模块 [" + MODULE_ID + "] 已自动跳过加载。");
            return;
        }

        registerListener();
        this.enabled = true;
    }

    @Override
    public void onDisable() {
        unregisterListener();
        this.enabled = false;
    }

    @Override
    public void onReload() {
        config.load(plugin.getConfig());

        if (!config.isEnabled() || !isHuskHomesAvailable()) {
            if (this.enabled) {
                unregisterListener();
                this.enabled = false;
                plugin.getLogger().info("模块 [" + MODULE_ID + "] 已在热重载中停用。");
            }
            return;
        }

        if (!this.enabled) {
            registerListener();
            this.enabled = true;
            plugin.getLogger().info("模块 [" + MODULE_ID + "] 已在热重载中启用。");
        }
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
    // 软依赖探测与监听器隔离管理
    // ==========================================

    private boolean isHuskHomesAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled(HUSKHOMES_PLUGIN_NAME);
    }

    private void registerListener() {
        if (teleportListener == null) {
            teleportListener = new HuskHomesTeleportListener(plugin, config);
            plugin.getServer().getPluginManager().registerEvents(teleportListener, plugin);
        }
    }

    private void unregisterListener() {
        if (teleportListener != null) {
            HandlerList.unregisterAll(teleportListener);
            teleportListener = null;
        }
    }

    // ==========================================
    // Getters
    // ==========================================

    public TeleportCostConfig getConfig() {
        return config;
    }
}
