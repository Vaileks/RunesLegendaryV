package dev.vaileks.runeslegendaryv.compat;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import org.bukkit.event.*;

public final class PaperCombatListener implements Listener {
    private final RunesLegendaryV plugin;

    public PaperCombatListener(RunesLegendaryV plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void attack(PrePlayerAttackEntityEvent event) {
        if (event.willAttack()
                && plugin.swordOfNature()
                        .isItem(event.getPlayer().getInventory().getItemInMainHand()))
            plugin.swordOfNature().captureSwing(event.getPlayer(), event.getAttacked());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void reaperAttack(PrePlayerAttackEntityEvent event) {
        if (event.willAttack()
                && plugin.reaperScythe()
                        .isItem(event.getPlayer().getInventory().getItemInMainHand()))
            plugin.reaperScythe()
                    .captureSwing(
                            event.getPlayer(),
                            event.getAttacked(),
                            event.getPlayer().getAttackCooldown());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void extraAttack(PrePlayerAttackEntityEvent event) {
        if (!event.willAttack()) return;
        var player = event.getPlayer();
        var item = player.getInventory().getItemInMainHand();
        float strength = player.getAttackCooldown();
        if (plugin.gambleSword().isItem(item))
            plugin.gambleSword().captureSwing(player, event.getAttacked(), strength);
        if (plugin.gauntlet().isItem(item))
            plugin.gauntlet().captureSwing(player, event.getAttacked(), strength);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void maceAttack(PrePlayerAttackEntityEvent event) {
        if (!event.isCancelled() || !event.willAttack())
            plugin.maceCurses().attack(event.getPlayer(), event.getAttacked());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void release(PlayerStopUsingItemEvent event) {
        if (plugin.railGun().isItem(event.getItem())) plugin.railGun().release(event.getPlayer());
    }
}
