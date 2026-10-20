package com.magconch.engine.modules.signcolor.listener;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.signcolor.config.SignColorConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 告示牌彩色文字解析与无损还原监听器
 * <p>
 * 支持在告示牌上解析 & 颜色代码与十六进制色，并在二次编辑时无损还原源码。
 */
public class SignColorListener implements Listener {

    private static final String PERMISSION_USE = "magconch.signcolor.use";

    private static final LegacyComponentSerializer COLOR_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private final MagconchEngine plugin;
    private final SignColorConfig config;

    public SignColorListener(MagconchEngine plugin, SignColorConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ==========================================
    // 右键唤起编辑时无损还原颜色代码
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 1. 基础条件校验：仅响应主手右键方块
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!config.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // 2. 权限校验：拥有颜色权限才介入颜色无损还原，无权限完全走原版流程
        if (!player.hasPermission(PERMISSION_USE)) {
            return;
        }

        // 3. 目标方块校验
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        BlockState state = block.getState();
        if (!(state instanceof Sign sign)) {
            return;
        }

        // 4. 已打蜡封死的告示牌遵循原版规则禁止编辑
        if (sign.isWaxed()) {
            return;
        }

        // 5. 原版交互让路处理
        ItemStack handItem = player.getInventory().getItemInMainHand();
        Material handType = handItem.getType();

        // 潜行手持方块优先放置方块/插火把
        if (player.isSneaking() && handType.isBlock()) {
            return;
        }

        // 手持染料、蜜脾、发光墨囊等原版道具时让路
        if (isSignModifierItem(handType)) {
            return;
        }

        // 6. 领地与安全防盗改检查（模拟触发破坏事件让外部领地/保护插件裁决）
        if (event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }

        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) {
            return;
        }

        // 拦截原版右键动作，防止触发其他行为
        event.setCancelled(true);

        // 7. 判定交互面（正面/反面）
        Side side = sign.getInteractableSideFor(player);
        if (side == null) {
            side = Side.FRONT;
        }

        // 8. 还原现有文字中的颜色代码为 & 格式，确保玩家在编辑框中无损修改错别字
        SignSide signSide = sign.getSide(side);
        for (int i = 0; i < 4; i++) {
            Component current = signSide.line(i);
            if (current != null) {
                String serialized = COLOR_SERIALIZER.serialize(current);
                signSide.line(i, Component.text(serialized));
            }
        }
        sign.update(false, false);

        // 9. 打开对应朝向的原版告示牌编辑界面
        player.openSign(sign, side);
    }

    private boolean isSignModifierItem(Material type) {
        if (type == Material.HONEYCOMB || type == Material.GLOW_INK_SAC || type == Material.INK_SAC) {
            return true;
        }
        return type.name().endsWith("_DYE");
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

        // 解析 & 颜色代码与十六进制色为原生文本组件
        for (int i = 0; i < 4; i++) {
            String line = event.getLine(i);
            if (line != null && line.contains("&")) {
                Component colored = COLOR_SERIALIZER.deserialize(line);
                event.line(i, colored);
            }
        }
    }
}
