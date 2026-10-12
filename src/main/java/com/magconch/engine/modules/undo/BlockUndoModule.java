package com.magconch.engine.modules.undo;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.undo.command.UndoCommand;
import com.magconch.engine.modules.undo.listener.UndoBlockListener;
import com.magconch.engine.modules.undo.model.PlacementRecord;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 方块撤回功能模块实现
 * <p>
 * 纯内存管理、100% 物品组件保真，根治 NBT 泄漏与容器回滚风险。
 */
public class BlockUndoModule implements EngineModule {

    // ==========================================
    // 常量与状态字段
    // ==========================================

    private static final String MODULE_ID = "block-undo";

    private final MagconchEngine plugin;
    private final NamespacedKey disabledPdcKey;
    private final Map<Location, PlacementRecord> recordMap = new ConcurrentHashMap<>();
    private final List<Pattern> blacklistPatterns = new ArrayList<>();

    private boolean enabled;
    private long timeoutMillis = 2000L;
    private boolean checkPermission = true;

    private UndoBlockListener listener;
    private BukkitTask cleanTask;

    public BlockUndoModule(MagconchEngine plugin) {
        this.plugin = plugin;
        this.disabledPdcKey = new NamespacedKey(plugin, "undo_disabled");
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
        loadConfigValues();

        // 注册事件监听器
        if (listener == null) {
            listener = new UndoBlockListener(plugin, this);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }

        // 挂载子命令至主命令总线
        plugin.getMainCommand().registerSubCommand(new UndoCommand(plugin, this));

        // 启动主线程定时清理任务 (每秒检查一次超时记录)
        startCleanupTask();
    }

    @Override
    public void onDisable() {
        // 注销定时任务
        if (cleanTask != null) {
            cleanTask.cancel();
            cleanTask = null;
        }

        // 注销监听器
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }

        // 清空内存记录
        recordMap.clear();
        blacklistPatterns.clear();
    }

    @Override
    public void onReload() {
        loadConfigValues();
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
    // 配置解析与任务调度
    // ==========================================

    private void loadConfigValues() {
        FileConfiguration config = plugin.getConfig();
        String path = "modules.block-undo.";

        this.enabled = config.getBoolean(path + "enabled", true);
        int timeoutSec = config.getInt(path + "timeout", 2);
        this.timeoutMillis = timeoutSec * 1000L;
        this.checkPermission = config.getBoolean(path + "check-permission", true);

        // 加载黑名单材质正则匹配项
        blacklistPatterns.clear();
        List<String> list = config.getStringList(path + "blacklist-materials");
        for (String regex : list) {
            try {
                blacklistPatterns.add(Pattern.compile(regex));
            } catch (Exception e) {
                plugin.getLogger().warning("无效的黑名单材质正则模式: " + regex);
            }
        }
    }

    private void startCleanupTask() {
        if (cleanTask != null) {
            cleanTask.cancel();
        }

        // 每秒 (20 ticks) 扫描一次并移除超时快照
        this.cleanTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (recordMap.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<Location, PlacementRecord>> it = recordMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Location, PlacementRecord> entry = it.next();
                if (now - entry.getValue().getTimestamp() > timeoutMillis) {
                    it.remove();
                }
            }
        }, 20L, 20L);
    }

    // ==========================================
    // 业务判定与安全工具
    // ==========================================

    /**
     * 判断指定材质名是否命中黑名单
     */
    public boolean isBlacklisted(String materialName) {
        for (Pattern pattern : blacklistPatterns) {
            if (pattern.matcher(materialName).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判定指定玩家是否开启撤回功能并具备权限
     */
    public boolean isUndoEnabledFor(Player player) {
        if (checkPermission && !player.hasPermission("magconch.use.undo")) {
            return false;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        return !pdc.has(disabledPdcKey, PersistentDataType.BYTE);
    }

    /**
     * 设置玩家的方块撤回功能开关
     */
    public void setUndoEnabledFor(Player player, boolean active) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (active) {
            pdc.remove(disabledPdcKey);
        } else {
            pdc.set(disabledPdcKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    // ==========================================
    // Getter
    // ==========================================

    public Map<Location, PlacementRecord> getRecordMap() {
        return recordMap;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }
}
