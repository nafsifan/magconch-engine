package com.magconch.engine.modules.enderchest;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 扩展末影箱数据与会话管理器
 * <p>
 * 负责 54 格末影箱会话生命周期调度、原版末影箱无损数据迁移与 Data Components 原生字节流持久化。
 */
public class EnderChestManager {

    // ==========================================
    // 常量与状态字段
    // ==========================================

    private static final int CHEST_SIZE = 54;
    private static final int VANILLA_SIZE = 27;

    private final MagconchEngine plugin;
    private final File dataDirectory;
    private final Map<UUID, Inventory> activeInventories = new ConcurrentHashMap<>();

    public EnderChestManager(MagconchEngine plugin) {
        this.plugin = plugin;
        this.dataDirectory = new File(plugin.getDataFolder(), "enderchest");
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) {
            plugin.getLogger().warning("无法创建末影箱数据存储目录: " + dataDirectory.getAbsolutePath());
        }
    }

    // ==========================================
    // 会话开启与数据加载
    // ==========================================

    /**
     * 为查看者打开目标玩家的 54 格末影箱
     *
     * @param viewer 查看者
     * @param ownerUuid 末影箱归属者 UUID
     * @param ownerName 末影箱归属者玩家名
     */
    public void openEnderChest(Player viewer, UUID ownerUuid, String ownerName) {
        if (viewer == null || ownerUuid == null) {
            return;
        }

        // 1. 若内存中已存在活跃会话，直接复用以保持多端实时同步
        Inventory active = activeInventories.get(ownerUuid);
        if (active != null) {
            viewer.openInventory(active);
            return;
        }

        // 2. 加载或初始化 54 格物品数组
        ItemStack[] contents = loadOrMigrate(ownerUuid);

        // 3. 构造标题组件
        boolean isSelf = viewer.getUniqueId().equals(ownerUuid);
        String titleTemplate = isSelf
                ? plugin.getLanguageManager().getMessage("modules.enderchest.title_self")
                : plugin.getLanguageManager().getMessage("modules.enderchest.title_other", "{player}", ownerName);
        Component title = LegacyComponentSerializer.legacySection().deserialize(TextUtil.colorize(titleTemplate));

        // 4. 创建 54 格 Inventory 实例并绑定 Holder
        EnderChestHolder holder = new EnderChestHolder(ownerUuid, ownerName);
        Inventory inventory = Bukkit.createInventory(holder, CHEST_SIZE, title);
        holder.setInventory(inventory);
        inventory.setContents(contents);

        // 5. 登记至内存会话映射表并打开界面
        activeInventories.put(ownerUuid, inventory);
        viewer.openInventory(inventory);
    }

    /**
     * 加载玩家末影箱数据；若首次开启且原版有物品，则自动执行安全平移迁移
     */
    private ItemStack[] loadOrMigrate(UUID ownerUuid) {
        File dataFile = getDataFile(ownerUuid);

        // 场景 A: 存在持久化文件，直接反序列化读取
        if (dataFile.exists()) {
            try {
                byte[] bytes = Files.readAllBytes(dataFile.toPath());
                ItemStack[] loaded = ItemStack.deserializeItemsFromBytes(bytes);
                if (loaded.length == CHEST_SIZE) {
                    return loaded;
                }
                return Arrays.copyOf(loaded, CHEST_SIZE);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "读取末影箱数据异常: " + ownerUuid, e);
                return new ItemStack[CHEST_SIZE];
            }
        }

        // 场景 B: 首次开启，检查在线玩家原版末影箱执行平移
        ItemStack[] initial = new ItemStack[CHEST_SIZE];
        Player onlinePlayer = Bukkit.getPlayer(ownerUuid);
        if (onlinePlayer != null) {
            Inventory vanilla = onlinePlayer.getEnderChest();
            ItemStack[] vanillaItems = vanilla.getContents();
            boolean hasVanillaItems = false;
            for (ItemStack item : vanillaItems) {
                if (item != null && !item.isEmpty()) {
                    hasVanillaItems = true;
                    break;
                }
            }

            if (hasVanillaItems) {
                // 1. 将原版 27 格复制至 54 格前半段
                int copyLength = Math.min(vanillaItems.length, VANILLA_SIZE);
                for (int i = 0; i < copyLength; i++) {
                    ItemStack item = vanillaItems[i];
                    initial[i] = (item != null) ? item.clone() : null;
                }

                // 2. 先安全落盘写盘确认
                boolean saved = saveToFile(ownerUuid, initial);
                if (saved) {
                    // 3. 落盘无异常后清空原版
                    vanilla.clear();
                    plugin.getLogger().info("已安全迁移玩家 " + onlinePlayer.getName() + " 的原版末影箱物品至 54 格扩展箱。");
                }
                return initial;
            }
        }

        // 场景 C: 原版为空或离线全新玩家，生成空文件
        saveToFile(ownerUuid, initial);
        return initial;
    }

    // ==========================================
    // 会话关闭与持久化写盘
    // ==========================================

    /**
     * 界面关闭时的调度处理
     *
     * @param holder 末影箱 Holder
     * @param inventory 关闭的容器
     */
    public void handleClose(EnderChestHolder holder, Inventory inventory) {
        if (holder == null || inventory == null) {
            return;
        }

        UUID ownerUuid = holder.getOwnerUuid();
        // 当前关闭者为最后一个查看者时，移除会话并触发写盘
        if (inventory.getViewers().size() <= 1) {
            activeInventories.remove(ownerUuid);
            ItemStack[] snapshot = inventory.getContents().clone();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveToFile(ownerUuid, snapshot));
        }
    }

    /**
     * 将物品数据原子写盘
     *
     * @param ownerUuid 玩家 UUID
     * @param contents 54 格物品数组
     * @return true 为写入成功
     */
    private boolean saveToFile(UUID ownerUuid, ItemStack[] contents) {
        File dataFile = getDataFile(ownerUuid);
        File tempFile = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");

        try {
            byte[] bytes = ItemStack.serializeItemsAsBytes(contents);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(bytes);
                fos.flush();
                fos.getFD().sync();
            }

            Files.move(tempFile.toPath(), dataFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "末影箱原子写盘失败: " + ownerUuid, e);
            if (tempFile.exists()) {
                tempFile.delete();
            }
            return false;
        }
    }

    /**
     * 插件停用时同步刷新所有活跃会话至磁盘
     */
    public void flushAll() {
        if (activeInventories.isEmpty()) {
            return;
        }

        plugin.getLogger().info("正在保存内存中的活跃末影箱数据...");
        for (Map.Entry<UUID, Inventory> entry : activeInventories.entrySet()) {
            UUID ownerUuid = entry.getKey();
            Inventory inv = entry.getValue();
            saveToFile(ownerUuid, inv.getContents());
        }
        activeInventories.clear();
        plugin.getLogger().info("所有末影箱数据已安全落盘。");
    }

    private File getDataFile(UUID uuid) {
        return new File(dataDirectory, uuid.toString() + ".dat");
    }
}
