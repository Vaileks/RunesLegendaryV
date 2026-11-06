package dev.vaileks.runeslegendaryv.util;

import dev.vaileks.runeslegendaryv.support.Scheduler;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class Tasks {
    private static final Runnable NOOP = () -> {};
    private final Scheduler scheduler;

    public Tasks(Plugin plugin) {
        this.scheduler = new Scheduler(plugin);
    }

    public boolean isFolia() {
        return this.scheduler.isFolia();
    }

    public Scheduler.Task later(Location location, long l, Runnable runnable) {
        return this.scheduler.runInRegionLater(location, l, runnable);
    }

    public Scheduler.Task timer(Location location, long l, long l2, Runnable runnable) {
        return this.scheduler.runInRegionAtFixedRate(location, l, l2, runnable);
    }

    public Scheduler.Task globalLater(long l, Runnable runnable) {
        return this.scheduler.runGlobalLater(l, runnable);
    }

    public Scheduler.Task globalTimer(long l, long l2, Runnable runnable) {
        return this.scheduler.runGlobalAtFixedRate(l, l2, runnable);
    }

    public Scheduler.Task entityLater(Entity entity, long l, Runnable runnable) {
        return this.scheduler.runAtEntityLater(entity, l, NOOP, runnable);
    }

    public Scheduler.Task entityTimer(Entity entity, long l, long l2, Runnable runnable) {
        return this.scheduler.runAtEntityAtFixedRate(entity, l, l2, NOOP, runnable);
    }
}
