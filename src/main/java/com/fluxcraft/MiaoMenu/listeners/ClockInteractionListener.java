package com.fluxcraft.MiaoMenu.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.fluxcraft.MiaoMenu.managers.MenuClockManager;

public class ClockInteractionListener implements Listener {
    private final MenuClockManager clockManager;

    public ClockInteractionListener(MenuClockManager clockManager) {
        this.clockManager = clockManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // 避免雙手同時觸發兩次：只接受主手事件。
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (clockManager.isEnabled() && clockManager.isMenuClock(item)) {
            event.setCancelled(true);
            clockManager.openMenuWithClock(event.getPlayer());
        }
    }
}
