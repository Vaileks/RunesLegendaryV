package dev.vaileks.runeslegendaryv.support;

import dev.vaileks.runeslegendaryv.weapon.perchatkastoikosti.Gauntlet;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class DamageGuard implements Listener {
    private static final Set<EntityDamageEvent> protectedEvents =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<UUID> executions = new java.util.HashSet<>();

    private static final class Hit {
        final double amount;
        EntityDamageEvent event;
        boolean accepted;

        Hit(double amount) {
            this.amount = amount;
        }
    }

    private static final Map<UUID, Hit> pending = new HashMap<>();

    public static void protect(EntityDamageEvent event) {
        protectedEvents.add(event);
        event.setCancelled(true);
    }

    public static boolean wasProtected(EntityDamageEvent event) {
        return protectedEvents.contains(event);
    }

    public static boolean canDamage(LivingEntity target) {
        return target.isValid()
                && !target.isDead()
                && (!(target instanceof Player player)
                        || player.getGameMode() != org.bukkit.GameMode.CREATIVE
                                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR);
    }

    public static boolean isExecution(LivingEntity target) {
        return executions.contains(target.getUniqueId());
    }

    /** Only scythe life theft bypasses resurrection; ordinary damage retains native totems. */
    public static boolean execute(LivingEntity target, Player owner, double amount) {
        boolean added = executions.add(target.getUniqueId());
        try {
            return apply(target, owner, amount);
        } finally {
            if (added) executions.remove(target.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void executionResurrection(org.bukkit.event.entity.EntityResurrectEvent event) {
        if (isExecution(event.getEntity())) event.setCancelled(true);
    }

    public static boolean apply(LivingEntity target, Player owner, double amount) {
        if (!canDamage(target)
                || !Double.isFinite(amount)
                || amount <= 0
                || !isExecution(target) && DamageBridge.justResurrected(target)
                || dev.vaileks.runeslegendaryv.weapon.Excalibur.protectPureDamage(target, amount)
                || (isExecution(target)
                        ? Gauntlet.protectExecution(target, amount)
                        : Gauntlet.protectPureDamage(target, amount))) return false;
        Hit hit = new Hit(amount);
        Hit previous = pending.put(target.getUniqueId(), hit);
        int previousImmunity = target.getNoDamageTicks();
        target.setNoDamageTicks(0);
        try {
            target.damage(amount, owner);
        } finally {
            if (previous == null) pending.remove(target.getUniqueId());
            else pending.put(target.getUniqueId(), previous);
            if (!hit.accepted) target.setNoDamageTicks(previousImmunity);
        }
        return hit.accepted;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPure(EntityDamageEvent event) {
        Hit hit = pending.get(event.getEntity().getUniqueId());
        if (hit == null || hit.event != null || wasProtected(event)) return;
        hit.event = event;
        double amount = hit.amount;
        event.setDamage(amount);
        for (var m :
                new DamageModifier[] {
                    DamageModifier.ARMOR,
                    DamageModifier.RESISTANCE,
                    DamageModifier.MAGIC,
                    DamageModifier.HARD_HAT
                }) if (event.isApplicable(m)) event.setDamage(m, 0);
        if (event.isApplicable(DamageModifier.ABSORPTION)
                && event.getEntity() instanceof LivingEntity target)
            event.setDamage(
                    DamageModifier.ABSORPTION, -Math.min(amount, target.getAbsorptionAmount()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void confirm(EntityDamageEvent event) {
        Hit hit = pending.get(event.getEntity().getUniqueId());
        if (hit != null && hit.event == event)
            hit.accepted =
                    !event.isCancelled()
                            && !wasProtected(event)
                            && (event.getFinalDamage() > 0
                                    || event.isApplicable(DamageModifier.ABSORPTION)
                                            && event.getDamage(DamageModifier.ABSORPTION) < 0);
    }
}
