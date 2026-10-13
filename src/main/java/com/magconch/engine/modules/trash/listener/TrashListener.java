package com.magconch.engine.modules.trash.listener;

import com.magconch.engine.modules.trash.TrashHolder;
import com.magconch.engine.modules.trash.config.TrashConfig;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 垃圾桶关闭监听器
 * <p>
 * 当玩家关闭垃圾桶容器且内部存在待销毁物品时，触发对应的销毁音效。
 */
public class TrashListener implements Listener {

    private final TrashConfig config;

    public TrashListener(TrashConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!config.isSoundEnabled()) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (inventory.getHolder() instanceof TrashHolder) {
            // 检查容器内是否存在有效物品被销毁
            boolean hasItems = false;
            for (ItemStack item : inventory.getContents()) {
                if (item != null && !item.getType().isAir()) {
                    hasItems = true;
                    break;
                }
            }

            if (hasItems && event.getPlayer() instanceof Player player) {
                // 播放岩浆熄灭声提示物品已彻底销毁
                player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.6f, 1.2f);
            }
        }
    }
}
