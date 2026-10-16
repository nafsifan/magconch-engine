package com.magconch.engine.modules.creaturebundle.util;

import com.magconch.engine.MagconchEngine;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 生物捕捉系统物品构建与持久化工具
 * <p>
 * 支持构建与判别「捕捉喷溅药水」与「生物蛋」，严格通过 PDC 维护完整的 EntitySnapshot。
 */
public final class BundleItemUtil {

    // ==========================================
    // PDC 命名空间键常量
    // ==========================================

    public static final String KEY_STATE = "creature_bundle_state";
    public static final String KEY_DATA = "creature_bundle_data";
    public static final String KEY_TYPE = "creature_bundle_type";
    public static final String KEY_NAME = "creature_bundle_name";

    public static final String STATE_POTION = "capture_potion";
    public static final String STATE_FILLED_EGG = "filled_egg";

    private BundleItemUtil() {
        // 工具类私有构造
    }

    // ==========================================
    // 物品判别逻辑
    // ==========================================

    /**
     * 判断物品是否为捕捉药水（支持滞留与喷溅瓶）
     */
    public static boolean isCapturePotion(ItemStack item, MagconchEngine plugin) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        if (type != Material.LINGERING_POTION && type != Material.SPLASH_POTION) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey stateKey = new NamespacedKey(plugin, KEY_STATE);
        return STATE_POTION.equals(pdc.get(stateKey, PersistentDataType.STRING));
    }

    /**
     * 判断物品是否为已封存生物的生物蛋
     */
    public static boolean isFilledEgg(ItemStack item, MagconchEngine plugin) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey stateKey = new NamespacedKey(plugin, KEY_STATE);
        return STATE_FILLED_EGG.equals(pdc.get(stateKey, PersistentDataType.STRING));
    }

    /**
     * 判断物品是否为生物捕捉模块专属的任何特殊道具
     */
    public static boolean isAnyModuleItem(ItemStack item, MagconchEngine plugin) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey stateKey = new NamespacedKey(plugin, KEY_STATE);
        return pdc.has(stateKey, PersistentDataType.STRING);
    }

    // ==========================================
    // 物品生成逻辑
    // ==========================================

    /**
     * 构建一瓶崭新的「捕捉药水」（滞留药水形态）
     *
     * @param plugin          插件主实例
     * @param customModelData 自定义材质模型 ID（0 则不设置）
     * @return 捕捉药水物品
     */
    public static ItemStack createCapturePotion(MagconchEngine plugin, int customModelData) {
        ItemStack item = new ItemStack(Material.LINGERING_POTION);
        ItemMeta rawMeta = item.getItemMeta();
        if (rawMeta instanceof PotionMeta meta) {
            // 设置神秘深紫青色调
            meta.setColor(Color.fromRGB(138, 43, 226));
            meta.setBasePotionType(PotionType.WEAKNESS);
            meta.addCustomEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0), true);

            // 写入 PDC 状态
            NamespacedKey stateKey = new NamespacedKey(plugin, KEY_STATE);
            meta.getPersistentDataContainer().set(stateKey, PersistentDataType.STRING, STATE_POTION);

            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            // 从语言包渲染名称与说明
            String name = plugin.getLanguageManager().getMessage("creature-bundle.item.potion_name");
            meta.setDisplayName(name);

            List<String> rawLore = plugin.getLanguageManager().getMessageList("creature-bundle.item.potion_lore");
            if (rawLore.isEmpty()) {
                meta.setLore(null);
            } else {
                meta.setLore(rawLore);
            }

            // 附魔流光效果与提示清理
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 将目标生物封存入对应的原版生物生成蛋，构建单堆叠的生物蛋
     *
     * @param plugin          插件实例
     * @param entity          原生物实体
     * @param snapshot        实体完整快照
     * @param customModelData 模型 ID
     * @return 生物蛋物品
     */
    public static ItemStack createFilledEgg(MagconchEngine plugin, LivingEntity entity, EntitySnapshot snapshot, int customModelData) {
        Material eggMaterial = resolveSpawnEggMaterial(entity.getType());
        ItemStack item = new ItemStack(eggMaterial, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(plugin, KEY_STATE), PersistentDataType.STRING, STATE_FILLED_EGG);
            pdc.set(new NamespacedKey(plugin, KEY_DATA), PersistentDataType.STRING, snapshot.getAsString());
            pdc.set(new NamespacedKey(plugin, KEY_TYPE), PersistentDataType.STRING, entity.getType().name());

            String displayName = resolveEntityDisplayName(entity);
            pdc.set(new NamespacedKey(plugin, KEY_NAME), PersistentDataType.STRING, displayName);

            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            // 渲染封存物品名称
            String title = plugin.getLanguageManager().getMessage("creature-bundle.item.filled_egg_name", "{entity_name}", displayName);
            meta.setDisplayName(title);

            // 渲染 Lore 信息
            List<String> loreTemplate = plugin.getLanguageManager().getMessageList("creature-bundle.item.filled_egg_lore");
            if (loreTemplate.isEmpty()) {
                meta.setLore(null);
            } else {
                List<String> renderedLore = new ArrayList<>();
                for (String line : loreTemplate) {
                    renderedLore.add(line.replace("{entity_name}", displayName));
                }
                meta.setLore(renderedLore);
            }

            // 附魔流光效果
            meta.setEnchantmentGlintOverride(true);

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 动态匹配生物类型对应的原版刷怪蛋材质，无法匹配时兜底为普通鸡蛋
     */
    public static Material resolveSpawnEggMaterial(EntityType type) {
        String eggName = type.name() + "_SPAWN_EGG";
        Material match = Material.matchMaterial(eggName);
        if (match != null && match.isItem()) {
            return match;
        }
        // 针对没有对应刷怪蛋的特殊实体做兼容兜底
        return Material.EGG;
    }

    /**
     * 解析生物的友好显示名称
     */
    public static String resolveEntityDisplayName(LivingEntity entity) {
        if (entity.getCustomName() != null && !entity.getCustomName().trim().isEmpty()) {
            return entity.getCustomName();
        }
        return getChineseEntityName(entity.getType());
    }

    /**
     * 原生常见生物中文名称映射字典
     */
    public static String getChineseEntityName(EntityType type) {
        return switch (type) {
            case COW -> "牛";
            case SHEEP -> "羊";
            case PIG -> "猪";
            case CHICKEN -> "鸡";
            case RABBIT -> "兔子";
            case BEE -> "蜜蜂";
            case TURTLE -> "海龟";
            case FROG -> "青蛙";
            case AXOLOTL -> "美西螈";
            case SQUID -> "鱿鱼";
            case GLOW_SQUID -> "发光鱿鱼";
            case MOOSHROOM -> "哞菇";
            case GOAT -> "山羊";
            case HORSE -> "马";
            case DONKEY -> "驴";
            case MULE -> "骡";
            case LLAMA -> "羊驼";
            case TRADER_LLAMA -> "行商羊驼";
            case CAMEL -> "骆驼";
            case SNIFFER -> "嗅探兽";
            case ARMADILLO -> "犰狳";
            case STRIDER -> "炽足兽";
            case ALLAY -> "悦灵";
            case WOLF -> "狼";
            case CAT -> "猫";
            case PARROT -> "鹦鹉";
            case OCELOT -> "豹猫";
            case FOX -> "狐狸";
            case VILLAGER -> "村民";
            case ZOMBIE_VILLAGER -> "僵尸村民";
            case SNOW_GOLEM -> "雪傀儡";
            case IRON_GOLEM -> "铁傀儡";
            default -> type.name().toLowerCase(Locale.ROOT);
        };
    }
}
