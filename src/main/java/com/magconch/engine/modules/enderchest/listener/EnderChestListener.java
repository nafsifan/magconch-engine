package com.magconch.engine.modules.enderchest.listener;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.enderchest.EnderChestHolder;
import com.magconch.engine.modules.enderchest.EnderChestManager;
import com.magconch.engine.modules.enderchest.config.EnderChestConfig;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 扩展末影箱事件监听器
 * <p>
 * 负责物理末影箱方块的右键接管、原版音效与遮挡检测，以及关箱时的数据持久化调度。
 */
public class EnderChestListener implements Listener {

    // ==========================================
    // 依赖
    // ==========================================

    private static final String PERMISSION_USE = "magconch.enderchest.use";

    private final MagconchEngine plugin;
    private final EnderChestManager manager;
    private final EnderChestConfig config;

    public EnderChestListener(MagconchEngine plugin, EnderChestManager manager, EnderChestConfig config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
    }

    // ==========================================
    // 物理方块点击接管
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderChestInteract(PlayerInteractEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        // 仅处理主手右键方块交互
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.ENDER_CHEST) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();

        // 潜行手持方块时放行，允许玩家在箱体表面放置方块
        if (player.isSneaking() && handItem.getType().isBlock()) {
            return;
        }

        // 拦截原版打开行为
        event.setCancelled(true);

        // 遵循原版物理特性：上方有实体方块阻挡时不可打开
        Block above = clicked.getRelative(BlockFace.UP);
        if (above.getType().isOccluding()) {
            return;
        }

        // 权限判定
        if (!player.hasPermission(PERMISSION_USE)) {
            plugin.getLanguageManager().sendMessage(player, "general.no_permission");
            return;
        }

        // 播放原版开箱音效并开启 54 格界面
        player.playSound(clicked.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
        manager.openEnderChest(player, player.getUniqueId(), player.getName());
    }

    // ==========================================
    // 界面关闭与持久化回调
    // ==========================================

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof EnderChestHolder holder) {
            if (event.getPlayer() instanceof Player player) {
                player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 1.0f, 1.0f);
            }
            manager.handleClose(holder, event.getInventory());
        }
    }
}
