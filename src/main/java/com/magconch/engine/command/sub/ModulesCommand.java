package com.magconch.engine.command.sub;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.command.SubCommand;
import com.magconch.engine.core.LanguageManager;
import com.magconch.engine.util.TextUtil;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 模块状态检查指令 (/mcore modules)
 * <p>
 * 遍历并格式化展示当前所有已挂载模块的名称、ID 及启用运行状态。
 */
public class ModulesCommand implements SubCommand {

    private final MagconchEngine plugin;

    public ModulesCommand(MagconchEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "modules";
    }

    @Override
    public String getDescription() {
        return "commands.modules.description";
    }

    @Override
    public String getUsage() {
        return "modules";
    }

    @Override
    public String getPermission() {
        return "magconch.command.modules";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        LanguageManager lang = plugin.getLanguageManager();
        Collection<EngineModule> modules = plugin.getModuleManager().getModules();

        // 1. 发送头部
        String header = lang.getRawMessage("modules.header")
                .replace("{count}", String.valueOf(modules.size()));
        sender.sendMessage(TextUtil.colorize(header));

        String itemFormat = lang.getRawMessage("modules.item_format");
        String statusEnabled = lang.getRawMessage("modules.status_enabled");
        String statusDisabled = lang.getRawMessage("modules.status_disabled");

        // 2. 循环输出各模块状态
        for (EngineModule module : modules) {
            String status = module.isEnabled() ? statusEnabled : statusDisabled;
            String line = itemFormat
                    .replace("{name}", module.getName())
                    .replace("{id}", module.getId())
                    .replace("{status}", status);
            sender.sendMessage(TextUtil.colorize(line));
        }

        // 3. 发送底部 (非空时发送)
        String footer = lang.getRawMessage("modules.footer");
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
