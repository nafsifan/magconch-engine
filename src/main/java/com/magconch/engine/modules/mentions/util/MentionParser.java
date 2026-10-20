package com.magconch.engine.modules.mentions.util;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.mentions.config.MentionsConfig;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 聊天提及核心解析与效果分发工具
 * <p>
 * 负责扫描文本中的 @玩家 与 @everyone 语法，执行权限与冷却校验、
 * 渲染原版经典钻石青蓝高亮悬浮组件并向目标派发拾取提示音效。
 */
public class MentionParser {

    // ==========================================
    // 常量与匹配模式
    // ==========================================

    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<=^|\\s)@([a-zA-Z0-9_]{1,16})");

    /**
     * 原版经典钻石青蓝色 (#55ffff)
     */
    private static final TextColor MENTION_COLOR = NamedTextColor.AQUA;

    /**
     * 默认防骚扰触发冷却 (秒)
     */
    private static final int COOLDOWN_SECONDS = 3;

    /**
     * 统一经验球清脆拾取提示音
     */
    private static final Sound NOTIFICATION_SOUND = Sound.sound(
            Key.key("entity.experience_orb.pickup"),
            Sound.Source.PLAYER,
            1.0f,
            1.2f
    );

    private final MagconchEngine plugin;
    private final MentionsConfig config;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public MentionParser(MagconchEngine plugin, MentionsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ==========================================
    // 解析结果载荷
    // ==========================================

    public record ParseResult(
            Component formattedMessage,
            Set<Player> targets,
            boolean hasEveryone,
            boolean throttled
    ) {
        public boolean hasMentions() {
            return (hasEveryone || !targets.isEmpty()) && !throttled;
        }
    }

    // ==========================================
    // 核心解析逻辑
    // ==========================================

    /**
     * 解析 Component 消息中的提及语法并完成高亮与目标提取
     *
     * @param original 原始消息组件
     * @param sender   消息发送者 (玩家或控制台)
     * @return 解析完成的结构化结果
     */
    public ParseResult parse(Component original, CommandSender sender) {
        if (!config.isEnabled() || original == null) {
            return new ParseResult(original, Set.of(), false, false);
        }

        // 1. 权限初检：玩家无基础使用权限则原样直通
        if (sender instanceof Player player && !player.hasPermission("magconch.mentions.use")) {
            return new ParseResult(original, Set.of(), false, false);
        }

        Set<Player> targets = new HashSet<>();
        AtomicBoolean hasEveryone = new AtomicBoolean(false);

        boolean allowEveryone = (sender instanceof ConsoleCommandSender)
                || sender.hasPermission("magconch.mentions.everyone");

        // 2. 扫描并替换 Component 文本内容
        Component modified = original.replaceText(builder -> {
            builder.match(MENTION_PATTERN).replacement((matchResult, textBuilder) -> {
                String fullMatch = matchResult.group();
                String targetName = matchResult.group(1);

                // 2.1 检查全员广播提及 (@everyone / @all)
                if (targetName.equalsIgnoreCase("everyone") || targetName.equalsIgnoreCase("all")) {
                    if (allowEveryone) {
                        hasEveryone.set(true);
                        String hoverText = plugin.getLanguageManager().getMessage("mentions.hover_everyone");
                        Component hoverComponent = LegacyComponentSerializer.legacySection().deserialize(hoverText);
                        return Component.text(fullMatch)
                                .color(MENTION_COLOR)
                                .hoverEvent(HoverEvent.showText(hoverComponent));
                    }
                    return textBuilder.content(fullMatch);
                }

                // 2.2 检查单个在线玩家提及
                Player target = findOnlinePlayer(targetName);
                if (target != null) {
                    targets.add(target);
                    String hoverTemplate = plugin.getLanguageManager().getMessage("mentions.hover_player", "{player}", target.getName());
                    Component hoverComponent = LegacyComponentSerializer.legacySection().deserialize(hoverTemplate);
                    return Component.text("@" + target.getName())
                            .color(MENTION_COLOR)
                            .hoverEvent(HoverEvent.showText(hoverComponent))
                            .clickEvent(ClickEvent.suggestCommand("/msg " + target.getName() + " "));
                }

                return textBuilder.content(fullMatch);
            });
        });

        // 3. 若未解析到任何有效提及，直接返回原内容
        if (!hasEveryone.get() && targets.isEmpty()) {
            return new ParseResult(original, Set.of(), false, false);
        }

        // 4. 防刷冷却检查 (仅针对非特权玩家)
        if (sender instanceof Player player && !player.hasPermission("magconch.mentions.bypass")) {
            long now = System.currentTimeMillis();
            long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
            long elapsed = now - last;
            long cooldownMs = COOLDOWN_SECONDS * 1000L;

            if (elapsed < cooldownMs) {
                long remaining = (cooldownMs - elapsed + 999) / 1000;
                plugin.getLanguageManager().sendMessage(player, "mentions.cooldown", "{seconds}", remaining);
                // 冷却期间退化为普通消息发送，不给予声光提示
                return new ParseResult(original, Set.of(), false, true);
            }
            cooldowns.put(player.getUniqueId(), now);
        }

        return new ParseResult(modified, targets, hasEveryone.get(), false);
    }

    // ==========================================
    // 视听反馈调度
    // ==========================================

    /**
     * 为解析出的提及目标播放对应音效提示
     *
     * @param result 解析结果载荷
     * @param sender 消息发送者 (用于规避发送者自我提示)
     */
    public void dispatchNotifications(ParseResult result, CommandSender sender) {
        if (!result.hasMentions()) {
            return;
        }

        // 1. 全员广播推送
        if (result.hasEveryone()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player p && p.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                player.playSound(NOTIFICATION_SOUND);
            }
            return;
        }

        // 2. 单独玩家推送
        for (Player target : result.targets()) {
            if (sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }
            target.playSound(NOTIFICATION_SOUND);
        }
    }

    // ==========================================
    // 内部辅助方法
    // ==========================================

    /**
     * 大小写不敏感查找在线玩家
     */
    private Player findOnlinePlayer(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            return player;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    /**
     * 清理离线玩家冷却记录，防止内存堆积
     */
    public void clearCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
