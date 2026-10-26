package com.magconch.engine.modules.signcolor.listener;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.signcolor.config.SignColorConfig;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告示牌彩色文字解析与二次编辑无损还原监听器
 * <p>
 * 解决原版客户端重新编辑告示牌时剥离颜色代码的问题。
 * 在提交时将带颜色代码的原始字符串持久化到方块容器中；
 * 在唤起编辑时临时回填源码并延迟发包，确保客户端编辑框能完整显示颜色代码。
 */
public class SignColorListener implements Listener {

    private static final String PERMISSION_USE = "magconch.signcolor.use";

    private static final LegacyComponentSerializer COLOR_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private final MagconchEngine plugin;
    private final SignColorConfig config;

    private final NamespacedKey frontKey;
    private final NamespacedKey backKey;

    // 记录处于延迟打开流程中的玩家，防止插件主动调用 openSign 时陷入递归拦截
    private final Set<UUID> pendingEditorOpens = ConcurrentHashMap.newKeySet();

    public SignColorListener(MagconchEngine plugin, SignColorConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.frontKey = new NamespacedKey(plugin, "raw_sign_front");
        this.backKey = new NamespacedKey(plugin, "raw_sign_back");
    }

    // ==========================================
    // 告示牌打开编辑：无损还原颜色代码
    // ==========================================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerOpenSign(PlayerOpenSignEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        // 仅响应右键已放置告示牌的再编辑，新放置告示牌无需还原
        if (event.getCause() != PlayerOpenSignEvent.Cause.INTERACT) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission(PERMISSION_USE)) {
            return;
        }

        // 放行本插件延迟触发的二次打开，消费标记后直接由原版开启编辑界面
        if (pendingEditorOpens.remove(player.getUniqueId())) {
            return;
        }

        Sign sign = event.getSign();
        Side side = event.getSide();
        SignSide signSide = sign.getSide(side);

        // 优先从方块持久化容器获取原始输入文本；若历史方块未记录则从组件反序列化
        PersistentDataContainer pdc = sign.getPersistentDataContainer();
        String stored = pdc.get(getSideKey(side), PersistentDataType.STRING);
        String[] rawLines = (stored != null) ? stored.split("\n", -1) : null;

        if (rawLines == null) {
            rawLines = new String[4];
            boolean containsColorCode = false;
            for (int i = 0; i < 4; i++) {
                Component current = signSide.line(i);
                String serialized = (current != null) ? COLOR_SERIALIZER.serialize(current) : "";
                rawLines[i] = serialized;
                if (serialized.contains("&")) {
                    containsColorCode = true;
                }
            }

            // 无任何颜色代码的普通告示牌直接放行，完全遵循原版流程
            if (!containsColorCode) {
                return;
            }
        }

        // 拦截原版即刻弹窗，先将带 & 源码写入方块并强制同步客户端
        event.setCancelled(true);

        for (int i = 0; i < 4; i++) {
            String line = (i < rawLines.length && rawLines[i] != null) ? rawLines[i] : "";
            signSide.line(i, Component.text(line));
        }
        sign.update(true, false);

        UUID playerId = player.getUniqueId();
        pendingEditorOpens.add(playerId);

        // 延迟 2 tick 唤起编辑界面，确保客户端已接收并应用包含源码的方块数据包
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (player.isOnline() && sign.isPlaced() && player.getWorld().equals(sign.getWorld())) {
                    player.openSign(sign, side);
                }
            } finally {
                pendingEditorOpens.remove(playerId);
            }
        }, 2L);
    }

    // ==========================================
    // 告示牌文本提交与颜色解析
    // ==========================================

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission(PERMISSION_USE)) {
            return;
        }

        String[] rawLines = new String[4];
        boolean hasColor = false;

        // 解析 & 颜色代码与十六进制色为原生文本组件
        for (int i = 0; i < 4; i++) {
            String line = event.getLine(i);
            if (line == null) {
                rawLines[i] = "";
                continue;
            }

            rawLines[i] = line;
            if (line.contains("&")) {
                hasColor = true;
                event.line(i, COLOR_SERIALIZER.deserialize(line));
            }
        }

        // 延迟至主线程下一任务将原始输入存入方块持久化容器，避开事件处理中方块实体的写冲突
        Block block = event.getBlock();
        NamespacedKey rawTextKey = getSideKey(event.getSide());
        boolean shouldSave = hasColor;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getState() instanceof Sign sign) {
                PersistentDataContainer pdc = sign.getPersistentDataContainer();
                if (shouldSave) {
                    pdc.set(rawTextKey, PersistentDataType.STRING, String.join("\n", rawLines));
                } else {
                    pdc.remove(rawTextKey);
                }
                sign.update(true, false);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingEditorOpens.remove(event.getPlayer().getUniqueId());
    }

    public void clear() {
        pendingEditorOpens.clear();
    }

    private NamespacedKey getSideKey(Side side) {
        return side == Side.BACK ? backKey : frontKey;
    }
}
