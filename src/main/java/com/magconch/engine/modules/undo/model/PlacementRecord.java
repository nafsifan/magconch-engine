package com.magconch.engine.modules.undo.model;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

/**
 * 玩家方块放置记录快照模型
 * <p>
 * 纯内存轻量结构，精准保存原始物品堆叠组件与被替换的方块数据状态。
 */
public class PlacementRecord {

    // ==========================================
    // 字段属性
    // ==========================================

    private final UUID playerUuid;
    private final Location location;
    private final long timestamp;
    private final ItemStack item;
    private final BlockData replacedBlockData;

    public PlacementRecord(UUID playerUuid, Location location, long timestamp, ItemStack item, BlockData replacedBlockData) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid 不能为 null");
        this.location = Objects.requireNonNull(location, "location 不能为 null");
        this.timestamp = timestamp;
        this.item = Objects.requireNonNull(item, "item 不能为 null");
        this.replacedBlockData = Objects.requireNonNull(replacedBlockData, "replacedBlockData 不能为 null");
    }

    // ==========================================
    // Getter
    // ==========================================

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public Location getLocation() {
        return location;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public ItemStack getItem() {
        return item;
    }

    public BlockData getReplacedBlockData() {
        return replacedBlockData;
    }
}
