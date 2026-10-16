package com.magconch.engine.modules.compresseditem.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 压缩物品模块精炼配置与方块合法性过滤
 * <p>
 * 内建 9 级最大上限并自动排除容器/技术方块，仅保留紧急禁用黑名单。
 */
public class CompressedItemConfig {

    public static final int MAX_LEVEL = 9;

    private boolean enabled = true;
    private final Set<Material> blacklist = new HashSet<>();

    /**
     * 加载配置参数
     */
    public void load(FileConfiguration config) {
        String path = "modules.compressed-item.";
        this.enabled = config.getBoolean(path + "enabled", true);

        this.blacklist.clear();
        List<String> rawBlacklist = config.getStringList(path + "blacklist");
        for (String name : rawBlacklist) {
            Material mat = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
            if (mat != null) {
                blacklist.add(mat);
            }
        }
    }

    /**
     * 判断方块材质是否允许压缩
     */
    public boolean isCompressible(Material material) {
        if (material == null || material.isAir() || !material.isBlock() || !material.isItem()) {
            return false;
        }

        // 黑名单直接拒绝
        if (blacklist.contains(material)) {
            return false;
        }

        // 固有容器与技术方块安全排除
        return !isInherentlyDangerous(material);
    }

    private boolean isInherentlyDangerous(Material mat) {
        String name = mat.name();

        if (name.endsWith("SHULKER_BOX")) return true;
        if (name.contains("CHEST") || name.equals("BARREL") || name.equals("HOPPER")
                || name.equals("DISPENSER") || name.equals("DROPPER") || name.equals("CRAFTER")) {
            return true;
        }
        if (name.equals("FURNACE") || name.equals("BLAST_FURNACE") || name.equals("SMOKER")) {
            return true;
        }
        if (name.contains("BEEHIVE") || name.contains("BEE_NEST") || name.equals("BEACON")
                || name.equals("SPAWNER") || name.equals("ENCHANTING_TABLE")
                || name.equals("DECORATED_POT") || name.equals("CHISELED_BOOKSHELF")
                || name.equals("JUKEBOX") || name.equals("LECTERN")
                || name.equals("BREWING_STAND") || name.contains("CAMPFIRE")) {
            return true;
        }
        return name.equals("BEDROCK") || name.equals("BARRIER") || name.contains("COMMAND_BLOCK")
                || name.contains("STRUCTURE_") || name.equals("JIGSAW") || name.equals("LIGHT");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxLevel() {
        return MAX_LEVEL;
    }
}
