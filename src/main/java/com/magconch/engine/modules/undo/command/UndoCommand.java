package com.magconch.engine.modules.undo.command;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.command.SubCommand;
import com.magconch.engine.modules.undo.BlockUndoModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 防误放撤回功能子命令 (/mcore undo [toggle])
 * <p>
 * 支持玩家切换自身的防误放撤回功能开关状态。
 */
public class UndoCommand implements SubCommand {

    private final MagconchEngine plugin;
    private final BlockUndoModule module;

    public UndoCommand(MagconchEngine plugin, BlockUndoModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public String getName() {
        return "undo";
    }

    @Override
    public String getDescription() {
        return "commands.undo.description";
    }

    @Override
    public String getUsage() {
        return "undo [toggle]";
    }

    @Override
    public String getPermission() {
        return "magconch.command.use";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageManager().sendMessage(sender, "general.player_only");
            return true;
        }

        if (!module.isEnabled()) {
            plugin.getLanguageManager().sendMessage(player, "undo.disabled_module");
            return true;
        }

        if (!player.hasPermission("magconch.use.undo")) {
            plugin.getLanguageManager().sendMessage(player, "general.no_permission");
            return true;
        }

        // 无参或 toggle 均触发切换
        boolean currentActive = module.isUndoEnabledFor(player);
        boolean nextActive = !currentActive;
        module.setUndoEnabledFor(player, nextActive);

        if (nextActive) {
            plugin.getLanguageManager().sendMessage(player, "undo.toggle_on");
        } else {
            plugin.getLanguageManager().sendMessage(player, "undo.toggle_off");
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if ("toggle".startsWith(args[0].toLowerCase())) {
                list.add("toggle");
            }
            return list;
        }
        return Collections.emptyList();
    }
}
