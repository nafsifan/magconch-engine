package com.magconch.engine.core;

import com.magconch.engine.MagconchEngine;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * 核心配置管理模块
 * <p>
 * 负责主配置文件 (config.yml) 及外部资源的自动释放、持久化与安全重载。
 */
public class ConfigManager {

    // ==========================================
    // 依赖与状态
    // ==========================================

    private final MagconchEngine plugin;
    private final File configFile;
    private FileConfiguration config;

    public ConfigManager(MagconchEngine plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    // ==========================================
    // 配置生命周期
    // ==========================================

    /**
     * 初始化加载配置并确保默认静态资源释放落盘
     */
    public void load() {
        // 1. 确保数据目录与基础文件已释放
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        plugin.saveDefaultConfig();
        saveDefaultResource("languages/zh_CN.yml");
        saveDefaultResource("languages/en_US.yml");

        // 2. 加载主配置文件内容
        reload();
    }

    /**
     * 重载主配置文件
     */
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    /**
     * 保存当前内存中的主配置至磁盘
     */
    public void save() {
        if (config == null || configFile == null) {
            return;
        }
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法保存 config.yml 配置文件至本地磁盘!", e);
        }
    }

    // ==========================================
    // 辅助工具方法
    // ==========================================

    /**
     * 安全导出 jar 内置的预设资源文件
     *
     * @param resourcePath 相对路径
     */
    public void saveDefaultResource(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    /**
     * 获取指定路径的独立 YAML 配置文件对象
     *
     * @param relativePath 相对数据目录路径
     * @return FileConfiguration 实例
     */
    public FileConfiguration loadCustomConfig(String relativePath) {
        File file = new File(plugin.getDataFolder(), relativePath);
        if (!file.exists()) {
            saveDefaultResource(relativePath);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    // ==========================================
    // Getter
    // ==========================================

    public FileConfiguration getConfig() {
        return config;
    }
}
