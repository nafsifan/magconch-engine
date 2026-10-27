package com.magconch.engine.modules.mark.listener;

import com.magconch.engine.modules.mark.MarkModule;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 标点交互监听器
 * <p>
 * 监听中键拾取（近身 5 格）与手持望远镜开镜按 F 键（超远距离）触发标点。
 */
public class MarkPickListener implements Listener {

    private static final double REACH_RANGE = 5.0;

    private final MarkModule module;

    public MarkPickListener(MarkModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPickItem(PlayerPickItemEvent event) {
        if (!module.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        event.setCancelled(true);
        module.performMark(player, REACH_RANGE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (!module.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isHandRaised()) {
            return;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (mainHand.getType() != Material.SPYGLASS && offHand.getType() != Material.SPYGLASS) {
            return;
        }

        event.setCancelled(true);
        module.performMark(player, module.getConfig().getRange());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        module.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
