package com.magconch.engine.modules.creaturebundle.listener;

import com.magconch.engine.MagconchEngine;
import com.magconch.engine.modules.creaturebundle.config.CreatureBundleConfig;
import com.magconch.engine.modules.creaturebundle.util.BundleItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;

/**
 * 生灵收纳袋交互与安全监听器
 * <p>
 * 严谨处理潜行收纳、安全释放，并建立防刷物、防偷宠、防卡墙与原版容器隔离防线。
 */
public class BundleInteractListener implements Listener {

    // ==========================================
    // 危险/禁止释放方块黑名单
    // ==========================================

    private static final Set<Material> DANGEROUS_BLOCKS = Set.of(
            Material.LAVA,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.CACTUS,
            Material.SWEET_BERRY_BUSH,
            Material.NETHER_PORTAL,
            Material.END_PORTAL,
            Material.END_GATEWAY
    );

    private final MagconchEngine plugin;
    private final CreatureBundleConfig config;

    public BundleInteractListener(MagconchEngine plugin, CreatureBundleConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ==========================================
    // 1. 捕获逻辑 (潜行 + 右键生物)
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCaptureEntity(PlayerInteractEntityEvent event) {
        // 1. 仅限主手交互，彻底规避双发漏洞
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // 2. 检查玩家是否手持空白生灵袋且处于潜行状态
        if (!player.isSneaking() || !BundleItemUtil.isEmptyBundle(mainHand, plugin)) {
            return;
        }

        // 3. 一旦判定为尝试收纳，立即拦截原版实体交互（避免弹出交易、骑乘或让宠物坐下）
        event.setCancelled(true);

        // 4. 权限校验
        if (!player.hasPermission("magconch.use.bundle")) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.no_permission");
            return;
        }

        // 5. 目标实体合法性检测
        Entity target = event.getRightClicked();
        if (!(target instanceof LivingEntity livingEntity) || target instanceof Player || target instanceof ArmorStand) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.invalid_entity");
            return;
        }

