package com.magconch.engine.modules.showitem;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.showitem.command.ShowItemCommand;
import com.magconch.engine.modules.showitem.config.ShowItemConfig;
import org.bukkit.command.PluginCommand;

/**
 * 物品展示模块生命周期实现
 * <p>
 * 提供全服聊天物品展示功能，玩家可通过指令向所有在线玩家广播手持物品，
 * 其他玩家鼠标悬浮即可查看完整物品提示框（附魔/Lore/耐久等）。
 */
public class ShowItemModule implements EngineModule {

    // ==========================================
    // 常量与状态字段
    // ==========================================

    private static final String MODULE_ID = "show-item";

    private final MagconchEngine plugin;
    private final ShowItemConfig config = new ShowItemConfig();

    private boolean enabled;
    private ShowItemCommand showItemCommand;

    public ShowItemModule(MagconchEngine plugin) {
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

        // 2. 实例化并注册指令（仅首次）
        if (showItemCommand == null) {
            showItemCommand = new ShowItemCommand(plugin, this, config);
            plugin.getMainCommand().registerSubCommand(showItemCommand);

            // 注册全局快捷指令 /showitem (/show 别名)
            PluginCommand showAlias = plugin.getCommand("showitem");
            if (showAlias != null) {
                showAlias.setExecutor(showItemCommand);
                showAlias.setTabCompleter(showItemCommand);
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

    public ShowItemConfig getConfig() {
        return config;
    }
}
