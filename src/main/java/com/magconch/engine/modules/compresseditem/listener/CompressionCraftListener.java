package com.magconch.engine.modules.compresseditem.listener;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.compresseditem.config.CompressedItemConfig;
import com.magconch.engine.modules.compresseditem.util.CompressedItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;

/**
 * 工作台压缩与随身解压核心动态事件引擎
 * <p>
 * 负责 9合1 压缩与 1拆9 解压的动态产物计算、原版非法误合拦截、
 * 完整支持原版所有快捷交互（左右键、Shift批量、Q/Ctrl+Q丢弃、数字键换槽）。
 */
public class CompressionCraftListener implements Listener {

    private enum CraftMode {
        NONE,
        COMPRESS,
        DECOMPRESS
    }

    private record CraftEvaluation(CraftMode mode, ItemStack result) {
        static final CraftEvaluation BLOCKED = new CraftEvaluation(CraftMode.NONE, null);
    }

    // ==========================================
    // 依赖与状态
    // ==========================================

    private final MagconchEngine plugin;
    private final CompressedItemConfig config;

    // 原版工作台与随身合成 Shift 点击的槽位装填顺序：快捷栏 8 到 0，主背包 35 到 9
    private static final int[] VANILLA_CRAFT_SLOT_ORDER = {
            8, 7, 6, 5, 4, 3, 2, 1, 0,
            35, 34, 33, 32, 31, 30, 29, 28, 27,
            26, 25, 24, 23, 22, 21, 20, 19, 18,
            17, 16, 15, 14, 13, 12, 11, 10, 9
    };

    public CompressionCraftListener(MagconchEngine plugin, CompressedItemConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ==========================================
    // 动态配方预览与误合拦截
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        World world = event.getView().getPlayer().getWorld();
        CraftEvaluation eval = evaluate(inv.getMatrix(), world);
        if (eval != null) {
            inv.setResult(eval.result());
        }
    }

    // ==========================================
    // 结果槽点击原子结算 (完整支持原生快捷操作)
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResultClick(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }
        if (!(event.getInventory() instanceof CraftingInventory inv)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        CraftEvaluation eval = evaluate(inv.getMatrix(), player.getWorld());
        if (eval == null || eval.mode() == CraftMode.NONE || eval.result() == null) {
            return;
        }

        // 接管事件，阻止原版默认行为
        event.setCancelled(true);
        boolean isCompression = (eval.mode() == CraftMode.COMPRESS);

        ClickType click = event.getClick();
        switch (click) {
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                if (isCompression) {
                    handleShiftCompression(player, inv);
                } else {
                    handleShiftDecompression(player, inv);
                }
            }
            case DROP -> {
                if (isCompression) {
                    handleDropCompression(player, inv, false);
                } else {
                    handleDropDecompression(player, inv, false);
                }
            }
            case CONTROL_DROP -> {
                if (isCompression) {
                    handleDropCompression(player, inv, true);
                } else {
                    handleDropDecompression(player, inv, true);
                }
            }
            case NUMBER_KEY -> {
                int hotbarSlot = event.getHotbarButton();
                if (isCompression) {
                    handleNumberKeyCompression(player, inv, hotbarSlot);
                } else {
                    handleNumberKeyDecompression(player, inv, hotbarSlot);
                }
            }
            default -> {
                if (isCompression) {
                    handleNormalCompression(player, inv, event);
                } else {
                    handleNormalDecompression(player, inv, event);
                }
            }
        }

