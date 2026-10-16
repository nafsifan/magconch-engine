package com.magconch.engine.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * 物品发放与安全转移工具
 * <p>
 * 遵循服务器防刷防丢安全准则，优先安全放入背包，满溢时安全自然掉落。
 */
public final class ItemUtil {

    private ItemUtil() {
        // 工具类私有构造
    }

    /**
     * 安全将物品给予玩家，满溢时在玩家所在位置安全掉落
     *
     * @param player 接收物品的玩家
     * @param item   待发放物品
     */
    public static void giveItemSafely(Player player, ItemStack item) {
        if (player == null || item == null || item.getType().isAir()) {
            return;
        }
        giveItemSafely(player, player.getLocation(), item);
    }

    /**
     * 安全将物品给予玩家，满溢时在指定位置安全掉落
     *
     * @param player        接收物品的玩家
     * @param dropLocation  满溢时的掉落位置
     * @param item          待发放物品
     */
    public static void giveItemSafely(Player player, Location dropLocation, ItemStack item) {
        if (player == null || item == null || item.getType().isAir()) {
            return;
        }

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty() && dropLocation != null && dropLocation.getWorld() != null) {
            for (ItemStack leftover : overflow.values()) {
                dropLocation.getWorld().dropItemNaturally(dropLocation, leftover);
            }
        }
    }
}
