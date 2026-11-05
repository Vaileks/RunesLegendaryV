package dev.vaileks.runeslegendaryv.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.util.ItemUtil;
import net.kyori.adventure.text.Component;

/** Server-wide language; translated text is separate from item mechanics. */
public final class Language {
    private static Language current;
    private final YamlConfiguration defaults;
    private final YamlConfiguration selected;
    private final String code;

    public Language(RunesLegendaryV plugin) {
        String requested = plugin.getConfig().getString("language", "ru").toLowerCase(Locale.ROOT).replace('-', '_');
        code = switch (requested) {
            case "ru", "ru_ru" -> "ru";
            case "en", "en_us", "en_gb" -> "en";
            default -> {
                plugin.getLogger().warning("Unknown language '" + requested + "'; using ru. Supported: ru, en.");
                yield "ru";
            }
        };
        for (String language : new String[]{"ru", "en"}) {
            String path = "lang/" + language + ".yml";
            if (!new File(plugin.getDataFolder(), path).exists()) plugin.saveResource(path, false);
        }
        defaults = bundled(plugin, "ru");
        selected = new YamlConfiguration();
        try {
            selected.load(new File(plugin.getDataFolder(), "lang/" + code + ".yml"));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load language file: " + code, error);
        }
        selected.setDefaults(bundled(plugin, code));
        current = this;
    }

    private static YamlConfiguration bundled(RunesLegendaryV plugin, String code) {
        try (var input = plugin.getResource("lang/" + code + ".yml")) {
            if (input == null) throw new IOException("Missing bundled language: " + code);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    public String code() { return code; }

    public static String text(String key, String... replacements) {
        String value = current.selected.getString(key);
        if (value == null) value = current.defaults.getString(key, key);
        if (replacements.length % 2 != 0) throw new IllegalArgumentException("Replacement pairs required");
        for (int i = 0; i < replacements.length; i += 2)
            value = value.replace("{" + replacements[i] + "}", replacements[i + 1]);
        return value;
    }

    public static Component component(String key, String... replacements) {
        return ItemUtil.parse(text(key, replacements));
    }

    public void applyItem(String key, YamlConfiguration item, File file) {
        ConfigurationSection section = defaults.getConfigurationSection("items." + key);
        if (section == null) return;
        boolean migrated = false;
        // Remove only known old defaults. Custom item text remains an explicit override.
        for (String path : section.getKeys(true)) {
            if (section.isConfigurationSection(path)) continue;
            if (item.isSet(path) && Objects.equals(item.get(path), section.get(path))) {
                item.set(path, null);
                migrated = true;
            }
        }
        if (migrated) {
            try { item.save(file); }
            catch (IOException error) { throw new IllegalStateException("Cannot migrate item text: " + file, error); }
        }
        for (String path : section.getKeys(true)) {
            if (!section.isConfigurationSection(path) && !item.isSet(path))
                item.set(path, Objects.requireNonNullElse(selected.get("items." + key + "." + path), section.get(path)));
        }
    }
}
