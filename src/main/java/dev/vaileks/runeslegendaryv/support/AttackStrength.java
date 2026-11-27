package dev.vaileks.runeslegendaryv.support;

import dev.vaileks.runeslegendaryv.compat.ServerCompat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Capture before vanilla resets attack strength; never reuse a sample for another target. */
public final class AttackStrength {
    private record Swing(UUID target, int tick, float strength) {}

    private final Map<UUID, Swing> swings = new HashMap<>();

    public void capture(Player owner, Entity target, float strength) {
        swings.put(
                owner.getUniqueId(),
                new Swing(target.getUniqueId(), ServerCompat.tick(), strength));
    }

    public float take(Player owner, Entity target) {
        Swing swing = swings.remove(owner.getUniqueId());
        int age = swing == null ? -1 : ServerCompat.tick() - swing.tick;
        float value =
                swing != null && swing.target.equals(target.getUniqueId()) && age >= 0 && age <= 1
                        ? swing.strength
                        : owner.getAttackCooldown();
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }

    public void clear(Player player) {
        swings.remove(player.getUniqueId());
    }

    public void clear() {
        swings.clear();
    }
}
