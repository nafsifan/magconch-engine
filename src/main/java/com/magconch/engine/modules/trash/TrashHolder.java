package com.magconch.engine.modules.trash;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 垃圾桶虚拟容器标识 Holder
 * <p>
 * 用于标记当前 Inventory 为垃圾桶界面，防止与其它界面或容器逻辑混淆。
 */
public class TrashHolder implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
