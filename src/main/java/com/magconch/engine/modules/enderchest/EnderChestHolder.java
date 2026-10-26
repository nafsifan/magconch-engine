package com.magconch.engine.modules.enderchest;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * 扩展末影箱容器持有者
 * <p>
 * 绑定末影箱归属玩家身份，供界面关闭及交互监听器准确辨识会话。
 */
public class EnderChestHolder implements InventoryHolder {

    // ==========================================
    // 状态字段
    // ==========================================

    private final UUID ownerUuid;
    private final String ownerName;
    private Inventory inventory;

    public EnderChestHolder(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
