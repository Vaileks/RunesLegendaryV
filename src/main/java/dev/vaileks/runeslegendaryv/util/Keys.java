package dev.vaileks.runeslegendaryv.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class Keys {
    private final Plugin plugin;
    private final NamespacedKey totemCount;

    public Keys(Plugin plugin) {
        this.plugin = plugin;
        this.totemCount = new NamespacedKey(plugin, "totem_count");
    }

    public NamespacedKey totemCount() {
        return this.totemCount;
    }

    public NamespacedKey attribute(String string) {
        return new NamespacedKey(this.plugin, string);
    }
}
