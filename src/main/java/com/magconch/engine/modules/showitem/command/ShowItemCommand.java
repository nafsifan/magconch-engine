package com.magconch.engine.modules.showitem.command;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.command.SubCommand;
import com.magconch.engine.modules.showitem.ShowItemModule;
import com.magconch.engine.modules.showitem.config.ShowItemConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * 物品展示指令处理器
 * <p>
 * 支持作为主命令子指令 (/mcore showitem) 以及全局快捷指令 (/showitem, /show) 调用。
 * 向全服玩家广播手持物品，鼠标悬浮可查看完整物品提示框（基于 Paper Adventure API）。
 */
public class ShowItemCommand implements SubCommand, CommandExecutor, TabCompleter {

    // ==========================================
    // 依赖
    // ==========================================

    private final MagconchEngine plugin;
    private final ShowItemModule module;
    private final ShowItemConfig config;

    public ShowItemCommand(MagconchEngine plugin, ShowItemModule module, ShowItemConfig config) {
        this.plugin = plugin;
        this.module = module;
        this.config = config;
    }

    // ==========================================
    // SubCommand 契约实现 (/mcore showitem)
    // ==========================================

    @Override
    public String getName() {
        return "showitem";
    }

    @Override
    public String getDescription() {
        return "commands.showitem.description";
    }

    @Override
    public String getUsage() {
        return "showitem";
    }

    @Override
    public String getPermission() {
        return "magconch.showitem.use";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        return handleShow(sender);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    // ==========================================
    // Bukkit CommandExecutor 实现 (/showitem, /show 快捷指令)
    // ==========================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            plugin.getLanguageManager().sendMessage(sender, "general.no_permission");
            return true;
        }
        return handleShow(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    // ==========================================
    // 核心展示逻辑
    // ==========================================

    private boolean handleShow(CommandSender sender) {
        // 1. 仅限玩家执行
        if (!(sender instanceof Player player)) {
            plugin.getLanguageManager().sendMessage(sender, "general.player_only");
            return true;
        }

        // 2. 模块状态检查
        if (!module.isEnabled()) {
            plugin.getLanguageManager().sendMessage(player, "show-item.disabled_module");
            return true;
        }

        // 3. 手持物品检查
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            plugin.getLanguageManager().sendMessage(player, "show-item.empty_hand");
            return true;
        }

        // 4. 构建广播消息并发送
        Component message = buildShowMessage(player, item);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(message);
        }

        return true;
    }

    /**
     * 构建广播用的 Adventure Component 消息
     * <p>
     * 格式: MAGCONCH › 玩家名 向大家展示了 [物品名 x数量]
     *                                     ↑ 悬浮显示完整物品提示框
     */
    private Component buildShowMessage(Player player, ItemStack item) {
        // 语言包文本模板
        String template = plugin.getLanguageManager().getMessage("show-item.broadcast",
                "{player}", player.getDisplayName(),
                "{item}", "__ITEM_PLACEHOLDER__",
                "{amount}", String.valueOf(item.getAmount()));

        // 按占位符拆分
        String[] parts = template.split("__ITEM_PLACEHOLDER__", 2);

        // 前半段文本（玩家名等）
        Component before = deserializeLegacy(parts[0]);

        // 物品组件（带悬浮提示）
        Component itemComponent = Component.text("[")
                .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                .append(Component.translatable(item.translationKey()))
                .append(Component.text("]"))
                .hoverEvent(item.asHoverEvent());

        // 后半段文本
        Component after = parts.length > 1 ? deserializeLegacy(parts[1]) : Component.empty();

        return before.append(itemComponent).append(after);
    }

    /**
     * 将带 & 和 &#RRGGBB 的旧版颜色代码转换为 Adventure Component
     */
    private Component deserializeLegacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
