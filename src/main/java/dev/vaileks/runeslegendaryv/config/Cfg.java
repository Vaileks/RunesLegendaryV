package dev.vaileks.runeslegendaryv.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

public final class Cfg {
    private final ConfigurationSection section;

    private Cfg(ConfigurationSection configurationSection) {
        this.section = configurationSection;
    }

    public static Cfg of(ConfigurationSection configurationSection) {
        return new Cfg(configurationSection);
    }

    public static Cfg child(ConfigurationSection configurationSection, String string) {
        return new Cfg(configurationSection == null ? null : configurationSection.getConfigurationSection(string));
    }

    public ConfigurationSection raw() {
        return this.section;
    }

    public boolean isPresent() {
        return this.section != null;
    }

    public String string(String string, String string2) {
        if (this.section == null) {
            return string2;
        }
        String string3 = this.section.getString(string);
        return string3 == null ? string2 : string3;
    }

    public double number(String string, double d) {
        return this.section == null ? d : this.section.getDouble(string, d);
    }

    public float decimal(String string, float f) {
        return this.section == null ? f : (float)this.section.getDouble(string, (double)f);
    }

    public int integer(String string, int n) {
        return this.section == null ? n : this.section.getInt(string, n);
    }

    public boolean flag(String string, boolean bl) {
        return this.section == null ? bl : this.section.getBoolean(string, bl);
    }

    public List<String> strings(String string) {
        ArrayList<String> arrayList = new ArrayList<String>();
        if (this.section == null) {
            return arrayList;
        }
        List list = this.section.getList(string);
        if (list == null) {
            return arrayList;
        }
        for (Object e : list) {
            arrayList.add(String.valueOf(e));
        }
        return arrayList;
    }

    public List<Integer> integers(String string, List<Integer> list) {
        ArrayList<Integer> arrayList = new ArrayList<Integer>();
        if (this.section == null) {
            return list;
        }
        List list2 = this.section.getList(string);
        if (list2 == null) {
            return list;
        }
        for (Object e : list2) {
            if (e instanceof Number) {
                Number number = (Number)e;
                arrayList.add(number.intValue());
                continue;
            }
            try {
                arrayList.add(Integer.parseInt(String.valueOf(e).trim()));
            }
            catch (NumberFormatException numberFormatException) {}
        }
        return arrayList.isEmpty() ? list : arrayList;
    }

    public Material material(String string, Material material) {
        Material material2 = Material.matchMaterial((String)this.string(string, ""));
        return material2 == null ? material : material2;
    }

    public Particle particle(String string, Particle particle) {
        try {
            return Particle.valueOf((String)this.string(string, "").toUpperCase().replace(' ', '_'));
        }
        catch (IllegalArgumentException illegalArgumentException) {
            return particle;
        }
    }

    public PotionEffectType effect(String string) {
        String string2 = this.string(string, "");
        if (string2.isEmpty()) {
            return null;
        }
        for (PotionEffectType potionEffectType : PotionEffectType.values()) {
            if (potionEffectType.getKey().getKey().equalsIgnoreCase(string2.replace(' ', '_'))) {
                return potionEffectType;
            }
            if (!potionEffectType.getName().equalsIgnoreCase(string2.replace(' ', '_'))) continue;
            return potionEffectType;
        }
        return null;
    }
}
