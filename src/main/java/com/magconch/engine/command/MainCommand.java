package com.magconch.engine.command;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.command.sub.HelpCommand;
import com.magconch.engine.command.sub.ModulesCommand;
import com.magconch.engine.command.sub.ReloadCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 引擎主命令执行与路由分发器 (/magconch, /mcore, /mce)
 * <p>
 * 统一管理子命令注册表，处理权限过滤、参数路由以及 Tab 智能补全。
 */
public class MainCommand implements CommandExecutor, TabCompleter {

    // ==========================================
    // 状态与注册表
    // ==========================================

    private final MagconchEngine plugin;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public MainCommand(MagconchEngine plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    /**
     * 注册核心预设内置子命令
     */
    private void registerDefaults() {
        registerSubCommand(new HelpCommand(plugin, this));
        registerSubCommand(new ReloadCommand(plugin));
        registerSubCommand(new ModulesCommand(plugin));
    }

    /**
     * 注册新的子命令
     *
     * @param subCommand 子命令实例
     */
    public void registerSubCommand(SubCommand subCommand) {
        if (subCommand != null) {
            subCommands.put(subCommand.getName().toLowerCase(), subCommand);
        }
    }

    // ==========================================
    // 命令执行路由
    // ==========================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 1. 无参数默认转至 help 指令
        if (args.length == 0) {
            SubCommand helpCmd = subCommands.get("help");
            if (helpCmd != null) {
                return helpCmd.execute(sender, new String[0]);
            }
            return true;
        }

        String subName = args[0].toLowerCase();
        SubCommand target = subCommands.get(subName);

        // 2. 未知子命令处理
        if (target == null) {
            plugin.getLanguageManager().sendMessage(sender, "general.unknown_command");
            return true;
        }

        // 3. 权限安全检查
        String perm = target.getPermission();
        if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) {
            plugin.getLanguageManager().sendMessage(sender, "general.no_permission");
            return true;
        }

        // 4. 裁剪参数并执行对应子命令
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return target.execute(sender, subArgs);
    }

    // ==========================================
    // Tab 键智能补全
    // ==========================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // 1. 补全一级子命令名称
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> matched = new ArrayList<>();
            for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
                SubCommand sub = entry.getValue();
                if (sub.getPermission() == null || sub.getPermission().isEmpty() || sender.hasPermission(sub.getPermission())) {
                    if (entry.getKey().startsWith(prefix)) {
                        matched.add(entry.getKey());
                    }
                }
            }
            return matched;
        }

        // 2. 委托各子命令处理二级及以上参数补全
        if (args.length > 1) {
            String subName = args[0].toLowerCase();
            SubCommand target = subCommands.get(subName);
            if (target != null) {
                String perm = target.getPermission();
                if (perm == null || perm.isEmpty() || sender.hasPermission(perm)) {
                    String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                    return target.tabComplete(sender, subArgs);
                }
            }
        }

        return Collections.emptyList();
    }

    // ==========================================
    // Getter
    // ==========================================

    public Map<String, SubCommand> getSubCommands() {
        return Collections.unmodifiableMap(subCommands);
    }
}
