package com.magconch.engine.modules.trash.command;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.command.SubCommand;
import com.magconch.engine.modules.trash.TrashHolder;
import com.magconch.engine.modules.trash.TrashModule;
import com.magconch.engine.modules.trash.config.TrashConfig;
import com.magconch.engine.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collections;
import java.util.List;

/**
 * 垃圾桶指令处理器
 * <p>
 * 支持作为主命令子指令 (/mcore trash) 以及全局快捷指令 (/trash, /ljt) 调用。
 */
public class TrashCommand implements SubCommand, CommandExecutor, TabCompleter {

    // ==========================================
    // 依赖
    // ==========================================

    private final MagconchEngine plugin;
    private final TrashModule module;
    private final TrashConfig config;

    public TrashCommand(MagconchEngine plugin, TrashModule module, TrashConfig config) {
        this.plugin = plugin;
        this.module = module;
        this.config = config;
    }

    // ==========================================
    // SubCommand 契约实现 (/mcore trash)
    // ==========================================

    @Override
    public String getName() {
        return "trash";
    }

    @Override
    public String getDescription() {
        return "commands.trash.description";
    }

    @Override
    public String getUsage() {
        return "trash";
    }

    @Override
    public String getPermission() {
        return "magconch.trash.use";
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
    // Bukkit CommandExecutor 实现 (/trash 快捷指令)
    // ==========================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(getPermission())) {
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

        if (!module.isEnabled()) {
            plugin.getLanguageManager().sendMessage(player, "trash.disabled_module");
            return true;
        }

        String rawTitle = plugin.getLanguageManager().getRawMessage("trash.title");
        Component title = LegacyComponentSerializer.legacySection().deserialize(TextUtil.colorize(rawTitle));

        Inventory trashInventory = Bukkit.createInventory(new TrashHolder(), config.getSize(), title);
        player.openInventory(trashInventory);

        plugin.getLanguageManager().sendMessage(player, "trash.opened");
        return true;
    }
}
