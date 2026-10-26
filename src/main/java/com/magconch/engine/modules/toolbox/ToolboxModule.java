package com.magconch.engine.modules.toolbox;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.toolbox.command.CraftCommand;
import com.magconch.engine.modules.toolbox.command.StonecutterCommand;
import com.magconch.engine.modules.toolbox.config.ToolboxConfig;
import org.bukkit.command.PluginCommand;

/**
 * 随身工具箱模块生命周期实现
 * <p>
 * 提供快捷随身工作台与切石机窗口，无需依赖物理方块，关界面物品自动回包。
 */
public class ToolboxModule implements EngineModule {

    // ==========================================
    // 常量与状态字段
    // ==========================================

    private static final String MODULE_ID = "toolbox";

    private final MagconchEngine plugin;
    private final ToolboxConfig config = new ToolboxConfig();

    private boolean enabled;
    private CraftCommand craftCommand;
    private StonecutterCommand stonecutterCommand;

    public ToolboxModule(MagconchEngine plugin) {
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

        // 2. 实例化并注册工作台指令
        if (craftCommand == null) {
            craftCommand = new CraftCommand(plugin, config);
            plugin.getMainCommand().registerSubCommand(craftCommand);

            PluginCommand craftAlias = plugin.getCommand("craft");
            if (craftAlias != null) {
                craftAlias.setExecutor(craftCommand);
                craftAlias.setTabCompleter(craftCommand);
            }
        }

        // 3. 实例化并注册切石机指令
        if (stonecutterCommand == null) {
            stonecutterCommand = new StonecutterCommand(plugin, config);
            plugin.getMainCommand().registerSubCommand(stonecutterCommand);

            PluginCommand stonecutterAlias = plugin.getCommand("stonecutter");
            if (stonecutterAlias != null) {
                stonecutterAlias.setExecutor(stonecutterCommand);
                stonecutterAlias.setTabCompleter(stonecutterCommand);
            }
        }

        this.enabled = config.isEnabled();
    }

    @Override
    public void onDisable() {
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
}
