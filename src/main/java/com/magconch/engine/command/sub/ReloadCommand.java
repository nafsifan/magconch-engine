package com.magconch.engine.command.sub;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.command.SubCommand;
import com.magconch.engine.core.LanguageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * 引擎重载指令 (/mcore reload)
 * <p>
 * 采用异步预加载配置 + 主线程原子生效的最佳实践，消除重载时的卡顿。
 */
public class ReloadCommand implements SubCommand {

    private final MagconchEngine plugin;
    private final AtomicBoolean reloading = new AtomicBoolean(false);

    public ReloadCommand(MagconchEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getDescription() {
        return "commands.reload.description";
    }

    @Override
    public String getUsage() {
        return "reload";
    }

    @Override
    public String getPermission() {
        return "magconch.command.reload";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        LanguageManager lang = plugin.getLanguageManager();

        // 若正在执行重载则直接忽略重复输入
        if (!reloading.compareAndSet(false, true)) {
            return true;
        }

        lang.sendMessage(sender, "reload.start");
        long startTime = System.currentTimeMillis();

        // 异步预读取与语法校验，绝不阻塞游戏主线程
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 1. 异步测试加载主配置文件（校验语法）
                File configFile = new File(plugin.getDataFolder(), "config.yml");
                YamlConfiguration testConfig = new YamlConfiguration();
                testConfig.load(configFile);
                String targetLang = testConfig.getString("language", "zh_CN");

                // 2. 异步解析多语言文件与内置回退
                LanguageManager.ReloadData langData = plugin.getLanguageManager().loadDataFromDisk(targetLang);

                // 3. 调度回主线程瞬间应用配置
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        if (!plugin.isEnabled()) {
                            return;
                        }

                        // 原子刷新主配置与语言缓存
                        plugin.getConfigManager().reload();
                        plugin.getLanguageManager().applyReloadData(langData);

                        // 调度各模块快速热重载
                        plugin.getModuleManager().reloadAll();

                        long elapsed = System.currentTimeMillis() - startTime;
                        lang.sendMessage(sender, "reload.success", "{time}", elapsed);
                        plugin.getLogger().info("核心引擎及各模块热重载完成，耗时 " + elapsed + " ms (操作者: " + sender.getName() + ")");
                    } catch (Exception e) {
                        lang.sendMessage(sender, "reload.failure");
                        plugin.getLogger().log(Level.SEVERE, "主线程应用重载配置时发生异常: ", e);
                    } finally {
                        reloading.set(false);
                    }
                });
            } catch (Exception e) {
                // 异步读取或语法解析失败时安全处理
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    reloading.set(false);
                    lang.sendMessage(sender, "reload.failure");
                    plugin.getLogger().log(Level.SEVERE, "异步重载配置读取失败 (可能是 YAML 格式错误): ", e);
                });
            }
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
