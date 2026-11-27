package dev.vaileks.runeslegendaryv.weapon.maces;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;

final class MaceSettings {
    static double number(
            ConfigurationSection config, String key, double fallback, double min, double max) {
        double value = config.getDouble(key, fallback);
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    static int integer(ConfigurationSection config, String key, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, config.getInt(key, fallback)));
    }

    static Color color(ConfigurationSection config) {
        try {
            return Color.fromRGB(
                    Integer.parseInt(
                            config.getString("flight.dust-color", "#132d85").replace("#", ""), 16));
        } catch (IllegalArgumentException e) {
            return Color.fromRGB(0x132d85);
        }
    }
}
