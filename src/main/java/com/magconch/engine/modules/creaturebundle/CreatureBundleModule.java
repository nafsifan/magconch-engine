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
 * 生灵收纳袋模块生命周期实现
 * <p>
 * 基于收纳袋与 EntitySnapshot 原生快照技术，提供平衡、安全、无损的生物打包运输方案。
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

        // 3. 注册工作台合成配方（环绕 8 末影珍珠 + 1 收纳袋）
        registerCraftingRecipe();

        // 4. 注册事件监听器
        if (listener == null) {
            listener = new BundleInteractListener(plugin, config);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }

        this.enabled = config.isEnabled();
        if (enabled) {
            plugin.getLogger().info("[生灵收纳袋] 模块已成功加载并启动。");
        }
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
        plugin.getLogger().info("[生灵收纳袋] 模块已安全卸载。");
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
     * 注册生灵收纳袋工作台合成配方
     */
    private void registerCraftingRecipe() {
        // 先清理可能存在的同键旧配方
        unregisterCraftingRecipe();

        if (!config.isEnableRecipe()) {
            return;
        }

        ItemStack emptyBundle = BundleItemUtil.createEmptyBundle(plugin, config.getCustomModelData());
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, emptyBundle);
        recipe.shape(
                "PPP",
                "PBP",
                "PPP"
        );
        recipe.setIngredient('P', Material.ENDER_PEARL);
        recipe.setIngredient('B', Material.BUNDLE);

        try {
            plugin.getServer().addRecipe(recipe);
            plugin.getLogger().info("[生灵收纳袋] 已注册工作台合成配方 (8 末影珍珠 + 1 收纳袋)。");
        } catch (Exception ex) {
            plugin.getLogger().warning("[生灵收纳袋] 注册合成配方失败: " + ex.getMessage());
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
