package dev.vaileks.runeslegendaryv.weapon;
import org.bukkit.plugin.Plugin;import dev.vaileks.runeslegendaryv.config.Settings;import dev.vaileks.runeslegendaryv.util.*;
public record ULegendaryContext(Plugin plugin,Settings settings,Keys keys,Cooldowns cooldowns,AbilityCooldown abilityCooldown,Tasks tasks){}
