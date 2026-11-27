package dev.vaileks.runeslegendaryv.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Persistent keys keep their original namespace so existing items survive renames. */
public final class Keys {
    private final NamespacedKey totemCount;

    public Keys(Plugin plugin) {
        this.totemCount = new NamespacedKey("runeslegendaryv", "totem_count");
    }

    public NamespacedKey totemCount() {
        return this.totemCount;
    }

    public NamespacedKey attribute(String string) {
        return new NamespacedKey("runeslegendaryv", string.toLowerCase(java.util.Locale.ROOT));
    }
}
