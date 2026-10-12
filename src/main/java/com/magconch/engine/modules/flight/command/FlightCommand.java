package com.magconch.engine.modules.flight.command;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.command.SubCommand;
import com.magconch.engine.modules.flight.FlightModule;
import com.magconch.engine.modules.flight.config.FlightConfig;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * 飞行指令路由处理器
 * <p>
 * 同时作为子命令挂载至 /magconch fly 以及独立全局别名 /fly，控制玩家自身饥饿飞行模式开关。
 */
public class FlightCommand implements SubCommand, CommandExecutor, TabCompleter {

    // ==========================================
    // 依赖
    // ==========================================

    private final MagconchEngine plugin;
    private final FlightModule module;
    private final FlightConfig config;

    public FlightCommand(MagconchEngine plugin, FlightModule module, FlightConfig config) {
        this.plugin = plugin;
        this.module = module;
        this.config = config;
    }

    // ==========================================
    // SubCommand 契约实现
    // ==========================================

    @Override
    public String getName() {
        return "fly";
    }

    @Override
    public String getDescription() {
        return "commands.fly.description";
    }

    @Override
    public String getUsage() {
        return "fly";
    }

    @Override
    public String getPermission() {
        return "magconch.flight.use";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        return handleToggle(sender);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    // ==========================================
    // Bukkit CommandExecutor 实现 (/fly 快捷指令)
    // ==========================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 权限校验
        if (!sender.hasPermission(getPermission())) {
            plugin.getLanguageManager().sendMessage(sender, "general.no_permission");
            return true;
        }
        return handleToggle(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    // ==========================================
    // 核心切换逻辑
    // ==========================================

    private boolean handleToggle(CommandSender sender) {
        // 仅限玩家执行
        if (!(sender instanceof Player player)) {
            plugin.getLanguageManager().sendMessage(sender, "general.player_only");
            return true;
        }

        // 模块运行状态安全检查
        if (!module.isEnabled()) {
            plugin.getLanguageManager().sendMessage(player, "flight.disabled_module");
            return true;
        }

        // 切换飞行能力开关
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            plugin.getLanguageManager().sendMessage(player, "flight.disabled");
        } else {
            player.setAllowFlight(true);
            FlightModule.enableFlyingFallDamage(player);
            plugin.getLanguageManager().sendMessage(player, "flight.enabled");

            // 饱食度过低预警（非创造/旁观模式）
            GameMode mode = player.getGameMode();
            if (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR) {
                if (player.getFoodLevel() <= config.getMinFoodLevel()) {
                    plugin.getLanguageManager().sendMessage(player, "flight.too_hungry");
                }
            }
        }
        return true;
    }
}
