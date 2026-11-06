package dev.vaileks.runeslegendaryv.config;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class Settings {
    private final FileConfiguration config;
    private final Map<String, ConfigurationSection> sections = new HashMap<String, ConfigurationSection>();

    public Settings(FileConfiguration fileConfiguration) {
        this.config = fileConfiguration;
    }

    public FileConfiguration raw() {
        return this.config;
    }

    public ConfigurationSection section(String string2) {
        return this.sections.computeIfAbsent(string2, string -> this.config.getConfigurationSection(string));
    }

    public boolean has(String string) {
        return this.config.isConfigurationSection(string);
    }

    public String message(String string, String string2) {
        String string3 = this.config.getString("messages." + string);
        return string3 == null ? string2 : string3;
    }

    public float interactCooldown() {
        return (float)this.config.getDouble("interact_cooldown", 0.7);
    }
}
