package com.magconch.engine.modules.undo.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * 玩家方块放置记录快照模型
 * <p>
 * 纯内存轻量结构，精准保存原始物品堆叠组件与放置坐标状态。
 */
public class PlacementRecord {

    // ==========================================
    // 字段属性
    // ==========================================

    private final Location location;
    private final long timestamp;
    private final ItemStack item;

    public PlacementRecord(Location location, long timestamp, ItemStack item) {
        this.location = Objects.requireNonNull(location, "location 不能为 null");
        this.timestamp = timestamp;
        this.item = Objects.requireNonNull(item, "item 不能为 null");
    }

    // ==========================================
    // Getter
    // ==========================================

    public Location getLocation() {
        return location;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public ItemStack getItem() {
        return item;
    }
}
