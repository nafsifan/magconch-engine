package com.magconch.engine.modules.showitem.command;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.command.SubCommand;
import com.magconch.engine.modules.showitem.ShowItemModule;
import com.magconch.engine.modules.showitem.config.ShowItemConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品展示指令处理器
 * <p>
 * 支持作为主命令子指令 (/mcore showitem) 以及全局快捷指令 (/showitem, /show) 调用。
 * 向全服玩家广播手持物品，鼠标悬浮可查看完整物品提示框（基于 Paper Adventure API）。
 */
public class ShowItemCommand implements SubCommand, CommandExecutor, TabCompleter {

    // ==========================================
    // 依赖与状态
    // ==========================================

    private final MagconchEngine plugin;
    private final ShowItemModule module;
    private final ShowItemConfig config;

    /** 玩家展示冷却时间戳记录 (UUID -> 上次展示毫秒时间戳) */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

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

        // 3. 冷却时间校验 (OP 或持有 bypass 权限可豁免)
        int cooldownSeconds = config.getCooldown();
        if (cooldownSeconds > 0 && !player.hasPermission("magconch.showitem.bypass")) {
            long now = System.currentTimeMillis();
            long lastShow = cooldowns.getOrDefault(player.getUniqueId(), 0L);
            long cooldownMillis = cooldownSeconds * 1000L;
            if (now - lastShow < cooldownMillis) {
                double remaining = (cooldownMillis - (now - lastShow)) / 1000.0;
                plugin.getLanguageManager().sendMessage(player, "show-item.cooldown",
                        "{seconds}", String.format(Locale.ROOT, "%.1f", remaining));
                return true;
            }
        }

        // 4. 手持物品检查
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            plugin.getLanguageManager().sendMessage(player, "show-item.empty_hand");
            return true;
        }

        // 5. 记录冷却时间戳
        if (cooldownSeconds > 0) {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }

        // 6. 构建广播消息并使用 Bukkit 全服广播
        Component message = buildShowMessage(player, item);
        Bukkit.broadcast(message);

        // 7. 为全服在线玩家播放清脆提示音
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
        }

        return true;
    }

    /**
     * 构建广播用的 Adventure Component 消息
     * <p>
     * 基于 Component.replaceText 动态插值，天然适应任意语序模板与多语言排版。
     */
    private Component buildShowMessage(Player player, ItemStack item) {
        // 先格式化包含前缀、基础色彩与数量的模板
        String renderedTemplate = plugin.getLanguageManager().getMessage("show-item.broadcast",
                "{amount}", String.valueOf(item.getAmount()));

        // 反序列化为初始组件树
        Component baseComponent = LegacyComponentSerializer.legacySection().deserialize(renderedTemplate);

        // 使用 replaceText 安全插值玩家名与带 HoverEvent 的物品组件
        return baseComponent
                .replaceText(b -> b.matchLiteral("{player}").replacement(player.displayName()))
                .replaceText(b -> b.matchLiteral("{item}").replacement(getItemDisplayComponent(item)));
    }

    /**
     * 构建物品展示组件（优先显示自定义名称，带悬浮提示）
     */
    private Component getItemDisplayComponent(ItemStack item) {
        Component itemName;
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            itemName = item.getItemMeta().displayName();  // 自定义名称（保留颜色）
        } else {
            itemName = Component.translatable(item.translationKey());  // 原版翻译名
        }

        return Component.text("[")
                .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                .append(itemName)
                .append(Component.text("]"))
                .hoverEvent(item.asHoverEvent());
    }
}
