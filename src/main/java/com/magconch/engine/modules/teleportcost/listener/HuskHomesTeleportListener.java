package com.magconch.engine.modules.teleportcost.listener;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.teleportcost.config.TeleportCostConfig;
import com.magconch.engine.modules.teleportcost.util.ExperienceUtil;
import com.magconch.engine.modules.teleportcost.util.TeleportCostCalculator;
import net.william278.huskhomes.event.RandomTeleportEvent;
import net.william278.huskhomes.event.TeleportBackEvent;
import net.william278.huskhomes.event.TeleportEvent;
import net.william278.huskhomes.event.TeleportWarmupEvent;
import net.william278.huskhomes.position.Position;
import net.william278.huskhomes.teleport.Target;
import net.william278.huskhomes.teleport.Teleport;
import net.william278.huskhomes.teleport.Teleportable;
import net.william278.huskhomes.teleport.TimedTeleport;
import net.william278.huskhomes.teleport.Username;
import net.william278.huskhomes.user.BukkitUser;
import net.william278.huskhomes.user.OnlineUser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * HuskHomes 传送生命周期双阶段无状态监听器
 * <p>
 * 阶段 1 (TeleportWarmupEvent): 第 0 秒预检经验余额，不足立即打断前摇并提示；
 * 阶段 2 (TeleportEvent / TeleportBackEvent / RandomTeleportEvent): 传送落地瞬间二次校验并原子扣费。
 */
public class HuskHomesTeleportListener implements Listener {

    // ==========================================
    // 常量与依赖
    // ==========================================

    private static final String BYPASS_PERMISSION = "magconch.teleportcost.bypass";

    private final MagconchEngine plugin;
    private final TeleportCostConfig config;

