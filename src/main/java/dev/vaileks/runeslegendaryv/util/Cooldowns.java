package dev.vaileks.runeslegendaryv.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class Cooldowns {
    private final Map<UUID, Map<String, Long>> data = new HashMap<UUID, Map<String, Long>>();

    public boolean ready(Player player, String string) {
        Map<String, Long> map = this.data.get(player.getUniqueId());
        if (map == null) {
            return true;
        }
        Long l = map.get(string);
        if (l == null) {
            return true;
        }
        if (System.currentTimeMillis() >= l) {
            map.remove(string);
            return true;
        }
        dev.vaileks.runeslegendaryv.support.Feedback.waiting(player,l-System.currentTimeMillis());
        return false;
    }

    public long remaining(Player player, String string) {
        Map<String, Long> map = this.data.get(player.getUniqueId());
        if (map == null) {
            return 0L;
        }
        Long l = map.get(string);
        if (l == null) {
            return 0L;
        }
        long l2 = l - System.currentTimeMillis();
        return l2 <= 0L ? 0L : l2;
    }

    public void set(Player player, String string, double d) {
        if (d <= 0.0) {
            this.clear(player, string);
        } else {
            this.data.computeIfAbsent(player.getUniqueId(), uUID -> new HashMap()).put(string, System.currentTimeMillis() + (long)(d * 1000.0));
            dev.vaileks.runeslegendaryv.support.Feedback.watch(player,"local:"+string,(long)(d*1000),player.getInventory().getItemInMainHand(),()->remaining(player,string));
        }
    }

    public void clear(Player player, String string) {
        dev.vaileks.runeslegendaryv.support.Feedback.cancel(player,"local:"+string);
        Map<String, Long> map = this.data.get(player.getUniqueId());
        if (map != null) {
            map.remove(string);
        }
    }

    public void clearAll(Player player) {
        this.data.remove(player.getUniqueId());
        dev.vaileks.runeslegendaryv.support.Feedback.clear(player);
    }
}

