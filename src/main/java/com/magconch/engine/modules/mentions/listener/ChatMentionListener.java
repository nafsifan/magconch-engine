package com.magconch.engine.modules.mentions.listener;

import com.magconch.engine.modules.mentions.config.MentionsConfig;
import com.magconch.engine.modules.mentions.util.MentionParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 玩家聊天提及事件监听器
 * <p>
 * 监听 Paper 异步聊天管线 (AsyncChatEvent)，动态高亮 @ 提及组件并派发音效。
 */
public class ChatMentionListener implements Listener {

    private final MentionsConfig config;
    private final MentionParser parser;

    public ChatMentionListener(MentionsConfig config, MentionParser parser) {
        this.config = config;
        this.parser = parser;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        Player sender = event.getPlayer();
        Component originalMessage = event.message();

        MentionParser.ParseResult result = parser.parse(originalMessage, sender);
        if (result.hasMentions()) {
            event.message(result.formattedMessage());
            parser.dispatchNotifications(result, sender);
        }
    }
}
