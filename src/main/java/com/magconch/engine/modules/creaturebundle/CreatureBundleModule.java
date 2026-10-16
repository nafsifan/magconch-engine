package com.magconch.engine.modules.creaturebundle;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.creaturebundle.command.BundleSubCommand;
import com.magconch.engine.modules.creaturebundle.config.CreatureBundleConfig;
import com.magconch.engine.modules.creaturebundle.listener.BundleInteractListener;
import com.magconch.engine.modules.creaturebundle.util.BundleItemUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 生物捕捉模块生命周期实现
 * <p>
 * 基于捕捉喷溅药水、生物蛋与 EntitySnapshot 原生快照技术，提供平衡、安全、无损的生物运输方案。
 */
public class CreatureBundleModule implements EngineModule {

    // ==========================================
    // 常量与状态字段
    // ==========================================

    private static final String MODULE_ID = "creature-bundle";

    private final MagconchEngine plugin;
    private final CreatureBundleConfig config = new CreatureBundleConfig();
    private final NamespacedKey recipeKey;

    private boolean enabled;
    private BundleInteractListener listener;
    private BundleSubCommand bundleSubCommand;

    public CreatureBundleModule(MagconchEngine plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "creature_bundle_crafting");
    }

    // ==========================================
    // EngineModule 生命周期契约
    // ==========================================

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        // 1. 加载模块配置
        config.load(plugin.getConfig());

        // 2. 注册管理员子指令
        if (bundleSubCommand == null) {
            bundleSubCommand = new BundleSubCommand(plugin, config);
            plugin.getMainCommand().registerSubCommand(bundleSubCommand);
        }

        // 3. 注册工作台合成配方（炼金星芒十字阵）
        registerCraftingRecipe();

        // 4. 注册事件监听器
        if (listener == null) {
            listener = new BundleInteractListener(plugin, config);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }

        this.enabled = config.isEnabled();
    }

    @Override
    public void onDisable() {
        // 1. 移除注册的工作台配方，防止重载或卸载时键冲突
        unregisterCraftingRecipe();

        // 2. 安全注销事件监听器
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }

        this.enabled = false;
    }

    @Override
    public void onReload() {
        onDisable();
        onEnable();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // ==========================================
    // 配方管理
    // ==========================================

    /**
     * 注册捕捉喷溅药水工作台合成配方
     */
    private void registerCraftingRecipe() {
        // 先清理可能存在的同键旧配方
        unregisterCraftingRecipe();

        if (!config.isEnableRecipe()) {
            return;
        }

        try {
            ItemStack potionResult = BundleItemUtil.createCapturePotion(plugin, config.getCustomModelData());
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, potionResult);

            List<String> shape = config.getRecipeShape();
            recipe.shape(shape.toArray(new String[0]));

            Map<Character, String> ingredients = config.getRecipeIngredients();

            for (Map.Entry<Character, String> entry : ingredients.entrySet()) {
                char key = entry.getKey();
                if (key == ' ' || !isCharInShape(shape, key)) {
                    continue;
                }

                String rawVal = entry.getValue();
                if (rawVal == null || rawVal.isBlank()) {
                    continue;
                }

                // 单行格式解析：可能为 MATERIAL 或 MATERIAL:EFFECT
                String[] parts = rawVal.trim().split(":", 2);
                Material material = Material.matchMaterial(parts[0].trim());
                if (material == null) {
                    plugin.getLogger().warning("模块 [creature-bundle] 合成配方材料无效: " + parts[0]);
                    continue;
                }

                // 若带有冒号指定效果（如 LINGERING_POTION:WEAKNESS），则构建精准效果匹配（自动兼顾普通版与加长版）
                if (parts.length > 1 && isPotionMaterial(material)) {
                    String effectName = parts[1].trim();
                    RecipeChoice choice = buildPotionChoice(material, effectName);
                    if (choice != null) {
                        recipe.setIngredient(key, choice);
                        continue;
                    }
                    plugin.getLogger().warning("模块 [creature-bundle] 未知或不支持的药水效果: " + effectName + "，已降级为不限效果");
                }

                recipe.setIngredient(key, material);
            }

            plugin.getServer().addRecipe(recipe);
        } catch (Exception ex) {
            plugin.getLogger().warning("模块 [creature-bundle] 注册合成配方失败: " + ex.getMessage());
        }
    }

    private boolean isCharInShape(List<String> shape, char key) {
        for (String row : shape) {
            if (row != null && row.indexOf(key) >= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isPotionMaterial(Material material) {
        return material == Material.LINGERING_POTION
                || material == Material.SPLASH_POTION
                || material == Material.POTION;
    }

    private RecipeChoice buildPotionChoice(Material material, String effectName) {
        List<ItemStack> choices = new ArrayList<>();
        String normalized = effectName.toUpperCase(Locale.ROOT);

        addPotionVariant(choices, material, normalized);
        if (!normalized.startsWith("LONG_")) {
            addPotionVariant(choices, material, "LONG_" + normalized);
        }

        return choices.isEmpty() ? null : new RecipeChoice.ExactChoice(choices);
    }

    private void addPotionVariant(List<ItemStack> choices, Material material, String typeName) {
        try {
            PotionType type = PotionType.valueOf(typeName);
            ItemStack item = new ItemStack(material);
            if (item.getItemMeta() instanceof PotionMeta meta) {
                meta.setBasePotionType(type);
                item.setItemMeta(meta);
                choices.add(item);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * 注销工作台合成配方
     */
    private void unregisterCraftingRecipe() {
        try {
            plugin.getServer().removeRecipe(recipeKey);
        } catch (Exception ignored) {
            // 忽略未注册或不存在的情形
        }
    }
}
