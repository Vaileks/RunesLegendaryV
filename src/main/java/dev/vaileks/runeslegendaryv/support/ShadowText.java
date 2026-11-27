package dev.vaileks.runeslegendaryv.support;

import net.kyori.adventure.text.Component;

public final class ShadowText {
    public static Component shadow(Component text) {
        Component result = text == null ? Component.empty() : text;
        try {
            Class<?> shadow = Class.forName("net.kyori.adventure.text.format.ShadowColor");
            Object color = shadow.getMethod("shadowColor", int.class).invoke(null, 0xff000000);
            return (Component)
                    Component.class.getMethod("shadowColor", shadow).invoke(result, color);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot format text shadow", e);
        }
    }

    public static Component legacy(String text) {
        return shadow(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand()
                        .deserialize(text == null ? "" : text.replace('§', '&')));
    }

    public static Component mini(String text) {
        return shadow(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize(text == null ? "" : text));
    }
}
