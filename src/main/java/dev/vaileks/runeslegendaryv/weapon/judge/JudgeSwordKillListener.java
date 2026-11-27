package dev.vaileks.runeslegendaryv.weapon.judge;

import dev.vaileks.runeslegendaryv.compat.ServerCompat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class JudgeSwordKillListener implements Listener {
    private final JudgeSword sword;

    public JudgeSwordKillListener(JudgeSword sword, Object ignored) {
        this.sword = sword;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (ServerCompat.cancelled(event)) return;
        Player victim = event.getEntity(), killer = victim.getKiller();
        if (killer == null
                || !(victim.getLastDamageCause()
                        instanceof org.bukkit.event.entity.EntityDamageByEntityEvent hit)
                || hit.getDamager() != killer
                || hit.getCause()
                        != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK)
            return;
        if (sword.isItem(killer.getInventory().getItemInMainHand())) {
            var config =
                    dev.vaileks.runeslegendaryv.RunesLegendaryV.instance().itemConfig("JudgeSword");
            victim.getWorld()
                    .playSound(
                            victim.getLocation(),
                            "minecraft:entity.wither.spawn",
                            org.bukkit.SoundCategory.PLAYERS,
                            (float) config.getDouble("killSoundVolume", 3),
                            (float) config.getDouble("killSoundPitch", 1));
            ServerCompat.deathMessage(
                    event,
                    dev.vaileks.runeslegendaryv.config.Language.component(
                            "messages.judge-death",
                            "victim",
                            victim.getName(),
                            "killer",
                            killer.getName()));
        }
    }
}
