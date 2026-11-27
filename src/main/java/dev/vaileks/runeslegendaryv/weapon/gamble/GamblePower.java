package dev.vaileks.runeslegendaryv.weapon.gamble;

/** Stable configuration keys; translations are kept in lang/ instead of gameplay code. */
public enum GamblePower {
    LIGHT_MACE("light_mace", 0, 20),
    DRAGON("dragon", 0, 8),
    CRYSTAL("crystal", 800, 20),
    SPIDER("spider", 0, 20),
    EXCALIBUR("excalibur", 500, 20),
    SHADOW("shadow", 320, 30),
    SHRINKER("shrinker", 1200, 20),
    FROST("frost", 600, 20),
    MJOLNIR("mjolnir", 600, 20),
    REAPER("reaper", 400, 55),
    JACKPOT("jackpot", 822, 30);

    private final String key;
    private final int duration;
    private final double cooldown;

    GamblePower(String key, int duration, double cooldown) {
        this.key = key;
        this.duration = duration;
        this.cooldown = cooldown;
    }

    public String key() {
        return key;
    }

    public int duration() {
        return duration;
    }

    public double cooldown() {
        return cooldown;
    }
}
