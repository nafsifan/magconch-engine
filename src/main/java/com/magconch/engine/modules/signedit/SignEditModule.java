package com.magconch.engine.modules.signedit;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.signedit.config.SignEditConfig;
import com.magconch.engine.modules.signedit.listener.SignEditListener;
import org.bukkit.event.HandlerList;

/**
 * 告示牌无损速改与颜色解析模块
 * <p>
 * 支持潜行右键重新编辑已放置告示牌，并在提交文本时支持 & 颜色与十六进制色。
 */
public class SignEditModule implements EngineModule {

    private static final String MODULE_ID = "signedit";

    private final MagconchEngine plugin;
    private final SignEditConfig config = new SignEditConfig();

    private boolean enabled;
    private SignEditListener listener;

    public SignEditModule(MagconchEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        config.load(plugin.getConfig());

        if (listener == null) {
            listener = new SignEditListener(plugin, config);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }

        this.enabled = config.isEnabled();
    }

    @Override
    public void onDisable() {
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

    public SignEditConfig getConfig() {
        return config;
    }
}
