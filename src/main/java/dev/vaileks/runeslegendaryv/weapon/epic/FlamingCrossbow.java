package dev.vaileks.runeslegendaryv.weapon.epic;

import dev.vaileks.runeslegendaryv.weapon.AbstractLegendaryWeapon;
import dev.vaileks.runeslegendaryv.weapon.ULegendaryContext;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;

/** Applies Flame to crossbow arrows; vanilla crossbows otherwise ignore the Flame enchantment. */
public final class FlamingCrossbow extends AbstractLegendaryWeapon implements Listener {
    public FlamingCrossbow(ULegendaryContext context) {
        super(context, "FlamingCrossbow");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void shoot(EntityShootBowEvent event) {
        if (!isItem(event.getBow()) || !(event.getProjectile() instanceof AbstractArrow arrow))
            return;
        arrow.setFireTicks(
                Math.max(arrow.getFireTicks(), Math.max(1, config.getInt("fire_ticks", 100))));
    }
}
