package com.magconch.engine.modules.expstore.listener;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.expstore.config.ExpStoreConfig;
import com.magconch.engine.modules.expstore.util.ExpBottleUtil;
import com.magconch.engine.util.ExperienceUtil;
import com.magconch.engine.util.ItemUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 经验存储交互事件监听器
 * <p>
 * 负责处理潜行右键附魔台萃取经验、手持经验瓶直接吸收以及发射器防误射拦截。
 */
public class ExpStoreListener implements Listener {

    private final MagconchEngine plugin;
    private final ExpStoreConfig config;

    public ExpStoreListener(MagconchEngine plugin, ExpStoreConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ==========================================
    // 玩家右键交互事件处理
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }

        Action action = event.getAction();

        // 1. 优先判定附魔台潜行萃取经验（严格尊重外部领地防破坏保护）
        if (action == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            if (event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) {
                return;
            }
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.ENCHANTING_TABLE && handleEnchantTableExtract(event, player, block)) {
                return;
            }
        }

        // 2. 判定经验瓶右键吸收经验
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            handleExpBottleAbsorb(event, player);
        }
    }

    /**
     * 处理附魔台经验萃取灌装逻辑
     */
    private boolean handleEnchantTableExtract(PlayerInteractEvent event, Player player, Block block) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        // 主副手防重：优先主手，主手非玻璃瓶时才判定副手
        boolean isMainHand = (event.getHand() == EquipmentSlot.HAND && mainHand.getType() == Material.GLASS_BOTTLE);
        boolean isOffHand = (event.getHand() == EquipmentSlot.OFF_HAND
                && mainHand.getType() != Material.GLASS_BOTTLE
                && offHand.getType() == Material.GLASS_BOTTLE);

        if (!isMainHand && !isOffHand) {
            return false;
        }

        // 拦截附魔台原生界面与方块交互
        event.setCancelled(true);

        // 权限校验
        if (!player.hasPermission("magconch.expstore.use")) {
            plugin.getLanguageManager().sendMessage(player, "general.no_permission");
            return true;
        }

        int points = config.getPointsPerBottle();
        int totalExp = ExperienceUtil.getTotalExperience(player);

        // 经验不足以凝聚标准经验瓶
        if (totalExp < points) {
            plugin.getLanguageManager().sendMessage(player, "exp-store.insufficient");
            if (config.isSoundEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            }
            return true;
        }

        // 扣除手持的 1 个空玻璃瓶
        EquipmentSlot slot = isMainHand ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        ItemStack bottleSource = isMainHand ? mainHand : offHand;
        consumeOneItem(player, slot, bottleSource);

        // 扣除玩家对应总经验点数
        ExperienceUtil.setTotalExperience(player, totalExp - points);

        // 构建标准经验瓶并安全发包
        ItemStack expBottle = ExpBottleUtil.createExpBottle(plugin, points);
        ItemUtil.giveItemSafely(player, expBottle);

        // 播放附魔台声效与粒子
        Location blockLoc = block.getLocation().add(0.5, 0.8, 0.5);
        if (config.isSoundEnabled()) {
            player.playSound(blockLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.0f);
            player.playSound(blockLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.9f);
        }
        if (config.isParticleEnabled()) {
            block.getWorld().spawnParticle(Particle.ENCHANT, blockLoc, 40, 0.3, 0.3, 0.3, 0.8);
        }

        return true;
    }

    /**
     * 处理手持经验瓶直接吸收逻辑
     */
    private void handleExpBottleAbsorb(PlayerInteractEvent event, Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        boolean isMainHand = (event.getHand() == EquipmentSlot.HAND && ExpBottleUtil.isExpBottle(mainHand, plugin));
        boolean isOffHand = (event.getHand() == EquipmentSlot.OFF_HAND
                && !ExpBottleUtil.isExpBottle(mainHand, plugin)
                && ExpBottleUtil.isExpBottle(offHand, plugin));

        if (!isMainHand && !isOffHand) {
            return;
        }

        // 若点击容器方块且未潜行，优先让玩家打开箱子/容器，不打断存取物品操作
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !player.isSneaking()) {
            Block block = event.getClickedBlock();
            if (block != null && block.getState() instanceof Container) {
                return;
            }
        }

        // 取消事件以彻底阻断原版投掷实体行为
        event.setCancelled(true);

        if (!player.hasPermission("magconch.expstore.use")) {
            plugin.getLanguageManager().sendMessage(player, "general.no_permission");
            return;
        }

        EquipmentSlot slot = isMainHand ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        ItemStack targetItem = isMainHand ? mainHand : offHand;
        int points = ExpBottleUtil.getStoredPoints(targetItem, plugin, config.getPointsPerBottle());

        // 消耗 1 个经验瓶
        consumeOneItem(player, slot, targetItem);

        // 若启用经验修补，优先按原版比例顺次修补另一只手及护甲
        int remainingPoints = points;
        if (config.isMendingRepairEnabled()) {
            remainingPoints = repairEquipments(player, isMainHand, points);
        }

        // 剩余点数注入玩家经验条
        if (remainingPoints > 0) {
            int currentExp = ExperienceUtil.getTotalExperience(player);
            long targetExp = (long) currentExp + remainingPoints;
            int finalExp = (int) Math.min(Integer.MAX_VALUE, targetExp);
            ExperienceUtil.setTotalExperience(player, finalExp);
        }

        // 返还 1 个空玻璃瓶
        ItemUtil.giveItemSafely(player, new ItemStack(Material.GLASS_BOTTLE, 1));

        // 吸收音效与粒子
        if (config.isSoundEnabled()) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.9f);
        }
        if (config.isParticleEnabled()) {
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.2, 0), 12, 0.3, 0.3, 0.3, 0.1);
        }
    }

    // ==========================================
    // 装备修补与物品消耗工具方法
    // ==========================================

    /**
     * 自动顺次修补玩家身上破损的经验修补装备
     *
     * @param player           目标玩家
     * @param bottleInMainHand 经验瓶是否在主手
     * @param points           封存的总经验点数
     * @return 修补消耗后结余的经验点数
     */
    private int repairEquipments(Player player, boolean bottleInMainHand, int points) {
        if (points <= 0) {
            return 0;
        }

        // 顺次修补判定队列：另一只手手持装备 -> 头部 -> 胸部 -> 腿部 -> 脚部
        EquipmentSlot otherHandSlot = bottleInMainHand ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        EquipmentSlot[] slots = {
                otherHandSlot,
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        int remaining = points;
        for (EquipmentSlot slot : slots) {
            if (remaining <= 0) {
                break;
            }

            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }

            // 必须拥有经验修补附魔
            if (item.getEnchantmentLevel(Enchantment.MENDING) <= 0) {
                continue;
            }

            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof Damageable damageable)) {
                continue;
            }

            // 过滤不可破坏标记与无损耗物品
            if (damageable.isUnbreakable() || !damageable.hasDamage() || damageable.getDamage() <= 0) {
                continue;
            }

            int currentDamage = damageable.getDamage();
            // 原版经验修补换算：1 经验点数 = 2 耐久（不足 2 耐久按 1 经验向上取整）
            int neededExp = (currentDamage + 1) / 2;

            if (remaining >= neededExp) {
                // 完全修复当前装备
                damageable.setDamage(0);
                item.setItemMeta(meta);
                player.getInventory().setItem(slot, item);
                remaining -= neededExp;
            } else {
                // 消耗所有剩余经验进行部分修复
                int repairAmount = remaining * 2;
                damageable.setDamage(Math.max(0, currentDamage - repairAmount));
                item.setItemMeta(meta);
                player.getInventory().setItem(slot, item);
                remaining = 0;
            }
        }

        return remaining;
    }

    /**
     * 安全扣除玩家主手或副手的单个手持物品
     */
    private void consumeOneItem(Player player, EquipmentSlot hand, ItemStack item) {
        int amount = item.getAmount();
        if (amount <= 1) {
            if (hand == EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
        } else {
            item.setAmount(amount - 1);
            if (hand == EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(item);
            } else {
                player.getInventory().setItemInOffHand(item);
            }
        }
    }

    // ==========================================
    // 发射器防护拦截
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (!config.isEnabled()) {
            return;
        }
        // 阻止发射器误将高额经验结晶当作普通附魔瓶投掷
        if (ExpBottleUtil.isExpBottle(event.getItem(), plugin)) {
            event.setCancelled(true);
        }
    }
}
