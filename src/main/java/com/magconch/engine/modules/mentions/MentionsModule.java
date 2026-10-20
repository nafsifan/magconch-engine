package com.magconch.engine.modules.mentions;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.mentions.config.MentionsConfig;
import com.magconch.engine.modules.mentions.listener.ChatMentionListener;
import com.magconch.engine.modules.mentions.listener.ConsoleSayListener;
import com.magconch.engine.modules.mentions.listener.MentionTabCompleteListener;
import com.magconch.engine.modules.mentions.util.MentionParser;
import org.bukkit.event.HandlerList;

/**
 * 聊天提及艾特系统模块生命周期契约实现
 * <p>
 * 提供 @玩家、@everyone 与控制台广播提及、原生聊天补全以及防刷视听提醒。
 */
public class MentionsModule implements EngineModule {

    private static final String MODULE_ID = "mentions";

    private final MagconchEngine plugin;
    private final MentionsConfig config = new MentionsConfig();

    private boolean enabled;
    private MentionParser parser;
    private ChatMentionListener chatListener;
    private ConsoleSayListener consoleSayListener;
    private MentionTabCompleteListener tabListener;

    public MentionsModule(MagconchEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        // 1. 加载模块配置
        config.load(plugin.getConfig());

        // 2. 实例化解析器与各事件监听器
        this.parser = new MentionParser(plugin, config);

        if (chatListener == null) {
            chatListener = new ChatMentionListener(config, parser);
            plugin.getServer().getPluginManager().registerEvents(chatListener, plugin);
        }
        if (consoleSayListener == null) {
            consoleSayListener = new ConsoleSayListener(plugin, config, parser);
            plugin.getServer().getPluginManager().registerEvents(consoleSayListener, plugin);
        }
        if (tabListener == null) {
            tabListener = new MentionTabCompleteListener(config, parser);
            plugin.getServer().getPluginManager().registerEvents(tabListener, plugin);
        }

        // 3. 初始同步全服在线玩家补全候选列表
        tabListener.syncAll();

        this.enabled = config.isEnabled();
    }

    @Override
    public void onDisable() {
        if (chatListener != null) {
            HandlerList.unregisterAll(chatListener);
            chatListener = null;
        }
        if (consoleSayListener != null) {
            HandlerList.unregisterAll(consoleSayListener);
            consoleSayListener = null;
        }
        if (tabListener != null) {
            HandlerList.unregisterAll(tabListener);
            tabListener = null;
        }

        this.parser = null;
        this.enabled = false;
    }

    @Override
    public void onReload() {
        config.load(plugin.getConfig());
        if (tabListener != null) {
            tabListener.syncAll();
        }
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

    public MentionsConfig getConfig() {
        return config;
    }
}
