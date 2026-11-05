package dev.vaileks.runeslegendaryv.listener;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.weapon.ULegendaryContext;

public final class EquipmentListener
implements Listener {
    private final RunesLegendaryV plugin;
    private final ULegendaryContext context;

    public EquipmentListener(RunesLegendaryV runesLegendaryV, ULegendaryContext uLegendaryContext) {
        this.plugin = runesLegendaryV;
        this.context = uLegendaryContext;
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent playerItemHeldEvent) {
        this.context.tasks().entityLater((Entity)playerItemHeldEvent.getPlayer(), 1L, () -> this.plugin.gauntlet().syncStats(playerItemHeldEvent.getPlayer()));
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onSwap(PlayerSwapHandItemsEvent playerSwapHandItemsEvent) {
        this.context.tasks().entityLater((Entity)playerSwapHandItemsEvent.getPlayer(), 1L, () -> this.plugin.gauntlet().syncStats(playerSwapHandItemsEvent.getPlayer()));
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent playerDropItemEvent) {
        this.context.tasks().entityLater((Entity)playerDropItemEvent.getPlayer(), 1L, () -> this.plugin.gauntlet().syncStats(playerDropItemEvent.getPlayer()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onWear(org.bukkit.event.player.PlayerItemDamageEvent event) {
        org.bukkit.inventory.ItemStack item=event.getItem();
        boolean glove=plugin.gauntlet().isItem(item);
        if(!(glove
                || (plugin.swordOfNature()!=null && plugin.swordOfNature().isItem(item))
                || (plugin.railGun()!=null && plugin.railGun().isItem(item))
                || plugin.judgeSword().isItem(item)))return;
        event.setCancelled(true);
        event.setDamage(0);
        org.bukkit.inventory.meta.ItemMeta meta=item.getItemMeta();
        if(meta!=null) {
            meta.setUnbreakable(true);
            if(meta instanceof org.bukkit.inventory.meta.Damageable damaged)damaged.setDamage(0);
            item.setItemMeta(meta);
        }
        if(glove)plugin.gauntlet().repairItem(item);
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if(event.getWhoClicked() instanceof org.bukkit.entity.Player player)syncLater(player);
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if(event.getWhoClicked() instanceof org.bukkit.entity.Player player)syncLater(player);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if(event.getPlayer() instanceof org.bukkit.entity.Player player)syncLater(player);
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if(event.getEntity() instanceof org.bukkit.entity.Player player && plugin.gauntlet().isItem(event.getItem().getItemStack()))syncLater(player);
    }

    private void syncLater(org.bukkit.entity.Player player) {
        context.tasks().entityLater(player,1L,()->{if(player.isOnline()&&!player.isDead())plugin.gauntlet().syncStats(player);});
    }
}