        // 重新计算并通知客户端刷新
        refreshCraftResult(inv);
        player.updateInventory();
    }

    // ==========================================
    // 压缩操作结算
    // ==========================================

    private void handleNormalCompression(Player player, CraftingInventory inv, InventoryClickEvent event) {
        ItemStack[] matrix = inv.getMatrix();
        ItemStack cursor = event.getCursor();
        ItemStack result = inv.getResult();
        if (result == null || !canMerge(cursor, result, 1)) return;

        for (int i = 0; i < matrix.length; i++) {
            deductSlot(matrix, i, 1);
        }
        inv.setMatrix(matrix);

        if (cursor == null || cursor.getType().isAir()) {
            event.getView().setCursor(result.clone());
        } else {
            cursor.setAmount(cursor.getAmount() + 1);
            event.getView().setCursor(cursor);
        }
    }

    private void handleShiftCompression(Player player, CraftingInventory inv) {
        ItemStack[] matrix = inv.getMatrix();
        ItemStack result = inv.getResult();
        if (result == null) return;

        int maxBatches = Integer.MAX_VALUE;
        for (ItemStack item : matrix) {
            if (item != null) {
                maxBatches = Math.min(maxBatches, item.getAmount());
            }
        }
        if (maxBatches <= 0 || maxBatches == Integer.MAX_VALUE) return;

        int canFit = calculateFitCapacity(player, result);
        int finalBatches = Math.min(maxBatches, canFit);
        if (finalBatches <= 0) return;

        for (int i = 0; i < matrix.length; i++) {
            deductSlot(matrix, i, finalBatches);
        }
        inv.setMatrix(matrix);

        addBatchToPlayer(player, result, finalBatches);
    }

    // ==========================================
    // 解压操作结算
    // ==========================================

    private void handleNormalDecompression(Player player, CraftingInventory inv, InventoryClickEvent event) {
        ItemStack[] matrix = inv.getMatrix();
        ItemStack cursor = event.getCursor();
        ItemStack result = inv.getResult();
        if (result == null || !canMerge(cursor, result, 9)) return;

        int inputSlot = getDecompressInputSlot(matrix);
        if (inputSlot == -1) return;

        deductSlot(matrix, inputSlot, 1);
        inv.setMatrix(matrix);

        if (cursor == null || cursor.getType().isAir()) {
            ItemStack newCursor = result.clone();
            newCursor.setAmount(9);
            event.getView().setCursor(newCursor);
        } else {
            cursor.setAmount(cursor.getAmount() + 9);
            event.getView().setCursor(cursor);
        }
    }

    private void handleShiftDecompression(Player player, CraftingInventory inv) {
        ItemStack[] matrix = inv.getMatrix();
        ItemStack result = inv.getResult();
        if (result == null) return;

        int inputSlot = getDecompressInputSlot(matrix);
        if (inputSlot == -1) return;

        int inputAmount = matrix[inputSlot].getAmount();
        int canFit = calculateFitCapacity(player, result);
        int finalBatches = Math.min(inputAmount, canFit / 9);
        if (finalBatches <= 0) return;

        deductSlot(matrix, inputSlot, finalBatches);
        inv.setMatrix(matrix);

        addBatchToPlayer(player, result, finalBatches * 9);
    }

    // ==========================================
    // 原生快捷操作支持 (Q 键丢弃与数字键快捷栏)
    // ==========================================

    private void handleDropCompression(Player player, CraftingInventory inv, boolean dropAll) {
        ItemStack[] matrix = inv.getMatrix();
        ItemStack result = inv.getResult();
        if (result == null) return;

        int batches = 1;
        if (dropAll) {
            batches = Integer.MAX_VALUE;
            for (ItemStack item : matrix) {
                if (item != null) batches = Math.min(batches, item.getAmount());
            }
        }
        if (batches <= 0 || batches == Integer.MAX_VALUE) return;

        for (int i = 0; i < matrix.length; i++) {
            deductSlot(matrix, i, batches);
        }
        inv.setMatrix(matrix);

        dropBatch(player, result, batches);
    }

    private void handleDropDecompression(Player player, CraftingInventory inv, boolean dropAll) {
        ItemStack[] matrix = inv.getMatrix();
        ItemStack result = inv.getResult();
        if (result == null) return;

        int inputSlot = getDecompressInputSlot(matrix);
        if (inputSlot == -1) return;

        int batches = dropAll ? matrix[inputSlot].getAmount() : 1;
        if (batches <= 0) return;

        deductSlot(matrix, inputSlot, batches);
        inv.setMatrix(matrix);

        dropBatch(player, result, batches * 9);
    }

    private void handleNumberKeyCompression(Player player, CraftingInventory inv, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return;
        ItemStack[] matrix = inv.getMatrix();
        ItemStack result = inv.getResult();
        if (result == null) return;

        ItemStack target = player.getInventory().getItem(hotbarSlot);
        if (!canMerge(target, result, 1)) return;

        for (int i = 0; i < matrix.length; i++) {
            deductSlot(matrix, i, 1);
        }
        inv.setMatrix(matrix);

        if (target == null || target.getType().isAir()) {
            ItemStack clone = result.clone();
            clone.setAmount(1);
            player.getInventory().setItem(hotbarSlot, clone);
        } else {
            target.setAmount(target.getAmount() + 1);
            player.getInventory().setItem(hotbarSlot, target);
        }
    }

    private void handleNumberKeyDecompression(Player player, CraftingInventory inv, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return;
        ItemStack[] matrix = inv.getMatrix();
        ItemStack result = inv.getResult();
        if (result == null) return;

        ItemStack target = player.getInventory().getItem(hotbarSlot);
        if (!canMerge(target, result, 9)) return;

        int inputSlot = getDecompressInputSlot(matrix);
        if (inputSlot == -1) return;

        deductSlot(matrix, inputSlot, 1);
        inv.setMatrix(matrix);

        if (target == null || target.getType().isAir()) {
            ItemStack clone = result.clone();
            clone.setAmount(9);
            player.getInventory().setItem(hotbarSlot, clone);
        } else {
            target.setAmount(target.getAmount() + 9);
            player.getInventory().setItem(hotbarSlot, target);
        }
    }

    // ==========================================
    // 核心配方计算 (单点真实源与原版动态避让)
    // ==========================================

    private CraftEvaluation evaluate(ItemStack[] matrix, World world) {
        int nonEmpty = 0;
        ItemStack singleItem = null;
        boolean hasCompressed = false;

        for (ItemStack item : matrix) {
            if (item != null && !item.getType().isAir()) {
                nonEmpty++;
                singleItem = item;
                if (CompressedItemUtil.isCompressed(item, plugin)) {
                    hasCompressed = true;
                }
            }
        }

        if (nonEmpty == 0) return null;

        // 1. 判定 9合1 压缩
        if (matrix.length == 9 && nonEmpty == 9) {
            ItemStack first = matrix[0];
            Material mat = first.getType();
            int baseLevel = CompressedItemUtil.getCompressedLevel(first, plugin);

            // 若为 0 级未压缩原料，检测原版或第三方是否有 3x3 转化配方，优先避让放行原版
            if (baseLevel == 0 && world != null) {
                try {
                    Recipe existingRecipe = Bukkit.getCraftingRecipe(matrix, world);
                    if (existingRecipe != null) {
                        return null;
                    }
                } catch (Exception ignored) {
                    // 防御部分极端上下文下的检测异常
                }
            }

            if (config.isCompressible(mat) && baseLevel < config.getMaxLevel()) {
                boolean allMatch = true;
                for (int i = 1; i < 9; i++) {
                    ItemStack slot = matrix[i];
                    if (slot == null || slot.getType() != mat
                            || CompressedItemUtil.getCompressedLevel(slot, plugin) != baseLevel) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    return new CraftEvaluation(CraftMode.COMPRESS, CompressedItemUtil.createCompressedItem(mat, baseLevel + 1, 1, plugin));
                }
            }
        }

        // 2. 判定 1拆9 解压
        if (nonEmpty == 1 && singleItem != null) {
            int level = CompressedItemUtil.getCompressedLevel(singleItem, plugin);
            if (level > 0) {
                return new CraftEvaluation(CraftMode.DECOMPRESS, CompressedItemUtil.createDecompressedItem(singleItem.getType(), level - 1, 9, plugin));
            }
        }

        // 3. 包含压缩物但非合法配方 -> 阻断原版误合
        return hasCompressed ? CraftEvaluation.BLOCKED : null;
    }

    private void refreshCraftResult(CraftingInventory inv) {
        World world = inv.getViewers().isEmpty() ? null : inv.getViewers().get(0).getWorld();
        CraftEvaluation eval = evaluate(inv.getMatrix(), world);
        inv.setResult(eval != null ? eval.result() : null);
    }

    // ==========================================
    // 辅助工具方法
    // ==========================================

    private int getDecompressInputSlot(ItemStack[] matrix) {
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] != null && !matrix[i].getType().isAir()) {
                return i;
            }
        }
        return -1;
    }

    private boolean canMerge(ItemStack target, ItemStack result, int addAmount) {
        if (target == null || target.getType().isAir()) return true;
        if (!target.isSimilar(result)) return false;
        return target.getAmount() + addAmount <= target.getMaxStackSize();
    }

    private void deductSlot(ItemStack[] matrix, int index, int count) {
        ItemStack item = matrix[index];
        if (item == null) return;
        int remain = item.getAmount() - count;
        matrix[index] = (remain <= 0) ? null : matrix[index];
        if (remain > 0) {
            item.setAmount(remain);
        }
    }

    private int calculateFitCapacity(Player player, ItemStack sample) {
        int totalCapacity = 0;
        int maxStack = sample.getMaxStackSize();
        ItemStack[] storage = player.getInventory().getStorageContents();

        for (ItemStack item : storage) {
            if (item == null || item.getType().isAir()) {
                totalCapacity += maxStack;
            } else if (item.isSimilar(sample)) {
                totalCapacity += Math.max(0, maxStack - item.getAmount());
            }
        }
        return totalCapacity;
    }

    private void addBatchToPlayer(Player player, ItemStack sample, int totalAmount) {
        int remaining = totalAmount;
        int maxStack = sample.getMaxStackSize();
        PlayerInventory inv = player.getInventory();

        // 优先合并同类未满组物品
        for (int slot : VANILLA_CRAFT_SLOT_ORDER) {
            if (remaining <= 0) break;
            ItemStack current = inv.getItem(slot);
            if (current != null && current.isSimilar(sample)) {
                int space = maxStack - current.getAmount();
                if (space > 0) {
                    int toAdd = Math.min(remaining, space);
                    current.setAmount(current.getAmount() + toAdd);
                    inv.setItem(slot, current);
                    remaining -= toAdd;
                }
            }
        }

        // 填充空槽位
        for (int slot : VANILLA_CRAFT_SLOT_ORDER) {
            if (remaining <= 0) break;
            ItemStack current = inv.getItem(slot);
            if (current == null || current.getType().isAir()) {
                int toAdd = Math.min(remaining, maxStack);
                ItemStack stack = sample.clone();
                stack.setAmount(toAdd);
                inv.setItem(slot, stack);
                remaining -= toAdd;
            }
        }

        // 溢出物安全掉落兜底
        while (remaining > 0) {
            int toDrop = Math.min(remaining, maxStack);
            ItemStack drop = sample.clone();
            drop.setAmount(toDrop);
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
            remaining -= toDrop;
        }
    }

    private void dropBatch(Player player, ItemStack sample, int totalAmount) {
        int remaining = totalAmount;
        int maxStack = sample.getMaxStackSize();
        while (remaining > 0) {
            int toDrop = Math.min(remaining, maxStack);
            ItemStack clone = sample.clone();
            clone.setAmount(toDrop);
            player.getWorld().dropItemNaturally(player.getLocation(), clone);
            remaining -= toDrop;
        }
    }
}
