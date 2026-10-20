package com.magconch.engine.modules.mentions.listener;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.mentions.config.MentionsConfig;
import com.magconch.engine.modules.mentions.util.MentionParser;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * 控制台 say 指令提及监听器
 * <p>
 * 捕获后台 say @xxx 指令，转换为带高亮色彩的原版 [Server] 广播并触发目标提醒。
 */
public class ConsoleSayListener implements Listener {

    private final MagconchEngine plugin;
    private final MentionsConfig config;
    private final MentionParser parser;

    public ConsoleSayListener(MagconchEngine plugin, MentionsConfig config, MentionParser parser) {
        this.plugin = plugin;
        this.config = config;
        this.parser = parser;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        String command = event.getCommand();
        if (!command.regionMatches(true, 0, "say ", 0, 4)) {
            return;
        }

        String messageContent = command.substring(4).trim();
        if (messageContent.isEmpty()) {
            return;
        }

        Component rawComponent = Component.text(messageContent);
        MentionParser.ParseResult result = parser.parse(rawComponent, event.getSender());

        // 仅当包含有效艾特时拦截原版广播，自行分发原版风格高亮消息
        if (result.hasMentions()) {
            event.setCancelled(true);

            Component prefix = Component.text("[Server] ");
            Component fullBroadcast = prefix.append(result.formattedMessage());

            // 向全服与控制台进行全量广播
            plugin.getServer().sendMessage(fullBroadcast);

            // 派发音效反馈
            parser.dispatchNotifications(result, event.getSender());
        }
    }
}
