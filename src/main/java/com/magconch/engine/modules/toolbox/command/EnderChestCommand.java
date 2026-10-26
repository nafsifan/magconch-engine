package com.magconch.engine.modules.toolbox.command;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.command.SubCommand;
import com.magconch.engine.modules.toolbox.config.ToolboxConfig;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 随身末影箱指令处理器
 * <p>
 * 支持作为主命令子指令 (/magconch ec [玩家]) 以及全局独立指令 (/ec [玩家]) 调度，
 * 直接打开原生末影箱容器，完美兼容核心端扩展与 AxShulkers 等潜影盒插件。
 */
public class EnderChestCommand implements SubCommand, CommandExecutor, TabCompleter {

    // ==========================================
    // 常量与权限节点
    // ==========================================

    private static final String PERMISSION_USE = "magconch.toolbox.ec";
    private static final String PERMISSION_OTHER = "magconch.toolbox.ec.other";

    private final MagconchEngine plugin;
    private final ToolboxConfig config;

    public EnderChestCommand(MagconchEngine plugin, ToolboxConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ==========================================
    // SubCommand 契约实现 (/magconch ec)
    // ==========================================

    @Override
    public String getName() {
        return "ec";
    }

    @Override
    public String getDescription() {
        return "commands.ec.description";
    }

    @Override
    public String getUsage() {
        return "ec [player]";
    }

    @Override
    public String getPermission() {
        return PERMISSION_USE;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        return handleCommand(sender, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return handleTab(sender, args);
    }

    // ==========================================
    // Bukkit CommandExecutor 实现 (/ec 独立指令)
    // ==========================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return handleCommand(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return handleTab(sender, args);
    }

    // ==========================================
    // 核心业务处理
    // ==========================================

    private boolean handleCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageManager().sendMessage(sender, "general.player_only");
            return true;
        }

        if (!config.isEnderchestEnabled()) {
            plugin.getLanguageManager().sendMessage(player, "toolbox.disabled");
            return true;
        }

        // 1. 无参数：打开自身末影箱
        if (args.length == 0) {
            if (!player.hasPermission(PERMISSION_USE)) {
                plugin.getLanguageManager().sendMessage(player, "general.no_permission");
                return true;
            }

            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
            player.openInventory(player.getEnderChest());
            return true;
        }

        // 2. 带参数：管理员查看在线玩家末影箱
        if (!player.hasPermission(PERMISSION_OTHER)) {
            plugin.getLanguageManager().sendMessage(player, "general.no_permission");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            plugin.getLanguageManager().sendMessage(player, "general.player_not_found", "{player}", targetName);
            return true;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
        player.openInventory(target.getEnderChest());
        return true;
    }

    private List<String> handleTab(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission(PERMISSION_OTHER)) {
            String current = args[0].toLowerCase();
            List<String> result = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(current)) {
                    result.add(player.getName());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
