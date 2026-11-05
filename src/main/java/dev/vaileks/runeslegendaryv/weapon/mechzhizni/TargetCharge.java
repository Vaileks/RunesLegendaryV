package dev.vaileks.runeslegendaryv.weapon.mechzhizni;

import java.util.*;

/** One attacker's charge belongs to exactly one victim; times are injected for testing. */
public final class TargetCharge {
    private UUID target;
    private long lastHit = Long.MIN_VALUE;
    private final List<Point> points = new ArrayList<>();
    public void select(UUID victim) {
        if (!victim.equals(target)) { target = victim; points.clear(); lastHit = Long.MIN_VALUE; }
    }
    public boolean ready(long now, long interval) { return lastHit == Long.MIN_VALUE || now - lastHit >= interval; }
    public double bonus(long now) {
        points.removeIf(point -> point.expires <= now);
        return points.stream().mapToDouble(Point::damage).sum();
    }
    public void record(long now, long duration, double damage) {
        bonus(now); lastHit = now;
        points.add(new Point(now + duration, Math.max(0, damage)));
    }
    public UUID target() { return target; }
    private record Point(long expires, double damage) {}
}
