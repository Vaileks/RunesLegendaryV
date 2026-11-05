package dev.vaileks.runeslegendaryv.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import dev.vaileks.runeslegendaryv.menu.TotemMenu;
import dev.vaileks.runeslegendaryv.weapon.perchatkastoikosti.Gauntlet;

/** The four top slots are a view of the live item's count, never another store of totems. */
public final class TotemMenuListener implements Listener {
    private final Gauntlet gauntlet;
    public TotemMenuListener(Gauntlet gauntlet) { this.gauntlet = gauntlet; }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TotemMenu menu)
                || !(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot(), top = event.getView().getTopInventory().getSize();
        ItemStack glove = gauntlet.menuItem(player, menu);
        if (glove == null) { event.setCancelled(true); return; }
        int count = gauntlet.getTotems(glove);
        var stored = gauntlet.storedTotems(glove);
        menu.refresh(stored); // A resurrection may have spent a totem while this view was open.
        if (raw < 0) return;
        if (raw >= top) {
            // Ordinary inventory/cursor operations remain usable. The bound glove cannot move.
            if (event.getSlot() == menu.sourceSlot() || gauntlet.isItem(event.getCurrentItem())
                    || event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND
                    || event.getClick() == ClickType.DOUBLE_CLICK || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true); return;
            }
            if (!event.isShiftClick()) return;
            event.setCancelled(true);
            ItemStack source = event.getCurrentItem();
            if (!totem(source) || count >= menu.totemSlotCount()) return;
            ItemStack one = source.clone(); one.setAmount(1); stored.add(one);
            if (gauntlet.storeMenuTotems(player, menu, count, stored)) {
                ItemStack remaining = source.clone(); remaining.setAmount(source.getAmount() - 1);
                event.getClickedInventory().setItem(event.getSlot(), remaining.getAmount() == 0 ? null : remaining);
                gauntlet.playTotemMenuSound(player, true);
            }
            return;
        }
        event.setCancelled(true);
        if (raw >= menu.totemSlotCount()) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
        boolean filled = raw < count;
        ItemStack cursor = event.getCursor();
        if (!filled && totem(cursor) && !event.isShiftClick() && count < menu.totemSlotCount()) {
            ItemStack one = cursor.clone(); one.setAmount(1); stored.add(one);
            if (gauntlet.storeMenuTotems(player, menu, count, stored)) {
                ItemStack remaining = cursor.clone(); remaining.setAmount(cursor.getAmount() - 1);
                event.getView().setCursor(remaining.getAmount() == 0 ? null : remaining);
                gauntlet.playTotemMenuSound(player, true);
            }
        } else if (filled) {
            if (event.isShiftClick()) {
                // No space means no removal and no drop at the player's feet.
                if (player.getInventory().firstEmpty() < 0) return;
                ItemStack taken = stored.remove(raw);
                if (gauntlet.storeMenuTotems(player, menu, count, stored)) {
                    var leftover = player.getInventory().addItem(taken);
                    if (!leftover.isEmpty()) { stored.add(raw, taken); gauntlet.storeMenuTotems(player, menu, count - 1, stored); return; }
                    gauntlet.playTotemMenuSound(player, false);
                }
            } else if (empty(cursor)) {
                ItemStack taken = stored.remove(raw);
                if (gauntlet.storeMenuTotems(player, menu, count, stored)) {
                    event.getView().setCursor(taken);
                    gauntlet.playTotemMenuSound(player, false);
                }
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TotemMenu menu)) return;
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < size)
                || event.getInventorySlots().contains(menu.sourceSlot())) event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof TotemMenu) event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        // Nothing to save: each accepted transaction already replaced the live inventory slot.
    }

    private static boolean totem(ItemStack item) { return !empty(item) && item.getType() == Material.TOTEM_OF_UNDYING; }
    private static boolean empty(ItemStack item) { return item == null || item.getType().isAir() || item.getAmount() <= 0; }
}
