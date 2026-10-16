package com.magconch.engine.modules.expstore.util;

import com.magconch.engine.MagconchEngine;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/**
 * 经验存储瓶物品构建与判别工具
 * <p>
 * 基于 PDC 持久化存储经验点数，保障标准点数经验瓶完全相同并可堆叠至 64。
 */
public final class ExpBottleUtil {

    // ==========================================
    // 常量定义
    // ==========================================

    public static final String KEY_EXP_POINTS = "exp_store_points";

    private ExpBottleUtil() {
        // 工具类私有构造
    }

    // ==========================================
    // 物品判别与数据读取
    // ==========================================

    /**
     * 判断目标物品是否为本模块生成的经验瓶
     *
     * @param item   目标物品
     * @param plugin 插件主类实例
     * @return 是否为经验存储瓶
     */
    public static boolean isExpBottle(ItemStack item, MagconchEngine plugin) {
        if (item == null || item.getType() != Material.EXPERIENCE_BOTTLE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, KEY_EXP_POINTS);
        return pdc.has(key, PersistentDataType.INTEGER);
    }

    /**
     * 读取经验瓶中封存的经验点数
     *
     * @param item          目标物品
     * @param plugin        插件主类实例
     * @param defaultPoints 回退默认点数
     * @return 封存的经验点数
     */
    public static int getStoredPoints(ItemStack item, MagconchEngine plugin, int defaultPoints) {
        if (!isExpBottle(item, plugin)) {
            return defaultPoints;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return defaultPoints;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, KEY_EXP_POINTS);
        Integer points = pdc.get(key, PersistentDataType.INTEGER);
        return (points != null && points > 0) ? points : defaultPoints;
    }

    // ==========================================
    // 物品生成逻辑
    // ==========================================

    /**
     * 构建一个指定经验点数的标准经验瓶
     *
     * @param plugin          插件主类实例
     * @param points          封存的经验点数
     * @param customModelData 自定义材质模型 ID（0 则不设置）
     * @return 经验存储瓶物品
     */
    public static ItemStack createExpBottle(MagconchEngine plugin, int points, int customModelData) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 1. 写入 PDC 经验点数
            NamespacedKey key = new NamespacedKey(plugin, KEY_EXP_POINTS);
            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, points);

            // 2. 自定义材质模型支持
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            // 3. 多语言显示名渲染（无 Lore 视觉杂音）
            String name = plugin.getLanguageManager().getMessage("exp-store.item.name", "{points}", String.valueOf(points));
            meta.setDisplayName(name);

            // 4. 视觉增强：流光附魔与隐藏标记
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

            item.setItemMeta(meta);
        }
        return item;
    }

    // ==========================================
    // 安全发物机制
    // ==========================================

    /**
     * 安全将物品给予玩家，背包溢出时自动平滑掉落在脚下，杜绝吞物漏洞
     *
     * @param player 目标玩家
     * @param item   给予的物品
     */
    public static void giveItemSafely(Player player, ItemStack item) {
        if (player == null || item == null || item.getAmount() <= 0) {
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack drop : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }
}
