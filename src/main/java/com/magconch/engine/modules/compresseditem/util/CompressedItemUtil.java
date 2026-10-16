package com.magconch.engine.modules.compresseditem.util;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;

/**
 * 压缩物品数据解析与外观渲染工具
 * <p>
 * 负责通过 PDC 原子标记压缩层级、注入纯净附魔流光，并按阶级色彩渲染单行极简 Lore。
 */
public final class CompressedItemUtil {

    // ==========================================
    // 常量与键定义
    // ==========================================

    public static final String KEY_COMPRESSED_LEVEL = "compressed_level";
    private static NamespacedKey levelKey;

    private static final String[] CAPACITY_STRINGS = {
            "9",
            "81",
            "729",
            "6,561",
            "59,049",
            "531,441",
            "4,782,969",
            "43,046,721",
            "387,420,489"
    };

    private static final String[] LEVEL_COLORS = {
            "&#52c41a",      // 1x 翠绿
            "&#36cfc9",      // 2x 冰青
            "&#1677ff",      // 3x 湛蓝
            "&#722ed1",      // 4x 幽紫
            "&#fa541c",      // 5x 烈橙
            "&#f5222d",      // 6x 绯红
            "&#eb2f96",      // 7x 瑰粉
            "&#faad14",      // 8x 辉金
            "&#ffd700&l"     // 9x 流金粗体
    };

    private CompressedItemUtil() {
        // 工具类私有构造
    }

    // ==========================================
    // PDC 层级识别与判断
    // ==========================================

    /**
     * 获取物品的压缩层级
     *
     * @param item 目标物品
     * @param plugin 核心插件实例
     * @return 压缩层级（0 表示未压缩的普通物品）
     */
    public static int getCompressedLevel(ItemStack item, MagconchEngine plugin) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer level = pdc.get(getKey(plugin), PersistentDataType.INTEGER);
        return level != null ? level : 0;
    }

    /**
     * 判断物品是否为压缩物品
     *
     * @param item 目标物品
     * @param plugin 核心插件实例
     * @return 是否为压缩物品
     */
    public static boolean isCompressed(ItemStack item, MagconchEngine plugin) {
        return getCompressedLevel(item, plugin) > 0;
    }

    // ==========================================
    // 物品构造与解包
    // ==========================================

    /**
     * 构建指定层级的压缩物品
     *
     * @param material 方块材质
     * @param level 目标压缩层级 (1~9)
     * @param amount 产出数量
     * @param plugin 核心插件实例
     * @return 带有 PDC 与单行流光 Lore 的压缩物品
     */
    public static ItemStack createCompressedItem(Material material, int level, int amount, MagconchEngine plugin) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // 1. 写入 PDC 压缩层级
        meta.getPersistentDataContainer().set(getKey(plugin), PersistentDataType.INTEGER, level);

        // 2. 赋予原生纯净附魔流光
        meta.setEnchantmentGlintOverride(true);

        // 3. 构建单行双拼色 Lore 并消除原版默认斜体
        String color = getLevelColor(level);
        String capacity = getCapacityString(level);
        String rawLore = color + "◈ " + level + "x 压缩 &r&#8c8c8c(" + capacity + ")";
        Component loreComponent = LegacyComponentSerializer.legacySection()
                .deserialize(TextUtil.colorize(rawLore))
                .decoration(TextDecoration.ITALIC, false);
        meta.lore(Collections.singletonList(loreComponent));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * 构建上一阶降级物品（若目标层级 <= 0 则返回原版纯净方块）
     *
     * @param material 方块材质
     * @param targetLevel 目标层级
     * @param amount 产出数量
     * @param plugin 核心插件实例
     * @return 降阶后的物品或原生物块
     */
    public static ItemStack createDecompressedItem(Material material, int targetLevel, int amount, MagconchEngine plugin) {
        if (targetLevel <= 0) {
            return new ItemStack(material, Math.max(1, amount));
        }
        return createCompressedItem(material, targetLevel, amount, plugin);
    }

    /**
     * 根据层级返回等效方块总数的千分位格式化字符串
     */
    public static String getCapacityString(int level) {
        if (level <= 0) {
            return "0";
        }
        int index = Math.min(level, CAPACITY_STRINGS.length) - 1;
        return CAPACITY_STRINGS[index];
    }

    /**
     * 获取常驻缓存的 PDC 命名空间键
     */
    public static NamespacedKey getKey(MagconchEngine plugin) {
        if (levelKey == null) {
            levelKey = new NamespacedKey(plugin, KEY_COMPRESSED_LEVEL);
        }
        return levelKey;
    }

    /**
     * 根据层级返回预设色阶代码
     */
    public static String getLevelColor(int level) {
        if (level <= 0) {
            return "&#ffffff";
        }
        int index = Math.min(level, LEVEL_COLORS.length) - 1;
        return LEVEL_COLORS[index];
    }
}
