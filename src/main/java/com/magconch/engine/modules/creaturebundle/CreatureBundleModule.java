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
import org.bukkit.inventory.ShapedRecipe;

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
     * 注册捕捉喷溅药水工作台合成配方（炼金星芒十字阵）
     * <pre>
     * [ 空 ]   [ 不死图腾 ]   [ 空 ]
     * [末影珍珠] [喷溅虚弱药水] [末影珍珠]
     * [ 空 ]     [ 鸡蛋 ]     [ 空 ]
     * </pre>
     */
    private void registerCraftingRecipe() {
        // 先清理可能存在的同键旧配方
        unregisterCraftingRecipe();

        if (!config.isEnableRecipe()) {
            return;
        }

        ItemStack potionResult = BundleItemUtil.createCapturePotion(plugin, config.getCustomModelData());
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, potionResult);
        recipe.shape(
                " T ",
                "EPE",
                " G "
        );
        recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);
        recipe.setIngredient('E', Material.ENDER_PEARL);
        recipe.setIngredient('P', Material.SPLASH_POTION);
        recipe.setIngredient('G', Material.EGG);

        try {
            plugin.getServer().addRecipe(recipe);
        } catch (Exception ex) {
            plugin.getLogger().warning("[生物捕捉] 注册合成配方失败: " + ex.getMessage());
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
