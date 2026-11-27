package dev.vaileks.runeslegendaryv.weapon;

import dev.vaileks.runeslegendaryv.compat.ServerCompat;
import dev.vaileks.runeslegendaryv.support.Cooldowner;
import dev.vaileks.runeslegendaryv.support.DamageGuard;
import dev.vaileks.runeslegendaryv.support.Feedback;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Six guarded hits or a timed ward; recharge begins after the protection ends. */
public final class Excalibur extends AbstractLegendaryWeapon implements Listener {
    private static final class Ward {
        final Player player;
        final ItemStack sword;
        final int until;
        int hits, nextHit;

        Ward(Player player, ItemStack sword, int until, int hits) {
            this.player = player;
            this.sword = sword;
            this.until = until;
            this.hits = hits;
        }
    }

    private final Map<UUID, Ward> wards = new HashMap<>();
    private final Cooldowner cooldown = new Cooldowner("excalibur");

    public Excalibur(ULegendaryContext context) {
        super(context, "Excalibur");
    }

    private int ticks(String key, int fallback) {
        return Math.max(0, Math.min(72000, config.getInt(key, fallback)));
    }

    public void start() {
        context.tasks().globalTimer(1, 1, this::tick);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void use(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !isItem(event.getItem())
                || !ServerCompat.isRightClick(event.getAction())
                || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        event.setCancelled(true);
        activate(event.getPlayer(), event.getItem());
    }

    public boolean activate(Player player, ItemStack sword) {
        if (!isItem(sword)
                || wards.containsKey(player.getUniqueId())
                || Feedback.hasCooldown(cooldown, player, sword)) return false;
        int duration = Math.max(1, ticks("duration_ticks", 500));
        int hits = Math.max(1, Math.min(100, config.getInt("blocked_hits", 6)));
        // Reserve the worst-case end time so a disconnect or restart cannot reset the ward.
        cooldown.setCooldownWithoutIndicator(
                player, sword, duration + ticks("cooldown_ticks", 1040));
        wards.put(
                player.getUniqueId(),
                new Ward(player, sword, ServerCompat.tick() + duration, hits));
        return true;
    }

    public boolean block(Player player, double damage) {
        Ward ward = wards.get(player.getUniqueId());
        if (ward == null) return false;
        int now = ServerCompat.tick();
        if (now >= ward.until) {
            finish(ward);
            return false;
        }
        if (damage >= Math.max(0, config.getDouble("min_damage", 1.5)) && now >= ward.nextHit) {
            ward.nextHit = now + ticks("hit_interval_ticks", 10);
            ward.hits--;
            player.getWorld()
                    .playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1, 1);
            if (ward.hits <= 0) finish(ward);
        }
        return true;
    }

    public static boolean protectPureDamage(LivingEntity target, double damage) {
        var plugin = dev.vaileks.runeslegendaryv.RunesLegendaryV.instance();
        return target instanceof Player player
                && plugin != null
                && plugin.excalibur() != null
                && plugin.excalibur().block(player, damage);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && block(player, event.getDamage()))
            DamageGuard.protect(event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void projectile(ProjectileHitEvent event) {
        if (event.getHitEntity() instanceof Player player
                && block(player, config.getDouble("min_damage", 1.5))) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    private void tick() {
        int now = ServerCompat.tick();
        for (Ward ward : List.copyOf(wards.values())) {
            if (!ward.player.isOnline() || ward.player.isDead() || now >= ward.until) {
                finish(ward);
                continue;
            }
            if (now % 2 != 0) continue;
            Location centre = ward.player.getLocation().add(0, 1.3, 0);
            for (int i = 0; i < 16; i++) {
                double angle = Math.PI * 2 * i / 16;
                centre.getWorld()
                        .spawnParticle(
                                Particle.TOTEM_OF_UNDYING,
                                centre.clone().add(Math.cos(angle) * .25, 0, Math.sin(angle) * .25),
                                1,
                                0,
                                0,
                                0,
                                .05);
            }
            if (now % 20 == 0)
                centre.getWorld().playSound(centre, Sound.BLOCK_BEACON_AMBIENT, 1, 1);
        }
    }

    private void finish(Ward ward) {
        if (!wards.remove(ward.player.getUniqueId(), ward)) return;
        Feedback.setCooldown(cooldown, ward.player, ward.sword, ticks("cooldown_ticks", 1040));
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        Ward ward = wards.get(event.getPlayer().getUniqueId());
        if (ward != null) finish(ward);
    }

    @EventHandler
    public void death(PlayerDeathEvent event) {
        Ward ward = wards.get(event.getEntity().getUniqueId());
        if (ward != null) finish(ward);
    }

    public void clear() {
        for (Ward ward : List.copyOf(wards.values())) finish(ward);
    }
}
