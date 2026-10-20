package com.magconch.engine;

import com.magconch.engine.command.MainCommand;
import com.magconch.engine.core.ConfigManager;
import com.magconch.engine.core.LanguageManager;
import com.magconch.engine.core.ModuleManager;
import com.magconch.engine.modules.compresseditem.CompressedItemModule;
import com.magconch.engine.modules.creaturebundle.CreatureBundleModule;
import com.magconch.engine.modules.expstore.ExpStoreModule;
import com.magconch.engine.modules.flight.FlightModule;
import com.magconch.engine.modules.harvester.HarvesterModule;
import com.magconch.engine.modules.teleportcost.TeleportCostModule;
import com.magconch.engine.modules.showitem.ShowItemModule;
import com.magconch.engine.modules.trash.TrashModule;
import com.magconch.engine.modules.undo.BlockUndoModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Magconch Engine 核心主类
 * <p>
 * 负责引擎整体生命周期调度、服务管理组件实例化及微内核架构总控。
 */
public class MagconchEngine extends JavaPlugin {

    // ==========================================
    // 单例与核心组件
    // ==========================================

    private static MagconchEngine instance;

    private ConfigManager configManager;
    private LanguageManager languageManager;
    private ModuleManager moduleManager;
    private MainCommand mainCommand;

    // ==========================================
    // 生命周期
    // ==========================================

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        getLogger().info("=========================================");
        getLogger().info("   Magconch Engine 正在启动...");
        getLogger().info("=========================================");

        try {
            // 1. 初始化核心配置与静态资源
            this.configManager = new ConfigManager(this);
            this.configManager.load();

            // 2. 初始化多语言服务
            this.languageManager = new LanguageManager(this);
            this.languageManager.load();

            // 3. 初始化微内核模块管理器
            this.moduleManager = new ModuleManager(this);

            // 4. 注册主命令及 Tab 自动补全
            this.mainCommand = new MainCommand(this);
            PluginCommand pluginCommand = getCommand("magconch");
            if (pluginCommand != null) {
                pluginCommand.setExecutor(this.mainCommand);
                pluginCommand.setTabCompleter(this.mainCommand);
            } else {
                getLogger().severe("无法找到 plugin.yml 中声明的 magconch 主命令，请检查配置！");
            }

            // 5. 挂载子系统模块
            this.moduleManager.registerModule(new BlockUndoModule(this));
            this.moduleManager.registerModule(new FlightModule(this));
            this.moduleManager.registerModule(new TeleportCostModule(this));
            this.moduleManager.registerModule(new TrashModule(this));
            this.moduleManager.registerModule(new ShowItemModule(this));
            this.moduleManager.registerModule(new CreatureBundleModule(this));
            this.moduleManager.registerModule(new ExpStoreModule(this));
            this.moduleManager.registerModule(new CompressedItemModule(this));
            this.moduleManager.registerModule(new HarvesterModule(this));

            // 6. 统一调度启用所有挂载模块
            this.moduleManager.enableAll();

            long cost = System.currentTimeMillis() - startTime;
            getLogger().info("Magconch Engine 已成功就绪！耗时: " + cost + "ms");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Magconch Engine 启动过程中发生严重异常: ", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("正在停用 Magconch Engine 核心引擎...");

        // 1. 安全停用并卸载所有运行中的模块
        if (moduleManager != null) {
            moduleManager.disableAll();
        }

        getLogger().info("Magconch Engine 已安全退出。");
        instance = null;
    }

    // ==========================================
    // 全局访问器 (Getters)
    // ==========================================

    public static MagconchEngine getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public MainCommand getMainCommand() {
        return mainCommand;
    }
}
