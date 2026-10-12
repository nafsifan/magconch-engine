package com.magconch.engine.modules.undo.listener;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.undo.BlockUndoModule;
import com.magconch.engine.modules.undo.model.PlacementRecord;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 防误放撤回事件监听器
 * <p>
 * 负责记录玩家放置动作与响应左键撤回请求，并在主线程完成安全回滚。
 */
public class UndoBlockListener implements Listener {

    // ==========================================
    // 依赖组件
    // ==========================================

    private final MagconchEngine plugin;
    private final BlockUndoModule module;

    public UndoBlockListener(MagconchEngine plugin, BlockUndoModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    // ==========================================
    // 事件监听：方块放置
    // ==========================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!module.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        // 仅处理生存模式玩家
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        // 检查玩家个人开关与权限
        if (!module.isUndoEnabledFor(player)) {
            return;
        }

        Block placedBlock = event.getBlockPlaced();
        Material placedType = placedBlock.getType();

        // 1. 安全防御：黑名单材质过滤
        if (module.isBlacklisted(placedType.name())) {
            return;
        }

        // 2. 方块实体保护：严禁记录 TileState（容器、告示牌等）
        if (placedBlock.getState() instanceof TileState) {
            return;
        }

        // 3. 克隆原始物品（仅数量为 1，完整保留现代 Data Components）
        ItemStack sourceItem = event.getItemInHand().clone();
        sourceItem.setAmount(1);

        // 4. 纯内存记录放置快照
        PlacementRecord record = new PlacementRecord(
                player.getUniqueId(),
                placedBlock.getLocation(),
                System.currentTimeMillis(),
                sourceItem,
                event.getBlockReplacedState().getBlockData()
        );

        module.getRecordMap().put(placedBlock.getLocation(), record);
    }

    // ==========================================
    // 事件监听：左键撤回
    // ==========================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!module.isEnabled()) {
            return;
        }

        // 仅响应主手且为物理左键点击方块
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Location loc = clickedBlock.getLocation();
        PlacementRecord record = module.getRecordMap().get(loc);
        if (record == null) {
            return;
        }

        // 校验操作者必须为同一玩家
        if (!record.getPlayerUuid().equals(player.getUniqueId())) {
            return;
        }

        // 检查是否在超时窗口内
        long now = System.currentTimeMillis();
        if (now - record.getTimestamp() > module.getTimeoutMillis()) {
            module.getRecordMap().remove(loc);
            return;
        }

        // 取消原版左键交互事件（防止敲击破坏逻辑冲突）
        event.setCancelled(true);

        // 模拟触发 BlockBreakEvent 广播，检查领地/保护插件是否允许破坏
        BlockBreakEvent breakEvent = new BlockBreakEvent(clickedBlock, player);
        plugin.getServer().getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) {
            module.getRecordMap().remove(loc);
            return;
        }

        // 移除内存记录，防止重复撤回
        module.getRecordMap().remove(loc);

        // 恢复被替换方块的原有 BlockData
        clickedBlock.setBlockData(record.getReplacedBlockData(), true);

        // 物品安全回流：优先进入背包，溢出则自然掉落
        Location centerLoc = clickedBlock.getLocation().add(0.5, 0.5, 0.5);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(record.getItem());
        if (!overflow.isEmpty()) {
            for (ItemStack leftover : overflow.values()) {
                clickedBlock.getWorld().dropItemNaturally(centerLoc, leftover);
            }
        }

        // 即时动效与音效反馈
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.8f);
        clickedBlock.getWorld().spawnParticle(Particle.CLOUD, centerLoc, 4, 0.1, 0.1, 0.1, 0.02);

        // ActionBar 动作栏提示（不污染聊天栏）
        String actionMsg = plugin.getLanguageManager().getMessage("undo.reverted");
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionMsg));
    }
}
