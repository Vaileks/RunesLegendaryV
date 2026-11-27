package dev.vaileks.runeslegendaryv.compat;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/** Samples before vanilla resets attack strength and watches native spyglass use. */
public final class SpigotCombatListener implements Listener {
    private final RunesLegendaryV plugin;
    private final Map<UUID, Float> strength = new HashMap<>();
    private final Map<UUID, Integer> pendingRelease = new HashMap<>();

    public SpigotCombatListener(RunesLegendaryV plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) tickPlayer(player);
    }

    void tickPlayer(Player player) {
        UUID id = player.getUniqueId();
        if (plugin.swordOfNature().isItem(player.getInventory().getItemInMainHand())
                || plugin.reaperScythe().isItem(player.getInventory().getItemInMainHand())
                || plugin.gambleSword().isItem(player.getInventory().getItemInMainHand())
                || plugin.gauntlet().isItem(player.getInventory().getItemInMainHand()))
            strength.put(id, player.getAttackCooldown());
        else strength.remove(id);
        if (!plugin.railGun().isCharging(player) && !plugin.railGun().isFullyCharged(player)) {
            pendingRelease.remove(id);
            return;
        }
        if (player.isHandRaised()) {
            if (!plugin.railGun().isItem(player.getItemInUse())) {
                pendingRelease.remove(id);
                plugin.railGun().cancelCharge(player);
                return;
            }
            pendingRelease.put(id, ServerCompat.tick());
        } else if (ServerCompat.tick()
                        - pendingRelease.computeIfAbsent(id, k -> ServerCompat.tick())
                >= 2) {
            pendingRelease.remove(id);
            plugin.railGun().release(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void attack(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof Player player)) return;
        Float sample = strength.remove(player.getUniqueId());
        if (plugin.gambleSword().isItem(player.getInventory().getItemInMainHand()))
            plugin.gambleSword()
                    .captureSwing(
                            player,
                            event.getEntity(),
                            sample == null ? player.getAttackCooldown() : sample);
        if (plugin.gauntlet().isItem(player.getInventory().getItemInMainHand()))
            plugin.gauntlet()
                    .captureSwing(
                            player,
                            event.getEntity(),
                            sample == null ? player.getAttackCooldown() : sample);
        if (plugin.reaperScythe().isItem(player.getInventory().getItemInMainHand()))
            plugin.reaperScythe()
                    .captureSwing(
                            player,
                            event.getEntity(),
                            sample == null ? player.getAttackCooldown() : sample);
        if (plugin.swordOfNature().isItem(player.getInventory().getItemInMainHand()))
            plugin.swordOfNature()
                    .captureSwing(
                            player,
                            event.getEntity(),
                            sample == null ? player.getAttackCooldown() : sample);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        strength.remove(event.getPlayer().getUniqueId());
        pendingRelease.remove(event.getPlayer().getUniqueId());
    }
}
