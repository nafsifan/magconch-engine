package com.magconch.engine.modules.mark;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.mark.command.MarkCommand;
import com.magconch.engine.modules.mark.config.MarkConfig;
import com.magconch.engine.modules.mark.listener.MarkPickListener;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 标点模块核心生命周期与状态调度
 * <p>
 * 遵循纯内存无 NBT 污染模型，提供实体发光与方块高亮描边渲染、单人单标记排他性置换及空间立体声音效。
 */
public class MarkModule implements EngineModule {

    private static final String MODULE_ID = "mark";
    private static final String PERMISSION_USE = "magconch.use.mark";

    private final MagconchEngine plugin;
    private final MarkConfig config = new MarkConfig();

    private boolean enabled;
    private MarkPickListener pickListener;
    private MarkCommand markCommand;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveMark> activeMarks = new ConcurrentHashMap<>();
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

    private record ActiveMark(BukkitTask task, Runnable cleanup, Entity markerEntity) {}

    public MarkModule(MagconchEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        config.load(plugin.getConfig());

        if (pickListener == null) {
            pickListener = new MarkPickListener(this);
        }
        plugin.getServer().getPluginManager().registerEvents(pickListener, plugin);

        if (markCommand == null) {
            markCommand = new MarkCommand(plugin, this);
            plugin.getMainCommand().registerSubCommand(markCommand);
        }

        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;

        if (pickListener != null) {
            HandlerList.unregisterAll(pickListener);
        }

        clearAllMarks();
        cooldowns.clear();
        disabledPlayers.clear();
    }

    @Override
    public void onReload() {
        config.load(plugin.getConfig());
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 为指定玩家触发默认距离标点
     *
     * @param player 发起标记的玩家
     * @return true 为成功标点，false 为未命中目标或冷却中
     */
    public boolean performMark(Player player) {
        return performMark(player, config.getRange());
    }

    /**
     * 为指定玩家触发指定距离标点
     *
     * @param player   发起标记的玩家
     * @param maxRange 最大探测距离 (格)
     * @return true 为成功标点，false 为未命中目标或冷却中
     */
    public boolean performMark(Player player, double maxRange) {
        if (!enabled || player == null || !player.isOnline() || !player.hasPermission(PERMISSION_USE) || !isPlayerEnabled(player.getUniqueId())) {
            return false;
        }

        long now = System.currentTimeMillis();
        long lastTime = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long cdMillis = (long) (config.getCooldown() * 1000L);
        if (now - lastTime < cdMillis) {
            return false;
        }

        RayTraceResult result = player.getWorld().rayTrace(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                maxRange,
                FluidCollisionMode.NEVER,
                false,
                0.3,
                e -> {
                    if (e.equals(player) || e instanceof Player) {
                        return false;
                    }
                    if (e instanceof Shulker shulker && !shulker.hasAI()) {
                        return false;
                    }
                    return e instanceof Item || e instanceof LivingEntity;
                }
        );

        if (result == null) {
            return false;
        }

        Entity hitEntity = result.getHitEntity();
        Block hitBlock = result.getHitBlock();

        if (hitEntity == null && (hitBlock == null || hitBlock.getType().isAir())) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        cooldowns.put(uuid, now);
        clearPlayerMark(uuid);

        Location soundLocation;

        if (hitEntity != null) {
            soundLocation = hitEntity.getLocation();
            boolean wasGlowing = hitEntity.isGlowing();
            hitEntity.setGlowing(true);

            registerActiveMark(uuid, () -> {
                if (hitEntity.isValid() && !wasGlowing) {
                    hitEntity.setGlowing(false);
                }
            }, null);
        } else {
            soundLocation = hitBlock.getLocation().add(0.5, 0.5, 0.5);
            Location spawnLoc = hitBlock.getLocation();

            Shulker marker = hitBlock.getWorld().spawn(spawnLoc, Shulker.class, s -> {
                s.setAI(false);
                s.setInvisible(true);
                s.setInvulnerable(true);
                s.setSilent(true);
                s.setCollidable(false);
                s.setGlowing(true);
                s.setPersistent(false);
            });

            for (UUID disabledId : disabledPlayers) {
                Player dp = plugin.getServer().getPlayer(disabledId);
                if (dp != null && dp.isOnline()) {
                    dp.hideEntity(plugin, marker);
                }
            }

            registerActiveMark(uuid, () -> {
                if (marker.isValid()) {
                    marker.remove();
                }
            }, marker);
        }

        double maxDistSq = 32.0 * 32.0;
        boolean originHeard = false;

        for (Player online : soundLocation.getWorld().getPlayers()) {
            if (!isPlayerEnabled(online.getUniqueId())) {
                continue;
            }
            if (online.getLocation().distanceSquared(soundLocation) <= maxDistSq) {
                online.playSound(
                        soundLocation,
                        Sound.BLOCK_NOTE_BLOCK_BELL,
                        SoundCategory.PLAYERS,
                        2.5f,
                        1.5f
                );
                if (online.equals(player)) {
                    originHeard = true;
                }
            }
        }

        if (!originHeard) {
            player.playSound(
                    player.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_BELL,
                    SoundCategory.PLAYERS,
                    1.2f,
                    1.5f
            );
        }

        return true;
    }

    private void registerActiveMark(UUID uuid, Runnable cleanup, Entity markerEntity) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            cleanup.run();
            activeMarks.remove(uuid);
        }, config.getDuration() * 20L);

        activeMarks.put(uuid, new ActiveMark(task, cleanup, markerEntity));
    }

    public void clearPlayerMark(UUID uuid) {
        ActiveMark mark = activeMarks.remove(uuid);
        if (mark != null) {
            cancelMark(mark);
        }
    }

    public void onPlayerQuit(UUID uuid) {
        clearPlayerMark(uuid);
        cooldowns.remove(uuid);
        disabledPlayers.remove(uuid);
    }

    public boolean toggleMark(UUID uuid) {
        Player p = plugin.getServer().getPlayer(uuid);
        if (disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            if (p != null && p.isOnline()) {
                for (ActiveMark am : activeMarks.values()) {
                    if (am.markerEntity() != null && am.markerEntity().isValid()) {
                        p.showEntity(plugin, am.markerEntity());
                    }
                }
            }
            return true;
        } else {
            disabledPlayers.add(uuid);
            if (p != null && p.isOnline()) {
                for (ActiveMark am : activeMarks.values()) {
                    if (am.markerEntity() != null && am.markerEntity().isValid()) {
                        p.hideEntity(plugin, am.markerEntity());
                    }
                }
            }
            return false;
        }
    }

    public boolean isPlayerEnabled(UUID uuid) {
        return !disabledPlayers.contains(uuid);
    }

    public void clearAllMarks() {
        for (ActiveMark mark : activeMarks.values()) {
            cancelMark(mark);
        }
        activeMarks.clear();
    }

    private void cancelMark(ActiveMark mark) {
        try {
            mark.task().cancel();
        } catch (Exception ignored) {
        }
        try {
            mark.cleanup().run();
        } catch (Exception ignored) {
        }
    }

    public MarkConfig getConfig() {
        return config;
    }
}
