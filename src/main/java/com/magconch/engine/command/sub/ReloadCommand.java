package com.magconch.engine.command.sub;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.command.SubCommand;
import com.magconch.engine.core.LanguageManager;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * 引擎重载指令 (/mcore reload)
 * <p>
 * 触发核心配置、多语言词库以及所有已注册模块的热重载逻辑。
 */
public class ReloadCommand implements SubCommand {

    private final MagconchEngine plugin;

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
        lang.sendMessage(sender, "reload.start");

        long startTime = System.currentTimeMillis();

        try {
            // 1. 重载配置文件
            plugin.getConfigManager().reload();

            // 2. 重载多语言系统
            plugin.getLanguageManager().reload();

            // 3. 通知所有模块热重载
            plugin.getModuleManager().reloadAll();

            long elapsed = System.currentTimeMillis() - startTime;
            lang.sendMessage(sender, "reload.success", "{time}", elapsed);
            plugin.getLogger().info("核心引擎及各模块热重载完成，耗时 " + elapsed + " ms (操作者: " + sender.getName() + ")");
        } catch (Exception e) {
            lang.sendMessage(sender, "reload.failure");
            plugin.getLogger().log(Level.SEVERE, "执行 /mcore reload 时发生异常: ", e);
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
