package dev.vaileks.runeslegendaryv.weapon.emerald;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.compat.ServerCompat;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** An open menu locks one sword by identity, including off-hand, hotbar and death transfers. */
public final class EmeraldMenu implements Listener {
    private static final NamespacedKey LOCK =
            new NamespacedKey("runeslegendaryv", "emerald_menu_session");
    private final RunesLegendaryV plugin;
    private final EmeraldBlade blade;
    private final Map<UUID, Session> sessions = new HashMap<>();

    private static final class Session implements InventoryHolder {
        final String id = UUID.randomUUID().toString();
        final Inventory inventory;

        Session(ItemStack sword, ItemStack[] contents) {
            inventory =
                    ServerCompat.createInventory(
                            this, contents.length, ServerCompat.displayName(sword.getItemMeta()));
            inventory.setContents(contents);
        }

        public Inventory getInventory() {
            return inventory;
        }
    }

    public EmeraldMenu(RunesLegendaryV plugin, EmeraldBlade blade) {
        this.plugin = plugin;
        this.blade = blade;
    }

    private boolean allowed(ItemStack item) {
        return item == null || item.getType().isAir() || item.getType() == blade.currency();
    }

    private boolean owns(ItemStack item, Session session) {
        return blade.isItem(item)
                && session.id.equals(
                        item.getItemMeta()
                                .getPersistentDataContainer()
                                .get(LOCK, PersistentDataType.STRING));
    }

    private Session session(HumanEntity owner, Inventory top) {
        Session value = sessions.get(owner.getUniqueId());
        return value != null && value.inventory == top ? value : null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void open(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack sword = event.getItem();
        if (!ServerCompat.isRightClick(event.getAction())
                || !player.isSneaking()
                || !blade.isItem(sword)
                || blade.fullByDefault()
                || sessions.containsKey(player.getUniqueId())
                || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        ItemStack[] contents = blade.contents(sword);
        List<ItemStack> returned = new ArrayList<>();
        for (int i = 0; i < contents.length; i++)
            if (!allowed(contents[i])) {
                returned.add(contents[i]);
                contents[i] = null;
            }
        Session session = new Session(sword, contents);
        var view = player.openInventory(session.inventory);
        if (view == null || view.getTopInventory() != session.inventory) return;
        var meta = sword.getItemMeta();
        meta.getPersistentDataContainer().set(LOCK, PersistentDataType.STRING, session.id);
        sword.setItemMeta(meta);
        int slot =
                event.getHand() == EquipmentSlot.OFF_HAND
                        ? 40
                        : player.getInventory().getHeldItemSlot();
        player.getInventory().setItem(slot, sword);
        sessions.put(player.getUniqueId(), session);
        event.setCancelled(true);
        for (ItemStack item : returned)
            for (ItemStack rest : player.getInventory().addItem(item).values())
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        Session session = session(event.getWhoClicked(), event.getView().getTopInventory());
        if (session == null) return;
        var owner = event.getWhoClicked();
        ItemStack hotbar =
                event.getClick() == ClickType.SWAP_OFFHAND
                        ? owner.getInventory().getItemInOffHand()
                        : event.getHotbarButton() >= 0
                                ? owner.getInventory().getItem(event.getHotbarButton())
                                : null;
        if (owns(event.getCurrentItem(), session)
                || owns(event.getCursor(), session)
                || owns(hotbar, session)) {
            event.setCancelled(true);
            return;
        }
        boolean top = event.getRawSlot() >= 0 && event.getRawSlot() < session.inventory.getSize();
        if (top && (!allowed(event.getCursor()) || !allowed(hotbar))
                || !top && event.isShiftClick() && !allowed(event.getCurrentItem()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        Session session = session(event.getWhoClicked(), event.getView().getTopInventory());
        if (session == null) return;
        for (int slot : event.getRawSlots())
            if (slot < session.inventory.getSize() && !allowed(event.getOldCursor())
                    || owns(event.getView().getItem(slot), session)
                    || owns(event.getOldCursor(), session)) {
                event.setCancelled(true);
                return;
            }
    }

    @EventHandler(ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) {
        Session s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null && owns(event.getItemDrop().getItemStack(), s)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent event) {
        Session s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null && (owns(event.getMainHandItem(), s) || owns(event.getOffHandItem(), s)))
            event.setCancelled(true);
    }

    private void save(ItemStack sword, Session session) {
        blade.save(sword, session.inventory.getContents());
        var meta = sword.getItemMeta();
        meta.getPersistentDataContainer().remove(LOCK);
        sword.setItemMeta(meta);
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        Session s = session(event.getPlayer(), event.getInventory());
        if (s == null) return;
        sessions.remove(event.getPlayer().getUniqueId());
        for (int slot = 0; slot < event.getPlayer().getInventory().getSize(); slot++) {
            ItemStack item = event.getPlayer().getInventory().getItem(slot);
            if (owns(item, s)) {
                save(item, s);
                event.getPlayer().getInventory().setItem(slot, item);
                return;
            }
        }
        ItemStack cursor = event.getView().getCursor();
        if (owns(cursor, s)) {
            save(cursor, s);
            event.getView().setCursor(cursor);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void death(PlayerDeathEvent event) {
        Session s = sessions.get(event.getEntity().getUniqueId());
        if (s != null) {
            for (ItemStack item : event.getDrops()) if (owns(item, s)) save(item, s);
            event.getEntity().closeInventory();
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        event.getPlayer().closeInventory();
    }

    public void clear() {
        for (UUID id : List.copyOf(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) player.closeInventory();
        }
        sessions.clear();
    }
}
