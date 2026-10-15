package com.magconch.engine.modules.creaturebundle.util;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 生灵收纳袋物品构建与数据持久化工具
 * <p>
 * 基于 Paper 原生 PDC 管理容器状态与实体快照，严格封装 Data Components 数据。
 */
public final class BundleItemUtil {

    // ==========================================
    // PDC 命名空间键常量
    // ==========================================

    public static final String KEY_STATE = "creature_bundle_state";
    public static final String KEY_DATA = "creature_bundle_data";
    public static final String KEY_TYPE = "creature_bundle_type";
    public static final String KEY_NAME = "creature_bundle_name";
    public static final String KEY_HEALTH = "creature_bundle_health";
    public static final String KEY_MAX_HEALTH = "creature_bundle_max_health";

    public static final String STATE_EMPTY = "empty";
    public static final String STATE_FILLED = "filled";

    private BundleItemUtil() {
        // 工具类私有构造
    }

    // ==========================================
    // 物品判别逻辑
    // ==========================================

    /**
     * 判断物品是否为空白的生灵收纳袋
     *
     * @param item   待检测物品
     * @param plugin 插件实例
     * @return 若是空白生灵袋则返回 true
     */
    public static boolean isEmptyBundle(ItemStack item, MagconchEngine plugin) {
        if (item == null || item.getType() != Material.BUNDLE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey stateKey = new NamespacedKey(plugin, KEY_STATE);
        return STATE_EMPTY.equals(pdc.get(stateKey, PersistentDataType.STRING));
    }

    /**
     * 判断物品是否为已封存生物的生灵收纳袋
     *
     * @param item   待检测物品
     * @param plugin 插件实例
     * @return 若是已封存生灵袋则返回 true
     */
    public static boolean isFilledBundle(ItemStack item, MagconchEngine plugin) {
        if (item == null || item.getType() != Material.BUNDLE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey stateKey = new NamespacedKey(plugin, KEY_STATE);
        return STATE_FILLED.equals(pdc.get(stateKey, PersistentDataType.STRING));
    }

    /**
     * 判断物品是否为任何形态的生灵收纳袋（空白或已封存）
     *
     * @param item   待检测物品
     * @param plugin 插件实例
     * @return 若是生灵袋则返回 true
     */
    public static boolean isAnyCreatureBundle(ItemStack item, MagconchEngine plugin) {
        if (item == null || item.getType() != Material.BUNDLE) {
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
     * 构建一个崭新的空白生灵收纳袋
     *
     * @param plugin          插件主实例
     * @param customModelData 自定义材质模型 ID（0 则不设置）
     * @return 空白生灵袋物品
     */
    public static ItemStack createEmptyBundle(MagconchEngine plugin, int customModelData) {
        ItemStack item = new ItemStack(Material.BUNDLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 写入 PDC 状态
            NamespacedKey stateKey = new NamespacedKey(plugin, KEY_STATE);
            meta.getPersistentDataContainer().set(stateKey, PersistentDataType.STRING, STATE_EMPTY);

            // 模型值支持
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            // 从语言包渲染名称与说明
            String name = plugin.getLanguageManager().getMessage("creature-bundle.item.empty_name");
            meta.setDisplayName(name);

            List<String> rawLore = plugin.getLanguageManager().getMessageList("creature-bundle.item.empty_lore");
            meta.setLore(rawLore);

            // 附魔流光与神秘属性展示
            meta.setEnchantmentGlintOverride(true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 将目标生物封存入收纳袋，构建单堆叠的封存生灵袋
     *
     * @param plugin          插件实例
     * @param entity          原生物实体
     * @param snapshot        实体完整快照
     * @param customModelData 模型 ID
     * @return 封存生灵袋物品（堆叠数为 1）
     */
    public static ItemStack createFilledBundle(MagconchEngine plugin, LivingEntity entity, EntitySnapshot snapshot, int customModelData) {
        ItemStack item = new ItemStack(Material.BUNDLE, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(plugin, KEY_STATE), PersistentDataType.STRING, STATE_FILLED);
            pdc.set(new NamespacedKey(plugin, KEY_DATA), PersistentDataType.STRING, snapshot.getAsString());
            pdc.set(new NamespacedKey(plugin, KEY_TYPE), PersistentDataType.STRING, entity.getType().name());

            // 记录生物名称与血量
            String displayName = resolveEntityDisplayName(entity);
            pdc.set(new NamespacedKey(plugin, KEY_NAME), PersistentDataType.STRING, displayName);

            double health = Math.max(0.1, entity.getHealth());
            double maxHealth = health;
            AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealth = maxHealthAttr.getValue();
            }
            pdc.set(new NamespacedKey(plugin, KEY_HEALTH), PersistentDataType.DOUBLE, health);
            pdc.set(new NamespacedKey(plugin, KEY_MAX_HEALTH), PersistentDataType.DOUBLE, maxHealth);

            // 模型 ID
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            // 渲染封存物品名称
            String title = plugin.getLanguageManager().getMessage("creature-bundle.item.filled_name", "{entity_name}", displayName);
            meta.setDisplayName(title);

            // 渲染 Lore 信息
            List<String> loreTemplate = plugin.getLanguageManager().getMessageList("creature-bundle.item.filled_lore");
            String extraInfo = resolveExtraInfo(entity);
            String formattedHealth = String.format(Locale.ROOT, "%.1f", health);
            String formattedMaxHealth = String.format(Locale.ROOT, "%.1f", maxHealth);

            List<String> renderedLore = new ArrayList<>();
            for (String line : loreTemplate) {
                renderedLore.add(line
                        .replace("{entity_name}", displayName)
                        .replace("{health}", formattedHealth)
                        .replace("{max_health}", formattedMaxHealth)
                        .replace("{extra_info}", extraInfo)
                );
            }
            meta.setLore(TextUtil.colorize(renderedLore));

            // 附魔流光与神秘属性展示
            meta.setEnchantmentGlintOverride(true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 解析生物的友好显示名称
     */
    public static String resolveEntityDisplayName(LivingEntity entity) {
        if (entity.getCustomName() != null && !entity.getCustomName().trim().isEmpty()) {
            return entity.getCustomName();
        }
        // 返回易读的生物中文友好译名
        return getChineseEntityName(entity.getType());
    }

    /**
     * 解析生物的额外特征信息（如村民职业与等级、马匹状态等）
     */
    public static String resolveExtraInfo(LivingEntity entity) {
        if (entity instanceof Villager villager) {
            String profession = getVillagerProfessionName(villager.getProfession());
            return profession + " (等级 " + villager.getVillagerLevel() + ")";
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

    /**
     * 村民职业中文名称映射
     */
    public static String getVillagerProfessionName(Villager.Profession profession) {
        if (profession == null) {
            return "无业";
        }
        String key = profession.key().value().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "armorer" -> "盔甲匠";
            case "butcher" -> "屠夫";
            case "cartographer" -> "制图师";
            case "cleric" -> "牧师";
            case "farmer" -> "农民";
            case "fisherman" -> "渔夫";
            case "fletcher" -> "制箭师";
            case "leatherworker" -> "皮匠";
            case "librarian" -> "图书管理员";
            case "mason" -> "石匠";
            case "nitwit" -> "傻子";
            case "shepherd" -> "牧羊人";
            case "toolsmith" -> "工具匠";
            case "weaponsmith" -> "武器匠";
            default -> "无业";
        };
    }
}
