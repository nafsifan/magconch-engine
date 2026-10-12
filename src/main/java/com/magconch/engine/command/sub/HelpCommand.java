package com.magconch.engine.command.sub;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.command.MainCommand;
import com.magconch.engine.command.SubCommand;
import com.magconch.engine.core.LanguageManager;
import com.magconch.engine.util.TextUtil;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * 帮助指令实现 (/mcore help)
 * <p>
 * 根据玩家权限动态展示具备权限执行的子命令清单与使用说明。
 */
public class HelpCommand implements SubCommand {

    private final MagconchEngine plugin;
    private final MainCommand mainCommand;

    public HelpCommand(MagconchEngine plugin, MainCommand mainCommand) {
        this.plugin = plugin;
        this.mainCommand = mainCommand;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "commands.help.description";
    }

    @Override
    public String getUsage() {
        return "help";
    }

    @Override
    public String getPermission() {
        return "magconch.command.use";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        LanguageManager lang = plugin.getLanguageManager();

        // 1. 发送帮助头部
        String header = lang.getRawMessage("help.header");
        sender.sendMessage(TextUtil.colorize(header));

        String itemFormat = lang.getRawMessage("help.item_format");

        // 2. 遍历所有注册子命令，仅展示该发送者具备执行权限的命令项
        for (SubCommand sub : mainCommand.getSubCommands().values()) {
            if (sub.getPermission() != null && !sub.getPermission().isEmpty() && !sender.hasPermission(sub.getPermission())) {
                continue;
            }

            String desc = lang.getRawMessage(sub.getDescription());
            String line = itemFormat
                    .replace("{command}", "magc")
                    .replace("{subcommand}", sub.getUsage())
                    .replace("{description}", desc);

            sender.sendMessage(TextUtil.colorize(line));
        }

        // 3. 发送帮助底部 (非空时发送)
        String footer = lang.getRawMessage("help.footer");
        if (footer != null && !footer.trim().isEmpty()) {
            sender.sendMessage(TextUtil.colorize(footer));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
