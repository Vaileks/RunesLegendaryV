package dev.vaileks.runeslegendaryv.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.weapon.ULegendaryContext;

public final class PlayerSessionListener
implements Listener {
    private final RunesLegendaryV plugin;
    private final ULegendaryContext context;

    public PlayerSessionListener(RunesLegendaryV runesLegendaryV, ULegendaryContext uLegendaryContext) {
        this.plugin = runesLegendaryV;
        this.context = uLegendaryContext;
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        this.plugin.localizeInventory(playerJoinEvent.getPlayer().getInventory());
        this.plugin.gauntlet().syncStats(playerJoinEvent.getPlayer());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent playerRespawnEvent) {
        this.context.tasks().entityLater((Entity)playerRespawnEvent.getPlayer(), 1L, () -> this.plugin.gauntlet().syncStats(playerRespawnEvent.getPlayer()));
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent playerDeathEvent) {
        Player player = playerDeathEvent.getEntity();
        this.plugin.swordOfNature().clear(player);
        this.plugin.gauntlet().clear(player);
        this.plugin.railGun().clear(player);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        Player player = playerQuitEvent.getPlayer();
        this.plugin.swordOfNature().clear(player);
        this.plugin.gauntlet().clear(player);
        this.plugin.railGun().clear(player);
        this.context.cooldowns().clearAll(player);
    }
}
