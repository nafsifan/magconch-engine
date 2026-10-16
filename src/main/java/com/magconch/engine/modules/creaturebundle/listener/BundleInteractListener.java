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
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.PotionSplashEvent;
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
 * 生物捕捉系统交互与安全监听器
 * <p>
 * 严密处理喷溅药水命中捕获（原地掉落生物蛋）、安全放生还原，并构建防刷怪笼、防幼崽繁殖与防发射器安全防线。
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
    // 1. 喷溅药水捕捉逻辑 (原地掉落生物蛋)
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        ItemStack potionItem = event.getPotion().getItem();
        if (!BundleItemUtil.isCapturePotion(potionItem, plugin)) {
            return;
        }

        // 默认取消药水的常规虚弱/发光效果扩散，转为纯净的封印逻辑
        event.setCancelled(true);

        // 投掷者判定（优先玩家，无投掷玩家时按规则校验）
        Player thrower = null;
        if (event.getPotion().getShooter() instanceof Player player) {
            thrower = player;
            if (!thrower.hasPermission("magconch.use.bundle")) {
                plugin.getLanguageManager().sendMessage(thrower, "creature-bundle.no_permission");
                return;
            }
        }

        Location splashLoc = event.getPotion().getLocation();

        // 寻找溅射半径内离落点最近的合法生物（单次精准捕获 1 只）
        LivingEntity captured = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (LivingEntity living : event.getAffectedEntities()) {
            if (isEligibleTarget(living, thrower)) {
                double distSq = living.getLocation().distanceSquared(splashLoc);
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq;
                    captured = living;
                }
            }
        }

        if (captured == null) {
            // 未命中任何有效生物，仅播放药水破碎声与粒子
            splashLoc.getWorld().playSound(splashLoc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
            splashLoc.getWorld().spawnParticle(Particle.WITCH, splashLoc, 20, 0.4, 0.4, 0.4, 0.05);
            return;
        }

        Location entityLoc = captured.getLocation();

        // 捕捉概率校验 (默认 1.0 即 100% 成功)
        double chance = config.getCaptureChance();
        if (chance < 1.0 && java.util.concurrent.ThreadLocalRandom.current().nextDouble() > chance) {
            // 判定失败：播放生物挣脱的灰色烟雾与咆哮声，生物安全保留
            entityLoc.getWorld().playSound(entityLoc, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.2f);
            entityLoc.getWorld().spawnParticle(Particle.SMOKE, entityLoc.clone().add(0, 0.5, 0), 25, 0.3, 0.3, 0.3, 0.05);
            if (thrower != null) {
                String targetName = BundleItemUtil.resolveEntityDisplayName(captured);
                plugin.getLanguageManager().sendMessage(thrower, "creature-bundle.failed", "{entity}", targetName);
            }
            return;
        }

        // 拴绳安全回收
        if (captured.isLeashed()) {
            captured.setLeashHolder(null);
            captured.getWorld().dropItemNaturally(entityLoc, new ItemStack(Material.LEAD, 1));
        }

        // 创建实体完整快照并生成封存生物蛋
        EntitySnapshot snapshot = captured.createSnapshot();
        ItemStack filledEgg = BundleItemUtil.createFilledEgg(plugin, captured, snapshot, config.getCustomModelData());

        // 原地安全掉落封存生物蛋
        captured.getWorld().dropItemNaturally(entityLoc, filledEgg);

        // 视听反馈：空灵封印音与图腾收纳微光
        captured.getWorld().playSound(entityLoc, Sound.ITEM_TOTEM_USE, 0.6f, 1.5f);
        captured.getWorld().playSound(entityLoc, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        captured.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, entityLoc.clone().add(0, 0.5, 0), 20, 0.3, 0.4, 0.3, 0.1);
        captured.getWorld().spawnParticle(Particle.PORTAL, entityLoc.clone().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.15);

        String entityName = BundleItemUtil.resolveEntityDisplayName(captured);

        // 注销原实体
        captured.remove();

        if (thrower != null) {
            plugin.getLanguageManager().sendMessage(thrower, "creature-bundle.captured", "{entity}", entityName);
        }
    }

    // ==========================================
    // 2. 释放逻辑 (手持生物蛋右键方块表面)
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onReleaseEgg(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        if (!BundleItemUtil.isFilledEgg(mainHand, plugin)) {
            return;
        }

        // 一律拦截原版生成蛋行为（防止原版白板怪生成机制触发）
        event.setCancelled(true);

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        // 防御 1: 绝对禁止右键刷怪笼（防止原版将刷怪笼类型改写）
        if (clickedBlock.getType() == Material.SPAWNER) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.spawner_denied");
            return;
        }

        // 权限校验
        if (!player.hasPermission("magconch.use.bundle")) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.no_permission");
            return;
        }

        BlockFace face = event.getBlockFace();
        Block spawnBlock = clickedBlock.getRelative(face);

        // 危险方块与空间检测
        if (DANGEROUS_BLOCKS.contains(spawnBlock.getType()) || DANGEROUS_BLOCKS.contains(clickedBlock.getType())) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.dangerous_location");
            return;
        }

        if (spawnBlock.getType().isSolid()) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.no_space");
            return;
        }

        // 下界基岩顶层限制
        if (spawnBlock.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER && spawnBlock.getY() >= 127) {
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.dangerous_location");
            return;
        }

        // 提取快照 NBT 并反序列化完整实体
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
            plugin.getLogger().warning("模块 [creature-bundle] 解析封存生物蛋快照失败: " + ex.getMessage());
            plugin.getLanguageManager().sendMessage(player, "creature-bundle.corrupted_data");
            return;
        }

        // 计算生成精确坐标并朝向玩家
        Location spawnLoc = spawnBlock.getLocation().add(0.5, 0.05, 0.5);
        Vector lookDir = player.getLocation().toVector().subtract(spawnLoc.toVector()).setY(0);
        if (lookDir.lengthSquared() > 0.001) {
            spawnLoc.setDirection(lookDir);
        }

        // 在主线程安全生成具有完整 NBT 的实体
        Entity spawned = snapshot.createEntity(spawnLoc);
        spawned.setVelocity(new Vector(0, 0, 0));

        // 消耗 1 枚封存蛋
        int amount = mainHand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            mainHand.setAmount(amount - 1);
            player.getInventory().setItemInMainHand(mainHand);
        }

        // 视听反馈：破壳音效与新生微烟粒子
        spawnBlock.getWorld().playSound(spawnLoc, Sound.ENTITY_TURTLE_EGG_CRACK, 1.0f, 1.2f);
        spawnBlock.getWorld().playSound(spawnLoc, Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.0f);
        spawnBlock.getWorld().spawnParticle(Particle.POOF, spawnLoc.clone().add(0, 0.3, 0), 8, 0.2, 0.2, 0.2, 0.05);
        spawnBlock.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, spawnLoc.clone().add(0, 0.5, 0), 12, 0.3, 0.3, 0.3, 0.0);
    }

    // ==========================================
    // 3. 安全防御逻辑 (防生幼崽、防发射器、防合成)
    // ==========================================

    /**
     * 防御 2: 拦截手持封存生物蛋右键同类成年生物触发原版幼崽繁殖
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntityWithEgg(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (BundleItemUtil.isFilledEgg(item, plugin)) {
            event.setCancelled(true);
        }
    }

    /**
     * 防御 3: 拦截发射器发射捕捉药水与封存生物蛋（防止绕过插件判定生成原版白板怪）
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenseModuleItem(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        if (BundleItemUtil.isAnyModuleItem(item, plugin)) {
            event.setCancelled(true);
        }
    }

    /**
     * 校验生物是否满足封印捕获条件
     */
    private boolean isEligibleTarget(LivingEntity living, Player thrower) {
        if (living instanceof Player || living instanceof ArmorStand || living instanceof WanderingTrader) {
            return false;
        }
        if (living.isDead() || living.getHealth() <= 0 || !living.isValid()) {
            return false;
        }
        if (!living.getPassengers().isEmpty() || living.getVehicle() != null) {
            return false;
        }
        if (!config.isAllowed(living.getType())) {
            return false;
        }
        if (living instanceof Tameable tameable && tameable.isTamed()) {
            return tameable.getOwnerUniqueId() == null
                    || (thrower != null && (tameable.getOwnerUniqueId().equals(thrower.getUniqueId()) || thrower.hasPermission("magconch.bypass.bundle")));
        }
        return true;
    }
}
