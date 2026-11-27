package dev.vaileks.runeslegendaryv.compat;

import org.bukkit.attribute.Attribute;

/** Attribute names changed in Minecraft 1.21.2. */
public final class Attributes {
    public static final Attribute ARMOR = find("ARMOR"),
            ARMOR_TOUGHNESS = find("ARMOR_TOUGHNESS"),
            ATTACK_DAMAGE = find("ATTACK_DAMAGE"),
            ATTACK_SPEED = find("ATTACK_SPEED"),
            MAX_HEALTH = find("MAX_HEALTH"),
            MAX_ABSORPTION = find("MAX_ABSORPTION"),
            MOVEMENT_SPEED = find("MOVEMENT_SPEED"),
            SCALE = find("SCALE");

    private static Attribute find(String name) {
        for (String field : new String[] {name, "GENERIC_" + name})
            try {
                return (Attribute) Attribute.class.getField(field).get(null);
            } catch (ReflectiveOperationException ignored) {
            }
        throw new IllegalStateException("Missing server attribute: " + name);
    }

    private Attributes() {}
}
