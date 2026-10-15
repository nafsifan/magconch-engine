package com.magconch.engine.modules.creaturebundle.command;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.command.SubCommand;
import com.magconch.engine.modules.creaturebundle.config.CreatureBundleConfig;
import com.magconch.engine.modules.creaturebundle.util.BundleItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 生灵收纳袋管理子命令 (/mcore bundle)
 * <p>
 * 提供向指定玩家给予空白生灵收纳袋的功能。
 */
public class BundleSubCommand implements SubCommand {

    private final MagconchEngine plugin;
    private final CreatureBundleConfig config;

    public BundleSubCommand(MagconchEngine plugin, CreatureBundleConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public String getName() {
        return "bundle";
    }

    @Override
    public String getDescription() {
        return "commands.bundle.description";
    }

    @Override
    public String getUsage() {
        return "bundle give <player> [amount]";
    }

    @Override
    public String getPermission() {
        return "magconch.command.bundle";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // 参数格式: /mcore bundle give <player> [amount]
        if (args.length < 2 || !"give".equalsIgnoreCase(args[0])) {
            plugin.getLanguageManager().sendMessage(sender, "creature-bundle.command_usage");
            return true;
        }

        String targetName = args[1];
        Player targetPlayer = Bukkit.getPlayerExact(targetName);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            plugin.getLanguageManager().sendMessage(sender, "creature-bundle.player_not_found", Map.of("player", targetName));
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {
                plugin.getLanguageManager().sendMessage(sender, "creature-bundle.invalid_amount");
                return true;
            }
        }

        // 生成空白生灵袋
        ItemStack bundleItem = BundleItemUtil.createEmptyBundle(plugin, config.getCustomModelData());
        bundleItem.setAmount(amount);

        // 安全放入背包或掉落
        Map<Integer, ItemStack> leftover = targetPlayer.getInventory().addItem(bundleItem);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), drop);
            }
        }

        plugin.getLanguageManager().sendMessage(sender, "creature-bundle.given_success", Map.of(
                "player", targetPlayer.getName(),
                "amount", String.valueOf(amount)
        ));
        plugin.getLanguageManager().sendMessage(targetPlayer, "creature-bundle.received_notice", Map.of(
                "amount", String.valueOf(amount)
        ));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if ("give".startsWith(args[0].toLowerCase())) {
                return List.of("give");
            }
            return Collections.emptyList();
        }

        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            List<String> playerNames = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    playerNames.add(player.getName());
                }
            }
            return playerNames;
        }

        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            return List.of("1", "4", "16");
        }

        return Collections.emptyList();
    }
}
