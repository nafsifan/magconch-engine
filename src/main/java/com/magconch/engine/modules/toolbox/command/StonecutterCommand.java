package com.magconch.engine.modules.toolbox.command;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.command.SubCommand;
import com.magconch.engine.modules.toolbox.config.ToolboxConfig;
import com.magconch.engine.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MenuType;

import java.util.Collections;
import java.util.List;

/**
 * 随身切石机指令处理器
 * <p>
 * 支持作为主命令子指令 (/magconch stonecutter) 以及全局独立指令 (/stonecutter) 调度，
 * 采用 Paper 1.21.4+ 现代 MenuType 虚拟构建切石机界面。
 */
public class StonecutterCommand implements SubCommand, CommandExecutor, TabCompleter {

    // ==========================================
    // 依赖
    // ==========================================

    private static final String PERMISSION = "magconch.toolbox.stonecutter";

    private final MagconchEngine plugin;
    private final ToolboxConfig config;

    public StonecutterCommand(MagconchEngine plugin, ToolboxConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ==========================================
    // SubCommand 契约实现 (/magconch stonecutter)
    // ==========================================

    @Override
    public String getName() {
        return "stonecutter";
    }

    @Override
    public String getDescription() {
        return "commands.stonecutter.description";
    }

    @Override
    public String getUsage() {
        return "stonecutter";
    }

    @Override
    public String getPermission() {
        return PERMISSION;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        return handleOpen(sender);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    // ==========================================
    // Bukkit CommandExecutor 实现 (/stonecutter 独立指令)
    // ==========================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            plugin.getLanguageManager().sendMessage(sender, "general.no_permission");
            return true;
        }
        return handleOpen(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    // ==========================================
    // 界面开启核心逻辑
    // ==========================================

    private boolean handleOpen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageManager().sendMessage(sender, "general.player_only");
            return true;
        }

        if (!config.isStonecutterEnabled()) {
            plugin.getLanguageManager().sendMessage(player, "modules.toolbox.disabled");
            return true;
        }

        String rawTitle = plugin.getLanguageManager().getMessage("modules.toolbox.stonecutter_title");
        Component title = LegacyComponentSerializer.legacySection().deserialize(TextUtil.colorize(rawTitle));

        InventoryView view = MenuType.STONECUTTER.builder()
                .checkReachable(false)
                .title(title)
                .build(player);

        player.openInventory(view);
        return true;
    }
}
