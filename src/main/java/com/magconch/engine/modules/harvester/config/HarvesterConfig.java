package com.magconch.engine.modules.harvester.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

/**
 * 快速收菜模块配置管理器
 * <p>
 * 负责解析模块启用状态及作物种子映射关系。
 */
public class HarvesterConfig {

    // ==========================================
    // 静态映射：作物方块与对应消耗种子
    // ==========================================

    private static final Map<Material, Material> CROP_SEED_MAP = new EnumMap<>(Material.class);

    static {
        CROP_SEED_MAP.put(Material.WHEAT, Material.WHEAT_SEEDS);
        CROP_SEED_MAP.put(Material.CARROTS, Material.CARROT);
        CROP_SEED_MAP.put(Material.POTATOES, Material.POTATO);
        CROP_SEED_MAP.put(Material.BEETROOTS, Material.BEETROOT_SEEDS);
        CROP_SEED_MAP.put(Material.NETHER_WART, Material.NETHER_WART);
        CROP_SEED_MAP.put(Material.COCOA, Material.COCOA_BEANS);
    }

    // ==========================================
    // 配置字段与加载
    // ==========================================

    private boolean enabled = true;

    /**
     * 从主配置文件加载收菜模块状态
     *
     * @param config 主配置文件对象
     */
    public void load(FileConfiguration config) {
        if (config == null) {
            return;
        }
        this.enabled = config.getBoolean("modules.harvester.enabled", true);
    }

    // ==========================================
    // 业务工具方法与访问器
    // ==========================================

    /**
     * 判断方块材质是否为支持的作物
     *
     * @param material 方块材质
     * @return true 为受支持的作物
     */
    public boolean isAllowedCrop(Material material) {
        return material != null && CROP_SEED_MAP.containsKey(material);
    }

    /**
     * 获取作物对应的种子或原材料类型
     *
     * @param cropMaterial 作物材质
     * @return 种子材质，未收录则返回 null
     */
    public Material getSeedType(Material cropMaterial) {
        if (cropMaterial == null) {
            return null;
        }
        return CROP_SEED_MAP.get(cropMaterial);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
