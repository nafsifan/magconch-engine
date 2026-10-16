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
 * 采用“内置全量友好生物 + 轻量黑名单排除”模型，配置极致精炼。
 */
public class CreatureBundleConfig {

    private boolean enabled = true;
    private boolean enableRecipe = true;
    private List<String> recipeShape = List.of(" T ", "EPE", " G ");
    private final Map<Character, String> recipeIngredients = new LinkedHashMap<>();
    private double captureChance = 1.0;
    private final Set<EntityType> allowedEntityTypes = new HashSet<>();

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

        // 1. 初始化内置友好/被动/功能生物白名单
        this.allowedEntityTypes.clear();
        loadDefaultAllowedTypes();

        // 2. 从配置中排除服主指定禁用的生物类型
        List<String> disabledRaw = config.getStringList(path + "disabled-entities");
        for (String raw : disabledRaw) {
            try {
                EntityType type = EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                this.allowedEntityTypes.remove(type);
            } catch (IllegalArgumentException ignored) {
                // 忽略非法的枚举输入
            }
        }
    }

    /**
     * 内置支持的友好与功能型生物清单
     */
    private void loadDefaultAllowedTypes() {
        Collections.addAll(allowedEntityTypes,
                EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN,
                EntityType.RABBIT, EntityType.BEE, EntityType.TURTLE, EntityType.FROG,
                EntityType.AXOLOTL, EntityType.SQUID, EntityType.GLOW_SQUID, EntityType.MOOSHROOM,
                EntityType.GOAT, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
                EntityType.LLAMA, EntityType.TRADER_LLAMA, EntityType.CAMEL, EntityType.SNIFFER,
                EntityType.ARMADILLO, EntityType.STRIDER, EntityType.ALLAY, EntityType.WOLF,
                EntityType.CAT, EntityType.PARROT, EntityType.OCELOT, EntityType.FOX,
                EntityType.VILLAGER, EntityType.ZOMBIE_VILLAGER, EntityType.SNOW_GOLEM
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
        return allowedEntityTypes.contains(type);
    }
}
