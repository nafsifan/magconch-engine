package com.magconch.engine.modules.creaturebundle.util;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
     * @param plugin 插件主实例
     * @return 捕捉药水物品
     */
    public static ItemStack createCapturePotion(MagconchEngine plugin) {
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

            // 从语言包渲染名称与说明并去除客户端默认斜体
            String rawName = plugin.getLanguageManager().getMessage("creature-bundle.item.potion_name");
            meta.displayName(LegacyComponentSerializer.legacySection()
                    .deserialize(rawName)
                    .decoration(TextDecoration.ITALIC, false));

            List<String> rawLore = plugin.getLanguageManager().getMessageList("creature-bundle.item.potion_lore");
            if (rawLore.isEmpty()) {
                meta.lore(null);
            } else {
                List<Component> loreList = new ArrayList<>(rawLore.size());
                for (String line : rawLore) {
                    loreList.add(LegacyComponentSerializer.legacySection()
                            .deserialize(line)
                            .decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(loreList);
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
     * @param plugin   插件实例
     * @param entity   原生物实体
     * @param snapshot 实体完整快照
     * @return 生物蛋物品
     */
    public static ItemStack createFilledEgg(MagconchEngine plugin, LivingEntity entity, EntitySnapshot snapshot) {
        Material eggMaterial = resolveSpawnEggMaterial(entity.getType());
        ItemStack item = new ItemStack(eggMaterial, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(plugin, KEY_STATE), PersistentDataType.STRING, STATE_FILLED_EGG);
            pdc.set(new NamespacedKey(plugin, KEY_DATA), PersistentDataType.STRING, snapshot.getAsString());
            pdc.set(new NamespacedKey(plugin, KEY_TYPE), PersistentDataType.STRING, entity.getType().name());

            Component entityName = resolveEntityDisplayName(entity);
            String plainName = PlainTextComponentSerializer.plainText().serialize(entityName);
            pdc.set(new NamespacedKey(plugin, KEY_NAME), PersistentDataType.STRING, plainName);

            // 渲染封存物品名称并去除客户端默认斜体
            String titleTemplate = plugin.getLanguageManager().getRawMessage("creature-bundle.item.filled_egg_name");
            Component baseTitle = LegacyComponentSerializer.legacySection().deserialize(TextUtil.colorize(titleTemplate));
            Component finalTitle = baseTitle.replaceText(b -> b.matchLiteral("{entity_name}").replacement(entityName))
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(finalTitle);

            // 渲染 Lore 信息
            List<String> loreTemplate = plugin.getLanguageManager().getMessageList("creature-bundle.item.filled_egg_lore");
            if (loreTemplate.isEmpty()) {
                meta.lore(null);
            } else {
                List<Component> renderedLore = new ArrayList<>();
                for (String line : loreTemplate) {
                    Component baseLine = LegacyComponentSerializer.legacySection()
                            .deserialize(line)
                            .decoration(TextDecoration.ITALIC, false);
                    renderedLore.add(baseLine.replaceText(b -> b.matchLiteral("{entity_name}").replacement(entityName)));
                }
                meta.lore(renderedLore);
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
        return Material.EGG;
    }

    /**
     * 解析生物的友好显示名称组件（优先自定义命名，无自定义名则调用原版多语言键）
     */
    public static Component resolveEntityDisplayName(LivingEntity entity) {
        Component customName = entity.customName();
        if (customName != null) {
            return customName;
        }
        return Component.translatable(entity.getType().translationKey());
    }
}
