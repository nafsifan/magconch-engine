package com.magconch.engine.modules.undo.listener;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.undo.BlockUndoModule;
import com.magconch.engine.modules.undo.model.PlacementRecord;
import com.magconch.engine.util.ItemUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.block.data.type.Wall;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;


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

        // 任何新的放置动作均先作废该玩家上一次的撤回记录（确保仅可撤回最后 1 个方块）
        module.getRecordMap().remove(player.getUniqueId());

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

        // 1. 排除多格复合放置、工具/非本体交互、零硬度秒破物及对既有实心方块的改造/合并
        if (event instanceof BlockMultiPlaceEvent
                || event.getItemInHand().getType() != placedType
                || !placedType.isSolid()
                || placedType.getHardness() <= 0
                || event.getBlockReplacedState().getType().isSolid()) {
            return;
        }

        // 2. 仅允许标准正方体与常规建筑构件（剔除龙蛋、门、床、铁砧、耕地等异形块）
        if (!isBuildingBlock(placedBlock, placedBlock.getBlockData())) {
            return;
        }

        // 3. 方块实体保护：严禁记录 TileState（容器、告示牌等）
        if (placedBlock.getState() instanceof TileState) {
            return;
        }

        // 4. 克隆原始物品（仅数量为 1，完整保留现代 Data Components）
        ItemStack sourceItem = event.getItemInHand().clone();
        sourceItem.setAmount(1);

        // 5. 纯内存记录玩家最后 1 次放置快照
        PlacementRecord record = new PlacementRecord(
                placedBlock.getLocation(),
                System.currentTimeMillis(),
                sourceItem
        );

        module.getRecordMap().put(player.getUniqueId(), record);
    }

    /**
     * 判定是否为允许撤回的建筑方块（1×1×1 标准正方体或楼梯/半砖/栅栏/墙壁等构件）
     */
    private boolean isBuildingBlock(Block block, BlockData data) {
        if (block.getType().isOccluding()
                || data instanceof Stairs
                || data instanceof Slab
                || data instanceof Fence
                || data instanceof Wall
                || data instanceof TrapDoor
                || data instanceof Gate) {
            return true;
        }
        BoundingBox box = block.getBoundingBox();
        return box.getWidthX() == 1.0 && box.getHeight() == 1.0 && box.getWidthZ() == 1.0;
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
        PlacementRecord record = module.getRecordMap().get(player.getUniqueId());
        if (record == null || !record.getLocation().equals(loc)) {
            return;
        }

        // 校验个人开关与权限有效
        if (!module.isUndoEnabledFor(player)) {
            return;
        }

        // 校验手持物一致性：若已切换为工具或其他物品，判定为主动采掘行为，直接放行
        Material handType = player.getInventory().getItemInMainHand().getType();
        if (handType != record.getItem().getType() && !handType.isAir()) {
            return;
        }

        // 检查是否在超时窗口内，且当前方块材质仍与放置物一致
        long now = System.currentTimeMillis();
        if (now - record.getTimestamp() > module.getTimeoutMillis()
                || clickedBlock.getType() != record.getItem().getType()) {
            module.getRecordMap().remove(player.getUniqueId());
            return;
        }

        // 取消原版左键交互事件（防止敲击破坏逻辑冲突）
        event.setCancelled(true);

        // 先移除内存记录，防止递归或重复撤回
        module.getRecordMap().remove(player.getUniqueId());

        // 模拟触发 BlockBreakEvent 广播，检查领地/保护插件是否允许破坏
        BlockBreakEvent breakEvent = new BlockBreakEvent(clickedBlock, player);
        plugin.getServer().getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) {
            return;
        }

        // 将撤回的方块直接清除为空气
        clickedBlock.setType(Material.AIR, true);

        // 物品安全回流：优先进入背包，溢出则自然掉落
        Location centerLoc = clickedBlock.getLocation().add(0.5, 0.5, 0.5);
        ItemUtil.giveItemSafely(player, centerLoc, record.getItem());

        // 即时动效与音效反馈
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.8f);
        clickedBlock.getWorld().spawnParticle(Particle.CLOUD, centerLoc, 4, 0.1, 0.1, 0.1, 0.02);

        // ActionBar 动作栏提示（不污染聊天栏）
        plugin.getLanguageManager().sendActionBar(player, "undo.reverted");
    }

    // ==========================================
    // 事件监听：方块破坏清理
    // ==========================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        Location brokenLoc = event.getBlock().getLocation();
        module.getRecordMap().values().removeIf(r -> r.getLocation().equals(brokenLoc));
    }
}
