package com.fluxcraft.MiaoMenu.javamenu;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.menu.action.ActionRegistry;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

public class JavaMenuListener implements Listener {
    private final MiaoMenu plugin;
    private final ActionRegistry actionRegistry;

    public JavaMenuListener(MiaoMenu plugin, ActionRegistry actionRegistry) {
        this.plugin = plugin;
        this.actionRegistry = actionRegistry;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // 先看頂層視窗是不是 MenuHolder：玩家對自身物品欄做 shift-click / NUMBER_KEY / SWAP_OFFHAND / COLLECT_TO_CURSOR
        // 都會在 clickedInventory=PlayerInventory 的情況下把物品 transfer 到 MenuHolder（上層）裡造成物品永久遺失。
        // 只要視窗是 MiaoMenu 選單，全部 cancel；接著只有「點到選單格」才繼續派發 click 動作。
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof JavaMenu.MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !(clickedInventory.getHolder() instanceof JavaMenu.MenuHolder)) {
            // 點到下方玩家物品欄、或點到視窗外：已 cancel 防止 shift-transfer，但不派發任何動作。
            return;
        }
        if (!plugin.getInteractionRateLimiter().allow(player.getUniqueId())) {
            return;
        }
        JavaMenu.MenuItem item = holder.getMenu().getItem(event.getSlot());
        if (item == null) {
            return;
        }
        JavaMenu.MenuItem.LockState lockState = item.resolveLockState(player, plugin, plugin.getRequirementService(), holder.getMenu().getName(), holder.getMenu().getRequirementBlocks());
        if (lockState.locked()) {
            String lockMsg = lockState.message();
            if (lockMsg != null && !lockMsg.isBlank()) {
                player.sendMessage(lockMsg);
            }
            return;
        }
        List<String> commands = resolveClickCommands(item, event.getClick());
        if (!commands.isEmpty()) {
            executeActions(player, commands);
        }
    }

    /**
     * 依 ClickType 分流到對應 click 動作列表，並做 DeluxeMenus 風格的逐層 fallback：
     *   SHIFT_LEFT → shift_left → left → click
     *   SHIFT_RIGHT → shift_right → right → click
     *   MIDDLE → middle → click
     *   LEFT (含 DOUBLE_CLICK) → left → click
     *   RIGHT → right → click
     *   其他 (DROP/NUMBER_KEY/...) → click (catch-all)
     * 找不到任何配對則回空 list、不執行任何動作。
     */
    private List<String> resolveClickCommands(JavaMenu.MenuItem item, ClickType click) {
        return switch (click) {
            case SHIFT_LEFT -> firstNonEmpty(
                    item.getShiftLeftClickCommands(),
                    item.getLeftClickCommands(),
                    item.getClickCommands());
            case SHIFT_RIGHT -> firstNonEmpty(
                    item.getShiftRightClickCommands(),
                    item.getRightClickCommands(),
                    item.getClickCommands());
            case MIDDLE -> firstNonEmpty(
                    item.getMiddleClickCommands(),
                    item.getClickCommands());
            case LEFT, DOUBLE_CLICK -> firstNonEmpty(
                    item.getLeftClickCommands(),
                    item.getClickCommands());
            case RIGHT -> firstNonEmpty(
                    item.getRightClickCommands(),
                    item.getClickCommands());
            default -> item.getClickCommands();
        };
    }

    @SafeVarargs
    private static List<String> firstNonEmpty(List<String>... lists) {
        for (List<String> list : lists) {
            if (list != null && !list.isEmpty()) {
                return list;
            }
        }
        return List.of();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof JavaMenu.MenuHolder) {
            event.setCancelled(true);
        }
    }

    private void executeActions(Player player, List<String> rawCommands) {
        for (String rawCmd : rawCommands) {
            if (rawCmd == null || rawCmd.trim().isEmpty()) {
                continue;
            }
            String parsed = PlaceholderUtils.parse(player, rawCmd, plugin);
            actionRegistry.dispatch(player, parsed);
        }
    }
}
