package dev.vaileks.runeslegendaryv.util;

import java.util.Arrays;
import java.util.logging.Logger;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class Debug {
    private static boolean enabled;
    private static Logger logger;

    private Debug() {
    }

    public static void bind(Logger logger, boolean bl) {
        Debug.logger = logger;
        enabled = bl;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void log(String string) {
        if (enabled && logger != null) {
            logger.info("[debug] " + string);
        }
    }

    public static void item(String string, ItemStack itemStack) {
        if (enabled) {
            if (itemStack == null) {
                Debug.log(string + " item=null");
            } else {
                ItemMeta itemMeta = itemStack.getItemMeta();
                String string2 = itemMeta != null && itemMeta.hasDisplayName() ? itemMeta.getDisplayName() : "-";
                String string3 = itemMeta == null ? "-" : Arrays.toString(itemMeta.getPersistentDataContainer().getKeys().toArray());
                Debug.log(string + " type=" + String.valueOf(itemStack.getType()) + " amount=" + itemStack.getAmount() + " name=" + string2 + " pdc=" + string3);
            }
        }
    }
}

