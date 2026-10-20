package com.magconch.engine.modules.signcolor;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.signcolor.config.SignColorConfig;
import com.magconch.engine.modules.signcolor.listener.SignColorListener;
import org.bukkit.event.HandlerList;

/**
 * 告示牌彩色与十六进制色支持模块
 */
public class SignColorModule implements EngineModule {

    private static final String MODULE_ID = "signcolor";

    private final MagconchEngine plugin;
    private final SignColorConfig config = new SignColorConfig();

    private boolean enabled;
    private SignColorListener listener;

    public SignColorModule(MagconchEngine plugin) {
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
            listener = new SignColorListener(plugin, config);
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

    public SignColorConfig getConfig() {
        return config;
    }
}
