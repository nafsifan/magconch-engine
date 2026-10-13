package com.magconch.engine.modules.teleportcost.util;

import com.magconch.engine.modules.teleportcost.config.TeleportCostConfig;
import org.bukkit.World;

/**
 * 传送距离与跨维度费用计算工具
 * <p>
 * 负责同世界直线距离测算、跨维度等效空间投影（下界 8:1、末地 1:1）、
 * 开方倍率衰减以及对称跨界附加费与封顶计算。
 */
public final class TeleportCostCalculator {

    private static final double NETHER_PROJECTION_SCALE = 8.0;

    private TeleportCostCalculator() {
        // 工具类私有构造
    }

    // ==========================================
    // 核心计费模型
    // ==========================================

    /**
     * 计算常规传送（TELEPORT / BACK）所需的经验点数
     *
     * @param originX     起点 X
     * @param originY     起点 Y
     * @param originZ     起点 Z
     * @param originWorld 起点世界名称
     * @param originEnv   起点维度环境
     * @param targetX     终点 X
     * @param targetY     终点 Y
     * @param targetZ     终点 Z
     * @param targetWorld 终点世界名称
     * @param targetEnv   终点维度环境
     * @param config      模块配置
     * @return 最终应扣除的经验点数（受封顶上限约束）
     */
    public static int calculateDistanceCost(
            double originX, double originY, double originZ, String originWorld, World.Environment originEnv,
            double targetX, double targetY, double targetZ, String targetWorld, World.Environment targetEnv,
            TeleportCostConfig config
    ) {
        if (config == null) {
            return 0;
        }

        boolean sameWorld = originWorld != null && originWorld.equalsIgnoreCase(targetWorld);

        double x1 = originX;
        double z1 = originZ;
        double x2 = targetX;
        double z2 = targetZ;
        int extraCost = 0;

        // 跨世界传送时，执行下界 8:1 等效主世界坐标投影与对称跨界附加费判定
        if (!sameWorld) {
            if (originEnv == World.Environment.NETHER) {
                x1 *= NETHER_PROJECTION_SCALE;
                z1 *= NETHER_PROJECTION_SCALE;
            }
            if (targetEnv == World.Environment.NETHER) {
                x2 *= NETHER_PROJECTION_SCALE;
                z2 *= NETHER_PROJECTION_SCALE;
            }

            if (originEnv == World.Environment.THE_END || targetEnv == World.Environment.THE_END) {
                extraCost = config.getTheEndExtraCost();
            } else if (originEnv == World.Environment.NETHER || targetEnv == World.Environment.NETHER) {
                extraCost = config.getNetherExtraCost();
            }
        }

        double dx = x1 - x2;
        double dy = originY - targetY;
        double dz = z1 - z2;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        int distanceCost = (int) Math.floor(config.getBaseCost() + config.getDistanceRate() * Math.sqrt(distance));
        int totalCost = distanceCost + extraCost;

        return Math.max(0, Math.min(totalCost, config.getMaxCost()));
    }
}