        // 6. 流浪商人排除
        if (target instanceof WanderingTrader) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.wandering_trader_denied");
            return;
        }

        // 7. 濒死/无效实体防御
        if (livingEntity.isDead() || livingEntity.getHealth() <= 0 || !livingEntity.isValid()) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.dead_entity");
            return;
        }

        // 8. 载具与乘客关系校验
        if (!livingEntity.getPassengers().isEmpty() || livingEntity.getVehicle() != null) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.riding_entity");
            return;
        }

        // 9. 生物种类白名单检查 (已自动排除服主禁用的黑名单)
        if (!config.isAllowed(livingEntity.getType())) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.type_not_allowed");
            return;
        }

        // 10. 驯服宠物所有权校验（内置防盗宠机制）
        if (livingEntity instanceof Tameable tameable && tameable.isTamed()) {
            if (tameable.getOwnerUniqueId() != null
                    && !tameable.getOwnerUniqueId().equals(player.getUniqueId())
                    && !player.hasPermission("magconch.bypass.bundle")) {
                plugin.getLanguageManager().sendMessage(player, "creature-bundle.not_owner");
                return;
            }
        }

        // 12. 拴绳安全回收
        if (livingEntity.isLeashed()) {
            livingEntity.setLeashHolder(null);
            ItemStack leash = new ItemStack(Material.LEAD, 1);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(leash);
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }

        // 13. 创建实体快照与封存生灵袋
        EntitySnapshot snapshot = livingEntity.createSnapshot();
        ItemStack filledBundle = BundleItemUtil.createFilledBundle(plugin, livingEntity, snapshot, config.getCustomModelData());

        // 14. 原子化物品扣减与替换（防止高频并发丢弃刷物）
        int amount = mainHand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(filledBundle);
        } else {
            mainHand.setAmount(amount - 1);
            player.getInventory().setItemInMainHand(mainHand);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(filledBundle);
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }

        // 15. 安全注销原实体
        livingEntity.remove();

        // 16. 视听反馈
        Location eyeLoc = livingEntity.getEyeLocation();
        player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.6f);
        player.spawnParticle(Particle.PORTAL, eyeLoc, 25, 0.3, 0.3, 0.3, 0.1);

        String entityName = BundleItemUtil.resolveEntityDisplayName(livingEntity);
        plugin.getLanguageManager().sendMessage(player, "creature-bundle.captured", Map.of("entity", entityName));
    }

    // ==========================================
    // 2. 释放逻辑 (手持封存袋右键方块表面)
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onReleaseEntity(PlayerInteractEvent event) {
        // 1. 仅限主手右键方块
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // 2. 判定是否为已封存生灵袋
        if (!BundleItemUtil.isFilledBundle(mainHand, plugin)) {
            return;
        }

        // 3. 拦截原版收纳袋右键弹出行为
        event.setCancelled(true);

        // 4. 权限校验
        if (!player.hasPermission("magconch.use.bundle")) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.no_permission");
            return;
        }

        // 5. 方块与生成位置安全计算
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        BlockFace face = event.getBlockFace();
        Block spawnBlock = clickedBlock.getRelative(face);

        // 6. 危险方块与空间检测
        if (DANGEROUS_BLOCKS.contains(spawnBlock.getType()) || DANGEROUS_BLOCKS.contains(clickedBlock.getType())) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.dangerous_location");
            return;
        }

        if (spawnBlock.getType().isSolid()) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.no_space");
            return;
        }

        // 7. 下界基岩顶层限制
        if (spawnBlock.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER && spawnBlock.getY() >= 127) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.dangerous_location");
            return;
        }

        // 8. 提取快照 NBT 数据并反序列化实体
        PersistentDataContainer pdc = mainHand.getItemMeta().getPersistentDataContainer();
        NamespacedKey dataKey = new NamespacedKey(plugin, BundleItemUtil.KEY_DATA);
        String snbt = pdc.get(dataKey, PersistentDataType.STRING);

        if (snbt == null || snbt.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.corrupted_data");
            return;
        }

        EntitySnapshot snapshot;
        try {
            snapshot = Bukkit.getEntityFactory().createEntitySnapshot(snbt);
        } catch (Exception ex) {
            plugin.getLogger().warning("解析生灵袋快照失败: " + ex.getMessage());
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.corrupted_data");
            return;
        }

        // 9. 计算生成精确坐标（方块水平中心偏上）并让实体朝向玩家
        Location spawnLoc = spawnBlock.getLocation().add(0.5, 0.05, 0.5);
        Vector lookDir = player.getLocation().toVector().subtract(spawnLoc.toVector()).setY(0);
        if (lookDir.lengthSquared() > 0.001) {
            spawnLoc.setDirection(lookDir);
        }

        // 10. 生成实体并确保主线程世界安全
        Entity spawned = snapshot.createEntity(spawnLoc);
        spawned.setVelocity(new Vector(0, 0, 0));

        // 11. 消耗封存生灵袋
        int amount = mainHand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            mainHand.setAmount(amount - 1);
            player.getInventory().setItemInMainHand(mainHand);
        }

        // 12. 视听反馈
        spawnBlock.getWorld().playSound(spawnLoc, Sound.ITEM_BUNDLE_DROP_CONTENTS, 1.0f, 1.0f);
        spawnBlock.getWorld().playSound(spawnLoc, Sound.ENTITY_CHICKEN_EGG, 0.8f, 1.2f);
        spawnBlock.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, spawnLoc.clone().add(0, 0.5, 0), 16, 0.3, 0.3, 0.3, 0.0);

        String entityName = pdc.get(new NamespacedKey(plugin, BundleItemUtil.KEY_NAME), PersistentDataType.STRING);
        if (entityName == null || entityName.isEmpty()) {
            entityName = BundleItemUtil.getChineseEntityName(snapshot.getEntityType());
        }
        plugin.getLanguageManager().sendMessage(player, "creature-bundle.released", Map.of("entity", entityName));
    }

    // ==========================================
    // 3. 原版收纳袋容器隔离与防伪造合成防御
    // ==========================================

    /**
     * 严禁将生灵袋放入工作台进行染色或二次合成（防止 PDC 抹除或刷物）
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        for (ItemStack item : matrix) {
            if (BundleItemUtil.isAnyCreatureBundle(item, plugin)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /**
     * 拦截原版收纳袋背包内点击交互，防止常规物品被拖拽塞入生灵袋
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        boolean currentIsBundle = BundleItemUtil.isAnyCreatureBundle(current, plugin);
        boolean cursorIsBundle = BundleItemUtil.isAnyCreatureBundle(cursor, plugin);

        // 如果点击的两者之一是生灵袋，且另一个不是空气（试图拖动物品放入 Bundle 或取出）
        if (currentIsBundle && cursor != null && !cursor.getType().isAir()) {
            event.setCancelled(true);
            return;
        }

        if (cursorIsBundle && current != null && !current.getType().isAir()) {
            event.setCancelled(true);
            return;
        }

        // 拦截针对收纳袋的快捷放入动作
        if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR && (currentIsBundle || cursorIsBundle)) {
            event.setCancelled(true);
        }
    }
}
