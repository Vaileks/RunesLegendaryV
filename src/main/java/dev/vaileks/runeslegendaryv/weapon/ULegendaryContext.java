package dev.vaileks.runeslegendaryv.weapon;

import dev.vaileks.runeslegendaryv.config.Settings;
import dev.vaileks.runeslegendaryv.util.AbilityCooldown;
import dev.vaileks.runeslegendaryv.util.Cooldowns;
import dev.vaileks.runeslegendaryv.util.Keys;
import dev.vaileks.runeslegendaryv.util.Tasks;

import org.bukkit.plugin.Plugin;

public record ULegendaryContext(
        Plugin plugin,
        Settings settings,
        Keys keys,
        Cooldowns cooldowns,
        AbilityCooldown abilityCooldown,
        Tasks tasks) {}
