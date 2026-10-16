package com.magconch.engine.core;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.util.ConfigUpdater;
import com.magconch.engine.util.TextUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * 国际化多语言管理服务
 * <p>
 * 负责解析加载对应语言包、动态占位符替换与终端发信。
 */
public class LanguageManager {

    // ==========================================
    // 依赖与状态
    // ==========================================

    private final MagconchEngine plugin;
    private FileConfiguration langConfig;
    private String currentLanguage;
    private String cachedPrefix = "";

    public LanguageManager(MagconchEngine plugin) {
        this.plugin = plugin;
    }

    // ==========================================
    // 生命周期与文件加载
    // ==========================================

    /**
     * 加载当前配置设定的语言包
     */
    public void load() {
        this.currentLanguage = plugin.getConfig().getString("language", "zh_CN");
        File langFile = new File(plugin.getDataFolder(), "languages/" + currentLanguage + ".yml");

        // 容错处理：若指定语言包不存在，则自动降级为中文 zh_CN
        if (!langFile.exists()) {
            plugin.getLogger().warning("未找到指定的语言文件 languages/" + currentLanguage + ".yml，正在回退至 zh_CN.yml");
            this.currentLanguage = "zh_CN";
            langFile = new File(plugin.getDataFolder(), "languages/zh_CN.yml");
        }

        // 自动同步语言键（补齐新增键与清理废弃键）
        String resourcePath = "languages/" + currentLanguage + ".yml";
        int changedKeys = ConfigUpdater.update(plugin, resourcePath, langFile);
        if (changedKeys > 0) {
            plugin.getLogger().info("语言文件 " + langFile.getName() + " 已自动同步 " + changedKeys + " 项语言键变更。");
        }

        // 加载本地磁盘语言配置 (服主自定义优先)
        this.langConfig = YamlConfiguration.loadConfiguration(langFile);

        // 加载 jar 内置资源作为默认回退，新版本新增词条自动透明兜底，永不覆写服主磁盘文件
        InputStream defStream = plugin.getResource("languages/" + currentLanguage + ".yml");
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            this.langConfig.setDefaults(defConfig);
        }

        // 缓存前缀并提前完成颜色渲染
        String rawPrefix = langConfig.getString("prefix", "&8[&x&0&0&b&4&d&8Magconch&8] &7");
        this.cachedPrefix = TextUtil.colorize(rawPrefix);
    }

    /**
     * 重载语言系统
     */
    public void reload() {
        load();
    }

    // ==========================================
    // 文本提取与占位符处理
    // ==========================================

    /**
     * 获取指定键的本地化消息文本（已完成前缀注入与颜色代码解析）
     *
     * @param key 语言节点路径
     * @return 格式化后的最终展示文本
     */
    public String getRawMessage(String key) {
        if (langConfig == null) {
            return key;
        }
        return langConfig.getString(key, key);
    }

    /**
     * 获取带占位符替换的单行格式化文本
     *
     * @param key          配置键
     * @param placeholders 成对占位符数组 (例如: "{time}", "12", "{player}", "Steve")
     * @return 渲染完毕的文本
     */
    public String getMessage(String key, Object... placeholders) {
        String message = getRawMessage(key);
        return formatText(message, placeholders);
    }

    /**
     * 获取多行文本列表
     *
     * @param key 配置键
     * @return 渲染完毕的彩色文本列表
     */
    public List<String> getMessageList(String key) {
        if (langConfig == null || !langConfig.contains(key)) {
            return Collections.emptyList();
        }
        List<String> list = langConfig.getStringList(key);
        if (list.isEmpty()) {
            if (langConfig.isList(key)) {
                return Collections.emptyList();
            }
            String single = getMessage(key);
            return single.isEmpty() ? Collections.emptyList() : Collections.singletonList(single);
        }
        return TextUtil.colorize(list);
    }

    /**
     * 替换文本中的基础占位符与自定义占位符
     */
    private String formatText(String message, Object... placeholders) {
        if (message == null) {
            return "";
        }

        // 1. 替换系统统一前缀
        message = message.replace("{prefix}", cachedPrefix);

        // 2. 批量替换成对自定义占位符
        if (placeholders != null && placeholders.length >= 2) {
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length && placeholders[i] != null && placeholders[i + 1] != null) {
                    message = message.replace(String.valueOf(placeholders[i]), String.valueOf(placeholders[i + 1]));
                }
            }
        }

        // 3. 执行最终色彩渲染
        return TextUtil.colorize(message);
    }

    // ==========================================
    // 终端发信 API
    // ==========================================

    /**
     * 向目标发送本地化信息
     *
     * @param receiver     消息接收者（玩家或控制台）
     * @param key          语言键
     * @param placeholders 成对占位符
     */
    public void sendMessage(CommandSender receiver, String key, Object... placeholders) {
        if (receiver == null) {
            return;
        }
        receiver.sendMessage(getMessage(key, placeholders));
    }

    // ==========================================
    // Getter
    // ==========================================

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public String getCachedPrefix() {
        return cachedPrefix;
    }
}
