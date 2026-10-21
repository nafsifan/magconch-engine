package com.magconch.engine.modules.creaturebundle.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 生物捕捉模块配置映射
 * <p>
 * 采用配置文件黑名单驱动，默认允许捕获绝大多数活体生物，仅拦截 Boss 与特定限制实体。
 */
public class CreatureBundleConfig {

    private boolean enabled = true;
    private boolean enableRecipe = true;
    private List<String> recipeShape = List.of(" T ", "EPE", " G ");
    private final Map<Character, String> recipeIngredients = new LinkedHashMap<>();
    private double captureChance = 1.0;
    private final Set<EntityType> blacklistedEntityTypes = new HashSet<>();

    /**
     * 读取并解析配置
     */
    public void load(FileConfiguration config) {
        String path = "modules.creature-bundle.";
        this.enabled = config.getBoolean(path + "enabled", true);

        // 兼容结构化 recipe.enabled 与旧版 enable-recipe
        if (config.isSet(path + "recipe.enabled")) {
            this.enableRecipe = config.getBoolean(path + "recipe.enabled", true);
        } else {
            this.enableRecipe = config.getBoolean(path + "enable-recipe", true);
        }

        // 读取工作台九宫格形状
        List<String> rawShape = config.getStringList(path + "recipe.shape");
        if (rawShape != null && !rawShape.isEmpty() && rawShape.size() <= 3) {
            this.recipeShape = rawShape;
        } else {
            this.recipeShape = List.of(" T ", "EPE", " G ");
        }

        // 读取单行原材料映射（支持普通材质与 材质:药水效果 格式）
        this.recipeIngredients.clear();
        ConfigurationSection ingredientSection = config.getConfigurationSection(path + "recipe.ingredients");
        if (ingredientSection != null) {
            for (String key : ingredientSection.getKeys(false)) {
                if (!key.isEmpty()) {
                    this.recipeIngredients.put(key.charAt(0), ingredientSection.getString(key, ""));
                }
            }
        } else {
            this.recipeIngredients.put('T', "TOTEM_OF_UNDYING");
            this.recipeIngredients.put('E', "ENDER_PEARL");
            this.recipeIngredients.put('G', "EGG");
            this.recipeIngredients.put('P', "LINGERING_POTION:WEAKNESS");
        }

        this.captureChance = Math.max(0.0, Math.min(1.0, config.getDouble(path + "capture-chance", 1.0)));

        // 读取禁止捕捉的生物黑名单
        this.blacklistedEntityTypes.clear();
        List<String> rawBlacklist = config.getStringList(path + "blacklisted-entities");
        if (rawBlacklist != null && !rawBlacklist.isEmpty()) {
            for (String raw : rawBlacklist) {
                try {
                    EntityType type = EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                    this.blacklistedEntityTypes.add(type);
                } catch (IllegalArgumentException ignored) {
                    // 忽略无效的实体类型枚举
                }
            }
        } else {
            // 配置未指定黑名单时，加载默认拦截名单
            loadDefaultBlacklistedTypes();
        }
    }

    /**
     * 默认拦截的生物黑名单（Boss 及特殊机制生物）
     */
    private void loadDefaultBlacklistedTypes() {
        Collections.addAll(blacklistedEntityTypes,
                EntityType.ENDER_DRAGON,
                EntityType.WITHER,
                EntityType.WARDEN,
                EntityType.ELDER_GUARDIAN,
                EntityType.GIANT,
                EntityType.WANDERING_TRADER
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEnableRecipe() {
        return enableRecipe;
    }

    public List<String> getRecipeShape() {
        return Collections.unmodifiableList(recipeShape);
    }

    public Map<Character, String> getRecipeIngredients() {
        return Collections.unmodifiableMap(recipeIngredients);
    }

    public double getCaptureChance() {
        return captureChance;
    }

    public boolean isAllowed(EntityType type) {
        return !blacklistedEntityTypes.contains(type);
    }

    public boolean isBlacklisted(EntityType type) {
        return blacklistedEntityTypes.contains(type);
    }
}
