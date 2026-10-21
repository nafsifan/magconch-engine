package com.magconch.engine.modules.compresseditem.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 压缩物品模块精炼配置与方块合法性过滤
 * <p>
 * 内建 9 级最大上限并使用静态预编译 EnumSet 纳秒级排除容器、技术方块与作物，
 * 彻底消除高频字符串匹配开销。
 */
public class CompressedItemConfig {

    public static final int MAX_LEVEL = 9;

    private static final Set<Material> INHERENTLY_DANGEROUS = EnumSet.noneOf(Material.class);

    static {
        // 静态预编译固有危险容器、技术方块与非实心作物，一次性构建位图索引
        for (Material mat : Material.values()) {
            if (!mat.isBlock()) {
                continue;
            }
            String name = mat.name();

            // 1. 各类存储容器与方块实体
            if (name.endsWith("SHULKER_BOX") || name.contains("CHEST") || name.equals("BARREL")
                    || name.equals("HOPPER") || name.equals("DISPENSER") || name.equals("DROPPER")
                    || name.equals("CRAFTER") || name.equals("FURNACE") || name.equals("BLAST_FURNACE")
                    || name.equals("SMOKER") || name.contains("BEEHIVE") || name.contains("BEE_NEST")
                    || name.equals("BEACON") || name.equals("SPAWNER") || name.equals("ENCHANTING_TABLE")
                    || name.equals("DECORATED_POT") || name.equals("CHISELED_BOOKSHELF")
                    || name.equals("JUKEBOX") || name.equals("LECTERN") || name.equals("BREWING_STAND")
                    || name.contains("CAMPFIRE")) {
                INHERENTLY_DANGEROUS.add(mat);
                continue;
            }

            // 2. 技术与创造不可破方块
            if (name.equals("BEDROCK") || name.equals("BARRIER") || name.contains("COMMAND_BLOCK")
                    || name.contains("STRUCTURE_") || name.equals("JIGSAW") || name.equals("LIGHT")) {
                INHERENTLY_DANGEROUS.add(mat);
                continue;
            }

            // 3. 田间作物与附着类非完整方块（作物统一由其原版聚合块如干草块承接压缩）
            if (name.equals("WHEAT") || name.equals("CARROTS") || name.equals("POTATOES")
                    || name.equals("BEETROOTS") || name.contains("STEM") || name.equals("NETHER_WART")
                    || name.equals("COCOA") || name.equals("SWEET_BERRY_BUSH")) {
                INHERENTLY_DANGEROUS.add(mat);
            }
        }
    }

    private boolean enabled = true;
    private final Set<Material> blacklist = EnumSet.noneOf(Material.class);

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

        // 纳秒级位运算快速排查
        if (blacklist.contains(material) || INHERENTLY_DANGEROUS.contains(material)) {
            return false;
        }

        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxLevel() {
        return MAX_LEVEL;
    }
}
