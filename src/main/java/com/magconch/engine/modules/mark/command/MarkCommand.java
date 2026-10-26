package com.magconch.engine.modules.mark.command;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.command.SubCommand;
import com.magconch.engine.modules.mark.MarkModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * 标点子命令处理器 (/magconch mark, /magc mark)
 */
public class MarkCommand implements SubCommand {

    private final MagconchEngine plugin;
    private final MarkModule module;

    public MarkCommand(MagconchEngine plugin, MarkModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public String getName() {
        return "mark";
    }

    @Override
    public String getDescription() {
        return "commands.mark.description";
    }

    @Override
    public String getUsage() {
        return "mark";
    }

    @Override
    public String getPermission() {
        return "magconch.use.mark";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageManager().sendMessage(sender, "general.player_only");
            return true;
        }

        boolean enabled = module.toggleMark(player.getUniqueId());
        if (enabled) {
            plugin.getLanguageManager().sendMessage(player, "mark.enabled");
        } else {
            plugin.getLanguageManager().sendMessage(player, "mark.disabled");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
