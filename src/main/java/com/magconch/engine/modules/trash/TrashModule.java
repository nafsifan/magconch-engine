package com.magconch.engine.modules.trash;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.trash.command.TrashCommand;
import com.magconch.engine.modules.trash.config.TrashConfig;
import com.magconch.engine.modules.trash.listener.TrashListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

/**
 * 虚拟垃圾桶模块生命周期实现
 * <p>
 * 提供快捷随身垃圾桶窗口，放入物品并在关闭后由 JVM 垃圾回收自动销毁，零持久化负担。
 */
public class TrashModule implements EngineModule {

    // ==========================================
    // 常量与状态字段
    // ==========================================

    private static final String MODULE_ID = "trash";

    private final MagconchEngine plugin;
    private final TrashConfig config = new TrashConfig();

    private boolean enabled;
    private TrashListener listener;
    private TrashCommand trashCommand;

    public TrashModule(MagconchEngine plugin) {
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

        // 2. 实例化并注册指令
        if (trashCommand == null) {
            trashCommand = new TrashCommand(plugin, this, config);
            plugin.getMainCommand().registerSubCommand(trashCommand);

            PluginCommand trashAlias = plugin.getCommand("trash");
            if (trashAlias != null) {
                trashAlias.setExecutor(trashCommand);
                trashAlias.setTabCompleter(trashCommand);
            }
        }

        // 3. 注册事件监听器
        if (listener == null) {
            listener = new TrashListener(config);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }

        this.enabled = config.isEnabled();
        plugin.getLogger().info("垃圾桶模块 (trash) 已启用 (容量: " + config.getSize() + " 格)。");
    }

    @Override
    public void onDisable() {
        // 1. 注销监听器
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }

        this.enabled = false;
        plugin.getLogger().info("垃圾桶模块 (trash) 已安全停用。");
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

    public TrashConfig getConfig() {
        return config;
    }
}