    public HuskHomesTeleportListener(MagconchEngine plugin, TeleportCostConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ==========================================
    // 阶段 1：前摇第 0 秒预检拦截
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWarmup(TeleportWarmupEvent event) {
        Teleport teleport = event.getTimedTeleport();
        Player teleporter = resolveTeleporter(teleport);
        if (teleporter == null || shouldBypass(teleporter, teleport)) {
            return;
        }

        int cost = computeCost(teleporter, teleport);
        if (cost <= 0) {
            return;
        }

        int currentExp = ExperienceUtil.getTotalExperience(teleporter);
        if (currentExp < cost) {
            event.setCancelled(true);
            notifyInsufficientExp(teleporter, cost, currentExp);
            return;
        }

        // 前摇第 0 秒发送预计消耗提示，玩家可在倒计时期间移动取消
        plugin.getLanguageManager().sendMessage(teleporter, "teleport-cost.warmup_notice", "{cost}", cost);
    }

    // ==========================================
    // 阶段 2：传送执行瞬间二次校验与原子扣费
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(TeleportEvent event) {
        Teleport teleport = event.getTeleport();
        Player teleporter = resolveTeleporter(teleport);
        if (teleporter == null || shouldBypass(teleporter, teleport)) {
            return;
        }

        int cost = computeCost(teleporter, teleport);
        if (cost <= 0) {
            return;
        }

        int currentExp = ExperienceUtil.getTotalExperience(teleporter);
        if (currentExp < cost) {
            event.setCancelled(true);
            notifyInsufficientExp(teleporter, cost, currentExp);
            return;
        }

        // 原子扣减总经验并刷新客户端等级与进度条
        ExperienceUtil.setTotalExperience(teleporter, currentExp - cost);

        // 仅在跳过前摇的秒传场景下补发扣费提示；常规有前摇场景已在第 0 秒提示，落地不再重复刷屏
        if (!hasWarmup(teleporter, teleport)) {
            plugin.getLanguageManager().sendMessage(teleporter, "teleport-cost.deducted", "{cost}", cost);
        }
    }

    /**
     * 兼容 HuskHomes 子类独立 HandlerList：/back 传送完成事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleportBack(TeleportBackEvent event) {
        onTeleport(event);
    }

    /**
     * 兼容 HuskHomes 子类独立 HandlerList：/rtp 随机传送完成事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRandomTeleport(RandomTeleportEvent event) {
        onTeleport(event);
    }

    // ==========================================
    // 内部解析與业务判定
    // ==========================================

    /**
     * 解析本次传送对应的本地 Bukkit 玩家实例
     */
    private Player resolveTeleporter(Teleport teleport) {
        if (teleport == null) {
            return null;
        }
        Teleportable teleportable = teleport.getTeleporter();
        Player player = null;
        if (teleportable instanceof BukkitUser bukkitUser) {
            player = bukkitUser.getPlayer();
        } else if (teleportable instanceof OnlineUser onlineUser) {
            player = Bukkit.getPlayer(onlineUser.getUuid());
        } else if (teleportable instanceof Username username) {
            player = Bukkit.getPlayerExact(username.getName());
        }
        return (player != null && player.isOnline()) ? player : null;
    }

    /**
     * 判定是否豁免扣费（OP、拥有豁免权限、或由 OP/管理员指令如 /tphere 发起的调度）
     */
    private boolean shouldBypass(Player teleporter, Teleport teleport) {
        if (teleporter.isOp() || teleporter.hasPermission(BYPASS_PERMISSION)) {
            return true;
        }

        OnlineUser executor = teleport.getExecutor();
        if (executor != null && !executor.getUuid().equals(teleporter.getUniqueId())) {
            if (executor.hasPermission(BYPASS_PERMISSION)) {
                return true;
            }
            Player executorPlayer = Bukkit.getPlayer(executor.getUuid());
            if (executorPlayer != null && (executorPlayer.isOp() || executorPlayer.hasPermission(BYPASS_PERMISSION))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据传送类型与起止坐标计算应扣经验点数
     */
    private int computeCost(Player teleporter, Teleport teleport) {
        Teleport.Type type = teleport.getType();
        if (type == Teleport.Type.RESPAWN) {
            return 0;
        }
        if (type == Teleport.Type.RANDOM_TELEPORT) {
            return Math.min(config.getRtpCost(), config.getMaxCost());
        }

        // TELEPORT 与 BACK 均按空间距离与跨界规则计费
        Location origin = teleporter.getLocation();
        World originBukkitWorld = origin.getWorld();
        if (originBukkitWorld == null) {
            return 0;
        }

        Target target = teleport.getTarget();
        if (target instanceof Position targetPos && targetPos.getWorld() != null) {
            String targetWorldName = targetPos.getWorld().getName();
            World.Environment targetEnv = resolveEnvironment(targetPos.getWorld());
            return TeleportCostCalculator.calculateDistanceCost(
                    origin.getX(), origin.getY(), origin.getZ(),
                    originBukkitWorld.getName(), originBukkitWorld.getEnvironment(),
                    targetPos.getX(), targetPos.getY(), targetPos.getZ(),
                    targetWorldName, targetEnv,
                    config
            );
        }

        if (target instanceof Username targetUser) {
            Player targetPlayer = "@s".equals(targetUser.getName())
                    ? teleporter
                    : Bukkit.getPlayerExact(targetUser.getName());
            if (targetPlayer == null) {
                targetPlayer = Bukkit.getPlayer(targetUser.getName());
            }
            if (targetPlayer != null && targetPlayer.getWorld() != null) {
                Location targetLoc = targetPlayer.getLocation();
                World targetBukkitWorld = targetLoc.getWorld();
                return TeleportCostCalculator.calculateDistanceCost(
                        origin.getX(), origin.getY(), origin.getZ(),
                        originBukkitWorld.getName(), originBukkitWorld.getEnvironment(),
                        targetLoc.getX(), targetLoc.getY(), targetLoc.getZ(),
                        targetBukkitWorld.getName(), targetBukkitWorld.getEnvironment(),
                        config
                );
            }
        }

        return Math.min(config.getBaseCost(), config.getMaxCost());
    }

    /**
     * 解析 HuskHomes 世界的 Bukkit 维度环境类型（优先取本地已加载世界，回退至 HuskHomes 元数据）
     */
    private World.Environment resolveEnvironment(net.william278.huskhomes.position.World huskWorld) {
        World bukkitWorld = Bukkit.getWorld(huskWorld.getName());
        if (bukkitWorld != null) {
            return bukkitWorld.getEnvironment();
        }
        return switch (huskWorld.getEnvironment()) {
            case NETHER -> World.Environment.NETHER;
            case THE_END -> World.Environment.THE_END;
            case CUSTOM -> World.Environment.CUSTOM;
            default -> World.Environment.NORMAL;
        };
    }

    /**
     * 判定本次传送是否经历了前摇倒计时阶段
     */
    private boolean hasWarmup(Player teleporter, Teleport teleport) {
        if (!(teleport instanceof TimedTeleport)) {
            return false;
        }
        if (teleporter.hasPermission(TimedTeleport.BYPASS_PERMISSION)) {
            return false;
        }
        return teleport.getPlugin().getSettings().getGeneral().getTeleportWarmupTime() > 0;
    }

    /**
     * 发送经验不足拦截提示
     */
    private void notifyInsufficientExp(Player player, int requiredCost, int currentExp) {
        plugin.getLanguageManager().sendMessage(
                player,
                "teleport-cost.insufficient",
                "{cost}", requiredCost,
                "{current}", currentExp
        );
    }
}
