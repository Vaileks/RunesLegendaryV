package dev.vaileks.runeslegendaryv.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

public final class SoundUtil {
    private static final Map<String, String> VANILLA =
            Map.of(
                    "bloodstone:unicorn_horn.presence", "entity.horse.ambient",
                    "bloodstone:bloodsword.transition", "block.beacon.power_select");
    private static boolean vanillaFallback = true;

    private SoundUtil() {}

    public static void bind(boolean bl) {
        vanillaFallback = bl;
    }

    public static void play(Location location, String string, float f, float f2) {
        Sound sound;
        if (location.getWorld() != null && (sound = SoundUtil.sound(string, f, f2)) != null) {
            location.getWorld()
                    .playSound(
                            location,
                            sound.name().asString(),
                            org.bukkit.SoundCategory.MASTER,
                            sound.volume(),
                            sound.pitch());
        }
    }

    public static void play(Player player, String string, float f, float f2) {
        Sound sound = SoundUtil.sound(string, f, f2);
        if (sound != null) {
            player.playSound(
                    player.getLocation(),
                    sound.name().asString(),
                    org.bukkit.SoundCategory.MASTER,
                    sound.volume(),
                    sound.pitch());
        }
    }

    public static void playAround(Location location, double d, String string, float f, float f2) {
        Sound sound;
        if (location.getWorld() != null && (sound = SoundUtil.sound(string, f, f2)) != null) {
            double d2 = d * d;
            for (Player player : location.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(location) > d2) continue;
                player.playSound(
                        player.getLocation(),
                        sound.name().asString(),
                        org.bukkit.SoundCategory.MASTER,
                        sound.volume(),
                        sound.pitch());
            }
        }
    }

    private static Sound sound(String string, float f, float f2) {
        return string != null && !string.isEmpty()
                ? Sound.sound(
                        Key.key(SoundUtil.key(string).toString()),
                        (Sound.Source) Sound.Source.MASTER,
                        (float) f,
                        (float) f2)
                : null;
    }

    private static NamespacedKey key(String string) {
        NamespacedKey namespacedKey;
        String string2 = string.toLowerCase(Locale.ROOT);
        if (string2.contains(":")
                && (namespacedKey = NamespacedKey.fromString((String) string2)) != null) {
            String string3;
            if (vanillaFallback
                    && !namespacedKey.getNamespace().equals("minecraft")
                    && (string3 = VANILLA.get(string2)) != null) {
                return NamespacedKey.minecraft((String) string3);
            }
            return namespacedKey;
        }
        return NamespacedKey.minecraft((String) string2);
    }
}
