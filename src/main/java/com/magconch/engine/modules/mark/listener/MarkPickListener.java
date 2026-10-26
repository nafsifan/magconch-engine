package com.magconch.engine.modules.mark.listener;

import com.magconch.engine.modules.mark.MarkModule;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 标点交互监听器
 * <p>
 * 监听中键拾取触发标点，并在玩家退出时回收瞬态内存状态。
 */
public class MarkPickListener implements Listener {

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
        module.performMark(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!module.isEnabled()) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (mainHand.getType() != Material.SPYGLASS && offHand.getType() != Material.SPYGLASS) {
            return;
        }

        if (module.performMark(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        module.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
