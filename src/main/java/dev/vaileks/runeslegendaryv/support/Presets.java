package dev.vaileks.runeslegendaryv.support;
import org.bukkit.configuration.file.FileConfiguration;import dev.vaileks.runeslegendaryv.RunesLegendaryV;
public final class Presets {public static FileConfiguration config(String ignored,String key){return RunesLegendaryV.instance().itemConfig(key);}}
