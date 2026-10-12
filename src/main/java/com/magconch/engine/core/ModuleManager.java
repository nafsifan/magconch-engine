package com.magconch.engine.core;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.api.EngineModule;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * 核心模块注册与生命周期调度中心
 * <p>
 * 统一管理所有挂载至引擎的子系统模块，提供注册、批量启用、停用及安全重载。
 */
public class ModuleManager {

    // ==========================================
    // 依赖与状态
    // ==========================================

    private final MagconchEngine plugin;
    private final Map<String, EngineModule> modules = new LinkedHashMap<>();

    public ModuleManager(MagconchEngine plugin) {
        this.plugin = plugin;
    }

    // ==========================================
    // 模块注册与查询
    // ==========================================

    /**
     * 注册新的功能模块
     *
     * @param module 待注册模块实现
     */
    public synchronized void registerModule(EngineModule module) {
        if (module == null) {
            return;
        }

        String id = module.getId().toLowerCase();
        if (modules.containsKey(id)) {
            plugin.getLogger().warning("模块 [" + id + "] 重复注册，将覆盖原有模块实例。");
        }

        modules.put(id, module);
        plugin.getLogger().info("已注册核心模块: " + id);
    }

    /**
     * 注销指定模块（若处于运行中将先调用 onDisable）
     *
     * @param id 模块 ID
     */
    public synchronized void unregisterModule(String id) {
        if (id == null) {
            return;
        }

        EngineModule module = modules.remove(id.toLowerCase());
        if (module != null && module.isEnabled()) {
            try {
                module.onDisable();
                module.setEnabled(false);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "注销模块 [" + id + "] 时抛出异常: ", e);
            }
        }
    }

    /**
     * 根据模块 ID 获取对应实例
     *
     * @param id 模块 ID
     * @return 模块实例，若不存在返回 null
     */
    public EngineModule getModule(String id) {
        if (id == null) {
            return null;
        }
        return modules.get(id.toLowerCase());
    }

    /**
     * 获取当前所有已注册的模块集合
     *
     * @return 不可变模块集合
     */
    public Collection<EngineModule> getModules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    // ==========================================
    // 生命周期批量调度
    // ==========================================

    /**
     * 批量启用所有已注册的模块
     */
    public void enableAll() {
        for (EngineModule module : modules.values()) {
            if (!module.isEnabled()) {
                try {
                    module.onEnable();
                    module.setEnabled(true);
                    plugin.getLogger().info("模块 [" + module.getId() + "] 已成功启用。");
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "启用模块 [" + module.getId() + "] 时发生严重异常: ", e);
                }
            }
        }
    }

    /**
     * 批量停用所有处于运行状态的模块（逆序安全卸载）
     */
    public void disableAll() {
        for (EngineModule module : modules.values()) {
            if (module.isEnabled()) {
                try {
                    module.onDisable();
                    module.setEnabled(false);
                    plugin.getLogger().info("模块 [" + module.getId() + "] 已安全停用。");
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "停用模块 [" + module.getId() + "] 时发生异常: ", e);
                }
            }
        }
    }

    /**
     * 批量触发所有模块的重载生命周期
     */
    public void reloadAll() {
        for (EngineModule module : modules.values()) {
            if (module.isEnabled()) {
                try {
                    module.onReload();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "重载模块 [" + module.getId() + "] 时发生异常: ", e);
                }
            }
        }
    }
}
