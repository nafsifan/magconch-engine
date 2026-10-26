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
 * 随身工作台指令处理器
 * <p>
 * 支持作为主命令子指令 (/magconch craft) 以及全局独立指令 (/craft) 调度，
 * 采用 Paper 1.21.4+ 现代 MenuType 虚拟构建工作台界面。
 */
public class CraftCommand implements SubCommand, CommandExecutor, TabCompleter {

    // ==========================================
    // 依赖
    // ==========================================

    private static final String PERMISSION = "magconch.toolbox.craft";

    private final MagconchEngine plugin;
    private final ToolboxConfig config;

    public CraftCommand(MagconchEngine plugin, ToolboxConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ==========================================
    // SubCommand 契约实现 (/magconch craft)
    // ==========================================

    @Override
    public String getName() {
        return "craft";
    }

    @Override
    public String getDescription() {
        return "commands.craft.description";
    }

    @Override
    public String getUsage() {
        return "craft";
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
    // Bukkit CommandExecutor 实现 (/craft 独立指令)
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

        if (!config.isCraftEnabled()) {
            plugin.getLanguageManager().sendMessage(player, "toolbox.disabled");
            return true;
        }

        String rawTitle = plugin.getLanguageManager().getMessage("toolbox.craft_title");
        Component title = LegacyComponentSerializer.legacySection().deserialize(TextUtil.colorize(rawTitle));

        InventoryView view = MenuType.CRAFTING.builder()
                .checkReachable(false)
                .title(title)
                .build(player);

        player.openInventory(view);
        return true;
    }
}
