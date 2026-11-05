package dev.vaileks.runeslegendaryv.weapon.relsatron;

import dev.vaileks.runeslegendaryv.support.Scheduler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import dev.vaileks.runeslegendaryv.config.Cfg;
import dev.vaileks.runeslegendaryv.util.ItemUtil;
import dev.vaileks.runeslegendaryv.util.SoundUtil;
import dev.vaileks.runeslegendaryv.weapon.AbstractLegendaryWeapon;
import dev.vaileks.runeslegendaryv.weapon.ULegendaryContext;

public class RailGun
extends AbstractLegendaryWeapon {
    public static final String KEY = "relsatron";
    private final Set<UUID> charging = new HashSet<UUID>();
    private final Set<UUID> charged = new HashSet<UUID>();
    private final List<Projectile> projectiles = new ArrayList<Projectile>();
    private final Map<UUID, Long> noRegen = new HashMap<UUID, Long>();
    private final Map<UUID, Long> lastHitAt = new HashMap<UUID, Long>();
    private final Map<UUID, Scheduler.Task> chargeTasks = new HashMap<UUID, Scheduler.Task>();
    private final Map<UUID, Integer> chargeProgress = new HashMap<UUID, Integer>();
    private Scheduler.Task flightTask;

    public RailGun(ULegendaryContext uLegendaryContext) {
        super(uLegendaryContext, KEY);
    }

    private Cfg ability() {
        return Cfg.child(this.config, "ability");
    }

    private Cfg charge() {
        return Cfg.child(this.config, "charge");
    }

    private Cfg sounds() {
        return Cfg.child(this.config, "sounds");
    }

    private Material ammoMaterial() {
        return ItemUtil.material(this.config.getString("ammo_material", "ANVIL"), Material.ANVIL);
    }

    private int chargeTicks() {
        return Math.max(1, (int)Math.round(this.ability().number("charge_time", 10.0) * 20.0));
    }

    public void beginCharge(Player player) {
        if (this.charging.contains(player.getUniqueId()) || this.charged.contains(player.getUniqueId())) {
            return;
        }
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        if (this.isItem(itemStack)) {
            if (this.context.cooldowns().ready(player, "relsatron_shot") && this.context.abilityCooldown().ready(player, KEY, itemStack)) {
                Material ammoMaterial = this.ammoMaterial();
                if (!player.getInventory().contains(ammoMaterial)) {
                    String noAmmo = this.charge().string("no_ammo_text", "&c\u041d\u0435\u0442 \u0431\u043e\u0435\u043f\u0440\u0438\u043f\u0430\u0441\u0430: &f%ammo%");
                    player.sendActionBar(ItemUtil.parse(noAmmo.replace("%ammo%", ammoMaterial.name())));
                    SoundUtil.play(player, "block.note_block.bass", 0.6f, 0.6f);
                    return;
                }
                this.charging.add(player.getUniqueId());
                Cfg cfg = Cfg.child(this.sounds().raw(), "charge");
                SoundUtil.play(player, cfg.string("key", "block.beacon.power_select"), cfg.decimal("volume", 1.0f), cfg.decimal("pitch", 0.5f));
                int n = Math.max(0, (int)Math.round(this.ability().number("draw_time", 0.0) * 20.0));
                if (n <= 0) {
                    this.startCharging(player);
                } else {
                    this.context.tasks().globalLater(n, () -> {
                        if (this.charging.contains(player.getUniqueId()) && player.isOnline()) {
                            this.startCharging(player);
                        }
                    });
                }
            } else {
                Cfg cfg = Cfg.child(this.charge().raw(), "cooldown");
                player.sendActionBar(ItemUtil.parse(cfg.string("text", "&c\u0420\u0435\u043b\u044c\u0441\u0430\u0442\u0440\u043e\u043d \u043f\u0435\u0440\u0435\u0437\u0430\u0440\u044f\u0436\u0430\u0435\u0442\u0441\u044f")));
            }
        }
    }

    private void startCharging(Player player) {
        int n = this.chargeTicks();
        Cfg cfg = this.charge();
        int n2 = Math.max(1, cfg.integer("segments", 2));
        String string = cfg.string("bar_filled", "&a\u25b0");
        String string2 = cfg.string("bar_empty", "&7\u25b0");
        String string3 = cfg.string("prefix", "&e\u0417\u0430\u0440\u044f\u0434 &f");
        int[] nArray = new int[]{0};
        player.sendActionBar(ItemUtil.parse(string3 + RailGun.bar(0.0, n2, string, string2)));
        Scheduler.Task[] taskArray = new Scheduler.Task[1];
        Runnable runnable = () -> {
            if (this.charging.contains(player.getUniqueId()) && !player.isDead() && player.isOnline() && this.isItem(player.getInventory().getItemInMainHand())) {
                nArray[0] = nArray[0] + 2;
                this.chargeProgress.put(player.getUniqueId(), nArray[0]);
                double d = Math.min(1.0, (double)nArray[0] / (double)n);
                player.sendActionBar(ItemUtil.parse(string3 + RailGun.bar(d, n2, string, string2)));
                if (nArray[0] >= n) {
                    this.stopCharge(player, taskArray[0]);
                    this.charged.add(player.getUniqueId());
                    player.sendActionBar(ItemUtil.parse(cfg.string("charged_text", "&a\u0417\u0430\u0440\u044f\u0434 \u0433\u043e\u0442\u043e\u0432 &7- \u043e\u0442\u043f\u0443\u0441\u0442\u0438\u0442\u0435 \u041f\u041a\u041c")));
                    if (cfg.flag("auto_fire_on_full", false)) {
                        this.shoot(player, 1.0);
                        this.charged.remove(player.getUniqueId());
                    }
                }
            } else {
                this.stopCharge(player, taskArray[0]);
            }
        };
        taskArray[0] = this.context.tasks().globalTimer(1L, 2L, runnable);
        this.chargeTasks.put(player.getUniqueId(), taskArray[0]);
    }

    private void stopCharge(Player player, Scheduler.Task task) {
        this.charging.remove(player.getUniqueId());
        this.chargeProgress.remove(player.getUniqueId());
        this.chargeTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void cancelCharge(Player player) {
        this.stopCharge(player, this.chargeTasks.get(player.getUniqueId()));
    }

    public void release(Player player) {
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        if (!this.isItem(itemStack)) {
            this.stopCharge(player, this.chargeTasks.get(player.getUniqueId()));
            this.charged.remove(player.getUniqueId());
            return;
        }
        if (!this.charging.contains(player.getUniqueId())) {
            if (this.charged.remove(player.getUniqueId())) {
                this.shoot(player, 1.0);
            }
        } else {
            int n = this.chargeTicks();
            double d = Cfg.child(this.charge().raw(), "release_threshold").number("value", 1.0);
            double d2 = this.lastProgress(player, n);
            boolean bl = false;
            if (d2 >= d) {
                this.shoot(player, d2);
                bl = true;
            }
            this.stopCharge(player, this.chargeTasks.get(player.getUniqueId()));
            this.charged.remove(player.getUniqueId());
            if (!bl) {
                Cfg cfg = Cfg.child(this.charge().raw(), "weak_release");
                player.sendActionBar(ItemUtil.parse(cfg.string("text", "&c\u0417\u0430\u0440\u044f\u0434 \u043d\u0435\u043f\u043e\u043b\u043d\u044b\u0439 &7- \u043d\u0443\u0436\u043d\u043e \u0434\u043e\u0436\u0434\u0430\u0442\u044c\u0441\u044f 100%")));
                SoundUtil.play(player, cfg.string("sound", "block.note_block.bass"), cfg.decimal("volume", 0.6f), cfg.decimal("pitch", 0.6f));
            }
        }
    }

    private static String bar(double d, int n, String string, String string2) {
        double clamped = Math.max(0.0, Math.min(1.0, d));
        int n2 = clamped >= 1.0 ? n : (int)Math.floor(clamped * (double)n);
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < n; ++i) {
            stringBuilder.append(i < n2 ? string : string2);
        }
        return stringBuilder.toString();
    }

    private double lastProgress(Player player, int n) {
        Integer n2 = this.chargeProgress.get(player.getUniqueId());
        return n2 == null ? 0.0 : Math.min(1.0, (double)n2.intValue() / (double)n);
    }

    public boolean isCharging(Player player) {
        return this.charging.contains(player.getUniqueId());
    }

    public boolean isFullyCharged(Player player) {
        return this.charged.contains(player.getUniqueId());
    }

    private void shoot(Player player, double d) {
        if (player.isOnline()) {
            Material ammoMaterial = this.ammoMaterial();
            if (!player.getInventory().contains(ammoMaterial)) {
                return;
            }
            ItemStack itemStack = player.getInventory().getItemInMainHand();
            if (!this.isItem(itemStack)) {
                return;
            }
            player.getInventory().removeItem(new ItemStack[]{new ItemStack(ammoMaterial, 1)});
            double cooldown = this.config.getDouble("cooldown", 6.0);
            this.context.cooldowns().set(player, "relsatron_shot", cooldown);
            this.context.abilityCooldown().start(player, KEY, itemStack, cooldown);
            Cfg shootSound = Cfg.child(this.sounds().raw(), "shoot");
            SoundUtil.play(player.getLocation(), shootSound.string("key", "bloodstone:rail_gun.shot"), shootSound.decimal("volume", 1.0f), shootSound.decimal("pitch", 1.0f));
            Location origin = player.getEyeLocation().clone();
            Projectile projectile = new Projectile(player.getUniqueId(), origin, player.getLocation().getDirection().normalize().multiply(this.ability().number("projectile_speed", 4.0)));
            projectile.gravity = this.ability().number("ballistic_coefficient", 0.04);
            projectile.blockMaterial = this.ability().material("projectile_block", Material.ANVIL);
            projectile.scale = this.ability().number("projectile_scale", 0.6);
            projectile.hitRadius = this.ability().number("hit_radius", 0.35);
            projectile.maxTicks = this.ability().integer("max_flight_ticks", 200);
            projectile.baseDamage = this.ability().number("base_damage", 0.0);
            projectile.distanceCoefficient = this.ability().number("distance_damage_coefficient", 1.0);
            projectile.headshotMultiplier = this.ability().number("headshot_multiplier", 2.0);
            projectile.headshotFraction = this.ability().number("headshot_fraction", 0.8);
            projectile.power = d;
            projectile.spawn();
            this.projectiles.add(projectile);
        }
    }

    public void startFlightTask() {
        if (this.flightTask == null) {
            this.flightTask = this.context.tasks().globalTimer(1L, 1L, () -> {
                for (Projectile projectile : new ArrayList<Projectile>(this.projectiles)) {
                    if (this.step(projectile)) continue;
                    projectile.despawn();
                    this.projectiles.remove(projectile);
                }
            });
        }
    }

    private boolean step(Projectile projectile) {
        Cfg cfg = Cfg.child(this.sounds().raw(), "flight");
        if (projectile.ticks % Math.max(1, cfg.integer("interval_ticks", 2)) == 0) {
            SoundUtil.play(projectile.location, cfg.string("key", "entity.firework_rocket.launch"), cfg.decimal("volume", 0.6f), cfg.decimal("pitch", 1.0f));
        }
        Cfg cfg2 = Cfg.child(this.ability().raw(), "flight");
        int n = Math.max(1, cfg2.integer("substeps", 3));
        Location location = projectile.location;
        for (int i = 0; i < n; ++i) {
            Vector vector = projectile.velocity.clone().multiply(1.0 / (double)n);
            Location location2 = location.clone().add(vector);
            for (Object object : location.getWorld().getNearbyEntities(location, 2.0, 2.0, 2.0)) {
                LivingEntity livingEntity;
                if (!(object instanceof LivingEntity) || (livingEntity = (LivingEntity)object).getUniqueId().equals(projectile.owner) || !this.hitsSegment(location, location2, livingEntity, projectile.hitRadius)) continue;
                this.onProjectileHit(projectile, livingEntity, projectile.origin.distance(location2));
                return false;
            }
            if (location2.getBlock().getType().isSolid()) {
                Object object;
                Location location3 = location2.clone();
                location3.getWorld().spawnParticle(Particle.BLOCK, location3, 24, 0.2, 0.2, 0.2, 0.0, (Object)projectile.blockMaterial.createBlockData());
                object = Cfg.child(this.sounds().raw(), "hit");
                SoundUtil.play(location3, ((Cfg)object).string("key", "minecraft:block.anvil.land"), ((Cfg)object).decimal("volume", 1.0f), ((Cfg)object).decimal("pitch", 1.0f));
                return false;
            }
            if (location2.getY() < (double)(location.getWorld().getMinHeight() + 1)) {
                return false;
            }
            projectile.velocity = projectile.velocity.clone().setY(projectile.velocity.getY() - projectile.gravity / (double)n);
            location = location2;
        }
        projectile.location = location;
        if (++projectile.ticks > projectile.maxTicks) {
            return false;
        }
        projectile.updateDisplay();
        return true;
    }

    private boolean hitsSegment(Location location, Location location2, LivingEntity livingEntity, double d) {
        BoundingBox boundingBox = livingEntity.getBoundingBox().expand(d);
        if (!boundingBox.contains(location.toVector()) && !boundingBox.contains(location2.toVector())) {
            return this.crossesBox(location, location2, boundingBox) ? true : this.passesNear(location, location2, livingEntity, d);
        }
        return true;
    }

    private boolean crossesBox(Location location, Location location2, BoundingBox boundingBox) {
        Vector vector = location.toVector();
        Vector vector2 = location2.toVector().subtract(vector);
        double d = vector2.length();
        if (d < 1.0E-6) {
            return false;
        }
        vector2.normalize();
        int n = (int)Math.ceil(d * 2.0);
        for (int i = 1; i <= n; ++i) {
            if (!boundingBox.contains(vector.clone().add(vector2.clone().multiply(d * (double)i / (double)n)))) continue;
            return true;
        }
        return false;
    }

    private boolean passesNear(Location location, Location location2, LivingEntity livingEntity, double d) {
        Vector vector = livingEntity.getBoundingBox().getCenter();
        Vector vector2 = location.toVector();
        Vector vector3 = location2.toVector().subtract(vector2);
        double d2 = vector3.lengthSquared();
        if (d2 < 1.0E-9) {
            return false;
        }
        double d3 = Math.max(0.0, Math.min(1.0, vector.subtract(vector2).dot(vector3) / d2));
        return vector2.clone().add(vector3.multiply(d3)).distanceSquared(vector) <= d * d;
    }

    private void onProjectileHit(Projectile projectile, LivingEntity livingEntity, double d) {
        boolean bl;
        Player player = projectile.owner == null ? null : Bukkit.getPlayer((UUID)projectile.owner);
        double d2 = (projectile.baseDamage + Math.max(0.0, d - this.ability().number("distance_damage_start", 0.0)) * projectile.distanceCoefficient) * projectile.power;
        BoundingBox boundingBox = livingEntity.getBoundingBox();
        double d3 = Math.max(0.001, boundingBox.getHeight());
        boolean bl2 = bl = (projectile.location.getY() - boundingBox.getMinY()) / d3 >= projectile.headshotFraction;
        if (bl) {
            d2 *= projectile.headshotMultiplier;
            Cfg cfg = Cfg.child(this.sounds().raw(), "headshot");
            SoundUtil.play(livingEntity.getLocation(), cfg.string("key", "bloodstone:rail_gun.headshot"), cfg.decimal("volume", 1.0f), cfg.decimal("pitch", 1.0f));
            this.spawnHeadshotBlood(livingEntity);
            this.applyHeadshotEffect(player, livingEntity);
        } else {
            Cfg cfg = Cfg.child(this.sounds().raw(), "hit");
            SoundUtil.play(livingEntity.getLocation(), cfg.string("key", "minecraft:block.anvil.land"), cfg.decimal("volume", 1.0f), cfg.decimal("pitch", 1.0f));
        }
        if (player != null && player.isOnline()) {
            livingEntity.damage(d2, (Entity)player);
        } else {
            livingEntity.damage(d2);
        }
        this.lastHitAt.put(livingEntity.getUniqueId(), System.currentTimeMillis());
    }

    public boolean recentlyHit(LivingEntity livingEntity) {
        Long l = this.lastHitAt.get(livingEntity.getUniqueId());
        return l != null && System.currentTimeMillis() - l < 5000L;
    }

    private void spawnHeadshotBlood(LivingEntity livingEntity) {
        Cfg cfg = Cfg.child(this.ability().raw(), "headshot_blood");
        if (cfg.isPresent()) {
            livingEntity.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, livingEntity.getEyeLocation(), cfg.integer("count", 28), cfg.number("offset_x", 0.18), cfg.number("offset_y", 0.12), cfg.number("offset_z", 0.18), cfg.number("speed", 0.05));
        }
    }

    private void applyHeadshotEffect(Player player, LivingEntity livingEntity) {
        Player player2;
        PotionEffectType potionEffectType;
        Cfg cfg = Cfg.child(this.ability().raw(), "headshot_effect");
        PotionEffectType potionEffectType2 = potionEffectType = cfg.isPresent() ? cfg.effect("type") : null;
        if (potionEffectType == null) {
            potionEffectType = PotionEffectType.WITHER;
        }
        int n = (int)Math.round(cfg.number("duration", 10.0) * 20.0);
        livingEntity.addPotionEffect(new PotionEffect(potionEffectType, n, cfg.integer("amplifier", 0), false, true, true));
        double d = this.ability().number("headshot_uhc_duration", 10.0);
        if (d > 0.0 && livingEntity instanceof Player && (player2 = (Player)livingEntity).isOnline()) {
            this.noRegen.put(player2.getUniqueId(), System.currentTimeMillis() + (long)(d * 1000.0));
        }
    }

    public boolean isNaturalRegenBlocked(Player player) {
        Long l = this.noRegen.get(player.getUniqueId());
        if (l == null) {
            return false;
        }
        if (System.currentTimeMillis() > l) {
            this.noRegen.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void clear(Player player) {
        this.stopCharge(player, this.chargeTasks.get(player.getUniqueId()));
        this.charged.remove(player.getUniqueId());
        this.noRegen.remove(player.getUniqueId());
    }

    public void clearAll() {
        for (Scheduler.Task object : this.chargeTasks.values()) {
            object.cancel();
        }
        this.chargeTasks.clear();
        this.chargeProgress.clear();
        this.charging.clear();
        this.charged.clear();
        this.noRegen.clear();
        for (Projectile projectile : this.projectiles) {
            projectile.despawn();
        }
        this.projectiles.clear();
        if (this.flightTask != null) {
            this.flightTask.cancel();
            this.flightTask = null;
        }
    }

    static final class Projectile {
        final UUID owner;
        final Location origin;
        Location location;
        Vector velocity;
        double gravity;
        Material blockMaterial = Material.ANVIL;
        double scale = 0.6;
        double hitRadius = 0.35;
        int maxTicks = 200;
        double baseDamage = 0.0;
        double distanceCoefficient = 1.0;
        double headshotMultiplier = 2.0;
        double headshotFraction = 0.8;
        double power = 1.0;
        int ticks;
        ItemDisplay display;

        Projectile(UUID uUID, Location location, Vector vector) {
            this.owner = uUID;
            this.origin = location.clone();
            this.location = location;
            this.velocity = vector;
        }

        void despawn() {
            if (this.display != null) {
                this.display.remove();
            }
            this.display = null;
        }

        void spawn() {
            if (this.location.getWorld() != null) {
                this.display = (ItemDisplay)this.location.getWorld().spawn(this.location, ItemDisplay.class, itemDisplay -> {
                    itemDisplay.setItemStack(new ItemStack(this.blockMaterial));
                    itemDisplay.setBillboard(Display.Billboard.FIXED);
                    itemDisplay.setInterpolationDuration(1);
                    itemDisplay.setInterpolationDelay(0);
                    itemDisplay.setViewRange(4.0f);
                    itemDisplay.setPersistent(false);
                    itemDisplay.setTransformationMatrix(Projectile.scaleMatrix(this.scale));
                });
            }
        }

        void updateDisplay() {
            if (this.display != null && !this.display.isDead()) {
                Vector vector = this.velocity.clone().normalize();
                float f = (float)Math.toDegrees(Math.atan2(-vector.getX(), vector.getZ()));
                float f2 = (float)Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, vector.getY()))));
                this.display.setRotation(f, f2);
                this.display.setTransformationMatrix(Projectile.scaleMatrix(this.scale));
                this.display.teleport(this.location);
            }
        }

        private static Matrix4f scaleMatrix(double d) {
            return new Matrix4f().identity().scale((float)d);
        }
    }
}
