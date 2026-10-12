package com.magconch.engine.modules.flight;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;
import com.magconch.engine.modules.flight.command.FlightCommand;
import com.magconch.engine.modules.flight.config.FlightConfig;
import com.magconch.engine.modules.flight.listener.FlightDamageListener;
import com.magconch.engine.modules.flight.listener.FlightToggleListener;
import com.magconch.engine.modules.flight.task.FlightHungerTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.lang.reflect.Method;

/**
 * 生存饥饿飞行模块生命周期实现
 * <p>
 * 遵循原版生存法则，提供飞行饱食度消耗、断飞保护与摔落伤害修复，具备全服务端跨端兼容能力。
 */
public class FlightModule implements EngineModule {

    // ==========================================
    // 常量与状态字段
    // ==========================================

    private static final String MODULE_ID = "flight";

    private final MagconchEngine plugin;
    private final FlightConfig config = new FlightConfig();

    private boolean enabled;
    private FlightToggleListener toggleListener;
    private FlightDamageListener damageListener;
    private FlightHungerTask hungerTask;
    private FlightCommand flightCommand;

    // 现代服务端 (Paper / Purpur / magconch-core) 原生反射缓存
    private static Method setFlyingFallDamageMethod;
    private static Object triStateTrueValue;
    private static boolean probed;

    public FlightModule(MagconchEngine plugin) {
        this.plugin = plugin;
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
        // 1. 加载模块配置
        config.load(plugin.getConfig());

        // 2. 实例化指令处理器并挂载
        if (flightCommand == null) {
            flightCommand = new FlightCommand(plugin, this, config);
            plugin.getMainCommand().registerSubCommand(flightCommand);

            PluginCommand flyAlias = plugin.getCommand("fly");
            if (flyAlias != null) {
                flyAlias.setExecutor(flightCommand);
                flyAlias.setTabCompleter(flightCommand);
            }
        }

        // 3. 注册事件监听器
        if (toggleListener == null) {
            toggleListener = new FlightToggleListener(plugin, config);
            plugin.getServer().getPluginManager().registerEvents(toggleListener, plugin);
        }
        if (damageListener == null) {
            damageListener = new FlightDamageListener();
            plugin.getServer().getPluginManager().registerEvents(damageListener, plugin);
        }

        // 4. 启动单一定时器任务 (每 20 ticks 检查一次)
        startHungerTask();
    }

    @Override
    public void onDisable() {
        // 1. 停止定时任务并清理内存
        if (hungerTask != null) {
            hungerTask.cancel();
            hungerTask.clear();
            hungerTask = null;
        }

        // 2. 注销事件监听器
        if (toggleListener != null) {
            HandlerList.unregisterAll(toggleListener);
            toggleListener = null;
        }
        if (damageListener != null) {
            HandlerList.unregisterAll(damageListener);
            damageListener = null;
        }
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

    // ==========================================
    // 内部调度与任务管理
    // ==========================================

    private void startHungerTask() {
        if (hungerTask != null) {
            hungerTask.cancel();
            hungerTask.clear();
        }
        hungerTask = new FlightHungerTask(plugin, this, config);
        hungerTask.runTaskTimer(plugin, 20L, 20L);
    }

    // ==========================================
    // 跨端环境特性探测与适配
    // ==========================================

    /**
     * 启用玩家飞行时的摔落伤害标志（安全适配 Paper/Purpur/magconch-core）
     *
     * @param player 目标玩家
     */
    public static void enableFlyingFallDamage(Player player) {
        if (player == null) {
            return;
        }

        if (!probed) {
            probeFallDamageApi();
        }

        if (setFlyingFallDamageMethod != null && triStateTrueValue != null) {
            try {
                setFlyingFallDamageMethod.invoke(player, triStateTrueValue);
            } catch (Throwable ignored) {
                // 调用失败静默降级，由 FlightDamageListener 进行事件兜底
            }
        }
    }

    private static synchronized void probeFallDamageApi() {
        if (probed) {
            return;
        }
        probed = true;
        try {
            for (Method method : Player.class.getMethods()) {
                if ("setFlyingFallDamage".equals(method.getName()) && method.getParameterCount() == 1) {
                    Class<?> paramType = method.getParameterTypes()[0];
                    if (paramType.isEnum()) {
                        for (Object constant : paramType.getEnumConstants()) {
                            if ("TRUE".equalsIgnoreCase(((Enum<?>) constant).name())) {
                                setFlyingFallDamageMethod = method;
                                triStateTrueValue = constant;
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // 纯 Spigot 运行时无此方法，不产生任何副作用
        }
    }

    // ==========================================
    // Getters
    // ==========================================

    public FlightConfig getConfig() {
        return config;
    }
}
