package dev.vaileks.runeslegendaryv.support;

import dev.vaileks.runeslegendaryv.compat.ServerCompat;
import dev.vaileks.runeslegendaryv.weapon.perchatkastoikosti.Gauntlet;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DamageBridge {
    private static final Map<UUID, Long> resurrected = new HashMap<>();

    public static boolean justResurrected(LivingEntity target) {
        Long tick = resurrected.get(target.getUniqueId());
        if (tick == null) return false;
        if (ServerCompat.tick() - tick <= 1) return true;
        resurrected.remove(target.getUniqueId());
        return false;
    }

    public static void resurrected(LivingEntity target) {
        resurrected.put(target.getUniqueId(), (long) ServerCompat.tick());
    }

    public static boolean intercept(Player owner, Player target, double amount) {
        return justResurrected(target)
                || dev.vaileks.runeslegendaryv.weapon.Excalibur.protectPureDamage(target, amount)
                || Gauntlet.protectPureDamage(target, amount);
    }

    public static void clear() {
        resurrected.clear();
    }
}
