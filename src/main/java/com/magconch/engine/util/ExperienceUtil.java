package com.magconch.engine.util;

import org.bukkit.entity.Player;

/**
 * 原版等级与总经验点数高保真换算工具
 * <p>
 * 绕过 Bukkit 原生 getTotalExperience() 与 giveExp() 的历史同步缺陷，
 * 基于 Minecraft 原版分段二次方程双向精确计算并原子刷新玩家等级与进度条。
 */
public final class ExperienceUtil {

    private ExperienceUtil() {
        // 工具类私有构造
    }

    // ==========================================
    // 玩家经验读取与原子写入
    // ==========================================

    /**
     * 精确计算玩家当前持有的真实总经验点数
     *
     * @param player 目标玩家
     * @return 当前真实总经验点数
     */
    public static int getTotalExperience(Player player) {
        if (player == null) {
            return 0;
        }
        int level = Math.max(0, player.getLevel());
        int expAtLevel = getExpAtLevel(level);
        int expInBar = Math.round(getExpToNext(level) * Math.max(0.0f, Math.min(1.0f, player.getExp())));
        long total = (long) expAtLevel + expInBar;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * 原子设置玩家的总经验点数，并同步更新等级数字与经验条进度
     *
     * @param player   目标玩家
     * @param totalExp 新总经验点数
     */
    public static void setTotalExperience(Player player, int totalExp) {
        if (player == null) {
            return;
        }

        int targetExp = Math.max(0, totalExp);

        // 先清零旧状态，防止客户端 UI 残影
        player.setExp(0.0f);
        player.setLevel(0);
        player.setTotalExperience(0);

        if (targetExp == 0) {
            return;
        }

        // 反推目标总经验对应的等级与当前级剩余点数
        int newLevel = findLevelForExp(targetExp);
        int remainder = targetExp - getExpAtLevel(newLevel);
        int expToNext = getExpToNext(newLevel);
        float progress = expToNext > 0
                ? Math.max(0.0f, Math.min(0.9999f, (float) remainder / (float) expToNext))
                : 0.0f;

        player.setTotalExperience(targetExp);
        player.setLevel(newLevel);
        player.setExp(progress);
    }

    // ==========================================
    // 原版分段数学公式
    // ==========================================

    /**
     * 计算达到指定等级所需的累计总经验点数
     * <ul>
     *     <li>0 ~ 16 级: L^2 + 6L</li>
     *     <li>17 ~ 31 级: 2.5L^2 - 40.5L + 360</li>
     *     <li>32+ 级: 4.5L^2 - 162.5L + 2220</li>
     * </ul>
     *
     * @param level 等级
     * @return 累计总经验点数
     */
    public static int getExpAtLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        long l = level;
        long exp;
        if (level <= 16) {
            exp = l * l + 6L * l;
        } else if (level <= 31) {
            // (5L^2 - 81L + 720) / 2，分子恒为偶数，整数运算无浮点误差
            exp = (5L * l * l - 81L * l + 720L) / 2L;
        } else {
            // (9L^2 - 325L + 4440) / 2，分子恒为偶数，整数运算无浮点误差
            exp = (9L * l * l - 325L * l + 4440L) / 2L;
        }
        return (int) Math.min(Integer.MAX_VALUE, exp);
    }

    /**
     * 计算从指定等级升至下一级所需的经验点数
     * <ul>
     *     <li>0 ~ 15 级: 2L + 7</li>
     *     <li>16 ~ 30 级: 5L - 38</li>
     *     <li>31+ 级: 9L - 158</li>
     * </ul>
     *
     * @param level 当前等级
     * @return 升至下一级所需点数
     */
    public static int getExpToNext(int level) {
        if (level < 0) {
            return 7;
        }
        if (level <= 15) {
            return 2 * level + 7;
        }
        if (level <= 30) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }

    /**
     * 二分查找给定总经验点数对应的等级（最高支持至 Integer.MAX_VALUE 约 21863 级）
     */
    private static int findLevelForExp(int totalExp) {
        int low = 0;
        int high = 21863;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (getExpAtLevel(mid) <= totalExp) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }
}
