package com.magconch.engine.modules.enderchest;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.enderchest.command.EnderChestCommand;
import com.magconch.engine.modules.enderchest.config.EnderChestConfig;
import com.magconch.engine.modules.enderchest.listener.EnderChestListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

/**
 * 扩展末影箱模块生命周期实现
 * <p>
 * 提供全局 54 格末影箱容量扩展、原版物理方块接管、/ec 便捷查阅与关服持久化保障。
 */
public class EnderChestModule implements EngineModule {

    // ==========================================
    // 常量与状态字段
    // ==========================================

    private static final String MODULE_ID = "enderchest";

    private final MagconchEngine plugin;
    private final EnderChestConfig config = new EnderChestConfig();

    private boolean enabled;
    private EnderChestManager manager;
    private EnderChestListener listener;
    private EnderChestCommand command;

    public EnderChestModule(MagconchEngine plugin) {
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

        // 2. 初始化核心管理器
        if (manager == null) {
            manager = new EnderChestManager(plugin);
        }

        // 3. 注册指令处理器
        if (command == null) {
            command = new EnderChestCommand(plugin, manager, config);
            plugin.getMainCommand().registerSubCommand(command);

            PluginCommand ecAlias = plugin.getCommand("ec");
            if (ecAlias != null) {
                ecAlias.setExecutor(command);
                ecAlias.setTabCompleter(command);
            }
        }

        // 4. 注册事件监听器
        if (listener == null) {
            listener = new EnderChestListener(plugin, manager, config);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }

        this.enabled = config.isEnabled();
    }

    @Override
    public void onDisable() {
        // 1. 关服或卸载时强制将内存中所有活跃末影箱刷盘
        if (manager != null) {
            manager.flushAll();
        }

        // 2. 注销监听器
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

    public EnderChestManager getManager() {
        return manager;
    }
}
