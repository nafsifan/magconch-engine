package com.magconch.engine.modules.compresseditem.listener;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.compresseditem.util.CompressedItemUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * 压缩方块全场景安全防护与工匠容器隔离监听器
 * <p>
 * 严禁压缩方块误放落地、误入熔炉烧炼/当燃料、误入切石机等加工设备，
 * 以及被红石合成器 (Crafter) 误合或堆肥桶误吞。
 */
public class CompressedProtectListener implements Listener {

    // ==========================================
    // 常量与被隔离容器集合
    // ==========================================

    private static final Set<InventoryType> BLOCKED_CONTAINERS = EnumSet.of(
            InventoryType.STONECUTTER,
            InventoryType.FURNACE,
            InventoryType.BLAST_FURNACE,
            InventoryType.SMOKER
    );

    private final MagconchEngine plugin;

    public CompressedProtectListener(MagconchEngine plugin) {
        this.plugin = plugin;
    }

    // ==========================================
    // 防误放方块拦截 (静默取消)
    // ==========================================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (CompressedItemUtil.isCompressed(item, plugin)) {
            event.setCancelled(true);
        }
    }

    // ==========================================
    // 危险工匠台放入与 Shift-Click 拦截 (静默取消)
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        InventoryType topType = topInv.getType();

        // 仅防护黑名单中的功能加工容器
        if (!BLOCKED_CONTAINERS.contains(topType)) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // 1. 鼠标手持压缩方块点击放入加工容器
        if (event.getRawSlot() < topInv.getSize()) {
            if (CompressedItemUtil.isCompressed(cursor, plugin)) {
                event.setCancelled(true);
                return;
            }
            // 防御数字键 1~9 快捷换入加工容器
            if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
                int button = event.getHotbarButton();
                if (button >= 0 && button <= 8) {
                    ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(button);
                    if (CompressedItemUtil.isCompressed(hotbarItem, plugin)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // 2. 背包 Shift-Click 快捷送入加工容器
        if (event.isShiftClick() && CompressedItemUtil.isCompressed(current, plugin)) {
            event.setCancelled(true);
        }
    }

    // ==========================================
    // 危险工匠台鼠标拖拽放入拦截 (静默取消)
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (!BLOCKED_CONTAINERS.contains(topInv.getType())) {
            return;
        }

        if (!CompressedItemUtil.isCompressed(event.getOldCursor(), plugin)) {
            return;
        }

        for (int slot : event.getRawSlots()) {
            if (slot < topInv.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ==========================================
    // 熔炉类燃烧拦截 (防当原料冶炼与防当燃料烧毁)
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        ItemStack fuel = event.getFuel();
        if (CompressedItemUtil.isCompressed(fuel, plugin)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        ItemStack source = event.getSource();
        if (CompressedItemUtil.isCompressed(source, plugin)) {
            event.setCancelled(true);
        }
    }

    // ==========================================
    // 1.21+ 红石合成器 (Crafter) 误合拦截
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        // 合成器触发时若内部网格含有压缩方块，直接取消合成，防止被自动化装置误毁
        if (event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter) {
            for (ItemStack item : crafter.getInventory().getContents()) {
                if (CompressedItemUtil.isCompressed(item, plugin)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ==========================================
    // 堆肥桶右键防误吞拦截 (静默取消)
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractComposter(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.COMPOSTER) {
            return;
        }

        ItemStack item = event.getItem();
        if (CompressedItemUtil.isCompressed(item, plugin)) {
            event.setCancelled(true);
        }
    }
}
