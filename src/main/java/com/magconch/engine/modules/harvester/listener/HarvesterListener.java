package com.magconch.engine.modules.harvester.listener;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.harvester.config.HarvesterConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.SoundGroup;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 快速右键收菜交互监听器
 * <p>
 * 监听玩家右键成熟农作物事件，在保障区域安全防偷菜的前提下，
 * 扣除 1 颗种子并重置幼苗，产物自动安全入包。
 */
public class HarvesterListener implements Listener {

    private static final String PERMISSION_USE = "magconch.harvester.use";

    private final MagconchEngine plugin;
    private final HarvesterConfig config;

    public HarvesterListener(MagconchEngine plugin, HarvesterConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ==========================================
    // 交互事件监听与调度
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

        // 2. 权限校验
        if (!player.hasPermission(PERMISSION_USE)) {
            return;
        }

        // 3. 潜行且主手持可放置方块时让路（遵循原版交互准则：Shift 优先放置方块/插火把）
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (player.isSneaking() && handItem.getType().isBlock()) {
            return;
        }

        // 4. 目标方块校验
        Block block = event.getClickedBlock();
        if (block == null || !config.isAllowedCrop(block.getType())) {
            return;
        }

        // 5. 校验作物成熟度（仅成熟作物触发快速收割）
        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }

        // 6. 领地与安全防偷菜检查（模拟触发破坏事件让外部领地/保护插件裁决）
        if (event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }

        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) {
            return;
        }

        // 拦截原版右键动作，防止手持物品误放置
        event.setCancelled(true);

        // 7. 执行收割与自动补种
        executeHarvest(player, block, ageable, blockData);
    }

    // ==========================================
    // 核心收割与补种实现
    // ==========================================

    /**
     * 执行作物收割、扣种补种与产物入包
     */
    private void executeHarvest(Player player, Block block, Ageable ageable, BlockData originalData) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        Collection<ItemStack> rawDrops = block.getDrops(tool, player);

        List<ItemStack> finalDrops = new ArrayList<>();
        Material seedType = config.getSeedType(block.getType());
        boolean seedDeducted = false;

        // 扣除 1 颗种子作为补种消耗，剩余全额作为产物
        for (ItemStack drop : rawDrops) {
            if (!seedDeducted && drop.getType() == seedType && drop.getAmount() > 0) {
                if (drop.getAmount() > 1) {
                    ItemStack reduced = drop.clone();
                    reduced.setAmount(reduced.getAmount() - 1);
                    finalDrops.add(reduced);
                }
                seedDeducted = true;
            } else {
                finalDrops.add(drop);
            }
        }

        // 重置作物为初始幼苗状态（保留朝向等元数据）
        ageable.setAge(0);
        block.setBlockData(ageable, true);

        // 产物原地自然掉落（原版收集体验，走过自动磁吸）
        Location dropLocation = block.getLocation().add(0.5, 0.2, 0.5);
        for (ItemStack item : finalDrops) {
            block.getWorld().dropItemNaturally(dropLocation, item);
        }

        // 播放原版破坏与种植音效反馈
        SoundGroup soundGroup = originalData.getSoundGroup();
        block.getWorld().playSound(dropLocation, soundGroup.getBreakSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);
        block.getWorld().playSound(dropLocation, soundGroup.getPlaceSound(), SoundCategory.BLOCKS, 0.5f, 1.2f);

        player.swingMainHand();
    }
}
