package dev.vaileks.runeslegendaryv.weapon;

import dev.vaileks.runeslegendaryv.compat.Attributes;
import dev.vaileks.runeslegendaryv.support.AttackStrength;
import dev.vaileks.runeslegendaryv.support.Cooldowner;
import dev.vaileks.runeslegendaryv.support.DamageGuard;
import dev.vaileks.runeslegendaryv.support.Feedback;
import dev.vaileks.runeslegendaryv.support.TeamAllies;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A recovered melee strike steals health and active potion effects, without changing attributes.
 */
public final class ReaperScythe extends AbstractLegendaryWeapon implements Listener {
    private final Cooldowner cooldown = new Cooldowner("reaper_scythe");
    private final AttackStrength swings = new AttackStrength();
    private final Set<UUID> striking = new HashSet<>();

    public ReaperScythe(ULegendaryContext context) {
        super(context, "ReaperScythe");
    }

    public void captureSwing(Player owner, Entity target, float strength) {
        swings.capture(owner, target, strength);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof Player owner)
                || !(event.getEntity() instanceof Player target)
                || owner.equals(target)
                || striking.contains(owner.getUniqueId())
                || !isItem(owner.getInventory().getItemInMainHand())) return;
        float strength = swings.take(owner, target);
        if (strength < 1f || !DamageGuard.canDamage(target) || TeamAllies.areAllies(owner, target))
            return;
        var item = owner.getInventory().getItemInMainHand();
        if (Feedback.hasCooldown(cooldown, owner, item)) return;
        // The special strike replaces the ordinary hit, including when intercepted.
        event.setDamage(0);
        double amount = Math.max(0, Math.min(100, config.getDouble("hp_steal", 6)));
        if (!Double.isFinite(amount) || amount == 0) return;
        double before = target.getHealth() + target.getAbsorptionAmount();
        var effects = new ArrayList<>(target.getActivePotionEffects());
        striking.add(owner.getUniqueId());
        try {
            DamageGuard.execute(target, owner, amount);
        } finally {
            striking.remove(owner.getUniqueId());
        }
        double stolen =
                Math.min(
                        amount,
                        Math.max(0, before - target.getHealth() - target.getAbsorptionAmount()));
        if (stolen <= 0 && !target.isDead()) return;
        Feedback.setCooldown(
                cooldown,
                owner,
                item,
                Math.max(0, Math.min(72000, config.getInt("cooldown_ticks", 1100))));
        owner.getInventory().setItemInMainHand(item);
        if (!owner.isDead()) {
            var max = owner.getAttribute(Attributes.MAX_HEALTH);
            owner.setHealth(
                    Math.min(max == null ? 20 : max.getValue(), owner.getHealth() + stolen));
            for (var potion : effects) {
                owner.addPotionEffect(potion);
                target.removePotionEffect(potion.getType());
            }
        }
        Location at = target.getLocation().add(0, .8 * target.getHeight(), 0);
        target.getWorld().spawnParticle(Particle.SOUL, at, 35, .25, .25, .25, .05);
        target.getWorld().playSound(at, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1, 1);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        swings.clear(event.getPlayer());
        striking.remove(event.getPlayer().getUniqueId());
    }

    public void clear() {
        swings.clear();
        striking.clear();
    }
}
