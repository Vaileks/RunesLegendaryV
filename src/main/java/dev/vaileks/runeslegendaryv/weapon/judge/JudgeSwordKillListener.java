package dev.vaileks.runeslegendaryv.weapon.judge;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class JudgeSwordKillListener implements Listener {
    private final JudgeSword sword;
    public JudgeSwordKillListener(JudgeSword sword,Object ignored){this.sword=sword;}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onPlayerDeath(PlayerDeathEvent event){
        if(event.isCancelled())return;
        Player victim=event.getEntity(),killer=victim.getKiller();
        if(killer==null||!(victim.getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent hit)
                ||hit.getDamager()!=killer||hit.getCause()!=org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK)return;
        if(sword.isItem(killer.getInventory().getItemInMainHand())) {
            var config=dev.vaileks.runeslegendaryv.support.Presets.config("CustomSpeedItems","JudgeSword");
            victim.getWorld().playSound(victim.getLocation(),"minecraft:entity.wither.spawn",org.bukkit.SoundCategory.PLAYERS,
                    (float)config.getDouble("killSoundVolume",3),(float)config.getDouble("killSoundPitch",1));
            event.deathMessage(Component.text(victim.getName()+" пал от Судного меча игрока "+killer.getName(),NamedTextColor.WHITE));
        }
    }
}
