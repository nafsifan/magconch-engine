package com.magconch.engine.modules.mentions.listener;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.magconch.engine.modules.mentions.config.MentionsConfig;
import com.magconch.engine.modules.mentions.util.MentionParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天提及自动补全与生命周期同步监听器
 * <p>
 * 结合 Paper CustomChatCompletions 客户端本地补全与 AsyncTabCompleteEvent 服务端兜底，
 * 实现玩家打 @ 键入名字时的丝滑补全体验。
 */
public class MentionTabCompleteListener implements Listener {

    private final MentionsConfig config;
    private final MentionParser parser;

    public MentionTabCompleteListener(MentionsConfig config, MentionParser parser) {
        this.config = config;
        this.parser = parser;
    }

    // ==========================================
    // 玩家进退服生命周期同步
    // ==========================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        Player joinedPlayer = event.getPlayer();

        // 1. 为新进服玩家构建全量补全列表 (剔除自身)
        List<String> completionsForJoined = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(joinedPlayer.getUniqueId())) {
                completionsForJoined.add("@" + online.getName());
            }
        }
        if (joinedPlayer.hasPermission("magconch.mentions.everyone")) {
            completionsForJoined.add("@everyone");
            completionsForJoined.add("@all");
        }
        joinedPlayer.setCustomChatCompletions(completionsForJoined);

        // 2. 将新玩家名字追加至其他在线玩家的客户端补全缓存
        List<String> singleNewEntry = List.of("@" + joinedPlayer.getName());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(joinedPlayer.getUniqueId())) {
                online.addCustomChatCompletions(singleNewEntry);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quittingPlayer = event.getPlayer();
        parser.clearCooldown(quittingPlayer.getUniqueId());

        if (!config.isEnabled()) {
            return;
        }

        // 从其他在线玩家的补全词典中剔除离线玩家
        List<String> removeEntry = List.of("@" + quittingPlayer.getName());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(quittingPlayer.getUniqueId())) {
                online.removeCustomChatCompletions(removeEntry);
            }
        }
    }

    // ==========================================
    // 聊天框输入 Tab 补全兜底拦截
    // ==========================================

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (!config.isEnabled() || event.isCommand()) {
            return;
        }

        String buffer = event.getBuffer();
        if (buffer == null || buffer.isEmpty()) {
            return;
        }

        // 截取光标所在词 (以最后一个空白字符后的片段为准)
        int lastSpace = buffer.lastIndexOf(' ');
        String token = (lastSpace >= 0) ? buffer.substring(lastSpace + 1) : buffer;

        if (!token.startsWith("@")) {
            return;
        }

        String searchPrefix = token.substring(1).toLowerCase();
        List<String> suggestions = new ArrayList<>();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (event.getSender() instanceof Player p && p.getUniqueId().equals(online.getUniqueId())) {
                continue;
            }
            if (online.getName().toLowerCase().startsWith(searchPrefix)) {
                suggestions.add("@" + online.getName());
            }
        }

        if (event.getSender().hasPermission("magconch.mentions.everyone")) {
            if ("everyone".startsWith(searchPrefix)) {
                suggestions.add("@everyone");
            }
            if ("all".startsWith(searchPrefix)) {
                suggestions.add("@all");
            }
        }

        if (!suggestions.isEmpty()) {
            Collections.sort(suggestions);
            event.setCompletions(suggestions);
            event.setHandled(true);
        }
    }

    // ==========================================
    // 全量补全同步
    // ==========================================

    /**
     * 重载或模块启用时刷新全服在线玩家的补全词典
     */
    public void syncAll() {
        if (!config.isEnabled()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setCustomChatCompletions(Collections.emptyList());
            }
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            List<String> playerSpecificList = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    playerSpecificList.add("@" + online.getName());
                }
            }
            if (player.hasPermission("magconch.mentions.everyone")) {
                playerSpecificList.add("@everyone");
                playerSpecificList.add("@all");
            }
            player.setCustomChatCompletions(playerSpecificList);
        }
    }
}
