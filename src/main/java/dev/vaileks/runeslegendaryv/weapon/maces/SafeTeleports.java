package dev.vaileks.runeslegendaryv.weapon.maces;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.compat.ServerCompat;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

final class SafeTeleports implements Listener {
    private final RunesLegendaryV plugin;
    private final ConfigurationSection config;
    private final Map<UUID, UUID> pending = new HashMap<>();
    private final Map<UUID, Long> fallGrace = new HashMap<>();

    SafeTeleports(RunesLegendaryV plugin, ConfigurationSection config) {
        this.plugin = plugin;
        this.config = config;
    }

    void request(Player player, boolean border) {
        if (pending.containsKey(player.getUniqueId()) || !player.isOnline() || player.isDead())
            return;
        UUID token = UUID.randomUUID();
        pending.put(player.getUniqueId(), token);
        rift(player.getLocation());
        attempt(player, token, player.getLocation().clone(), border, 0);
    }

    private static void rift(Location center) {
        center.getWorld()
                .spawnParticle(
                        Particle.REVERSE_PORTAL,
                        center.clone().add(0, 1, 0),
                        18,
                        .35,
                        .65,
                        .35,
                        .025);
        for (int i = 0; i < 20; i++) {
            double a = i * Math.PI / 10;
            center.getWorld()
                    .spawnParticle(
                            Particle.DUST,
                            center.clone()
                                    .add(
                                            Math.cos(a) * .55,
                                            .15 + (i % 2) * 1.35,
                                            Math.sin(a) * .55),
                            1,
                            0,
                            0,
                            0,
                            0,
                            new Particle.DustOptions(
                                    Color.fromRGB(i % 2 == 0 ? 0x276ba6 : 0x8057c7), .7f));
        }
    }

    private boolean current(Player p, UUID token, World world) {
        return token.equals(pending.get(p.getUniqueId()))
                && p.isOnline()
                && !p.isDead()
                && p.getWorld().equals(world);
    }

    private void attempt(Player p, UUID token, Location origin, boolean border, int tries) {
        if (!current(p, token, origin.getWorld())) {
            pending.remove(p.getUniqueId(), token);
            return;
        }
        if (tries >= MaceSettings.integer(config, "teleport.attempts", 16, 1, 40)) {
            pending.remove(p.getUniqueId(), token);
            return;
        }
        var random = ThreadLocalRandom.current();
        World world = origin.getWorld();
        double[] xz;
        if (border) {
            WorldBorder wb = world.getWorldBorder();
            Location center = wb.getCenter();
            double maxInset =
                    Math.min(
                            MaceSettings.number(config, "teleport.border-max-inset", 24, 1, 512),
                            wb.getSize() / 4);
            double minInset =
                    Math.min(
                            MaceSettings.number(config, "teleport.border-min-inset", 8, 1, 512),
                            maxInset);
            if (minInset < 1) {
                pending.remove(p.getUniqueId(), token);
                return;
            }
            xz =
                    Mechanics.border(
                            center.getX(),
                            center.getZ(),
                            wb.getSize(),
                            random.nextInt(4),
                            random.nextDouble(),
                            minInset + (maxInset - minInset) * random.nextDouble());
        } else {
            double min = MaceSettings.number(config, "teleport.min-distance", 15, 1, 128),
                    max =
                            Math.max(
                                    min,
                                    MaceSettings.number(
                                            config, "teleport.max-distance", 20, 1, 128));
            xz =
                    Mechanics.local(
                            origin.getX(),
                            origin.getZ(),
                            random.nextDouble(Math.PI * 2),
                            min + (max - min) * random.nextDouble());
        }
        int x = (int) Math.floor(xz[0]), z = (int) Math.floor(xz[1]);
        ServerCompat.loadChunk(
                        world, x >> 4, z >> 4, config.getBoolean("teleport.allow-new-chunks", true))
                .whenComplete(
                        (chunk, error) -> {
                            if (!plugin.isEnabled()) return;
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (!current(p, token, world)) {
                                                    pending.remove(p.getUniqueId(), token);
                                                    return;
                                                }
                                                Location to =
                                                        error == null && chunk != null
                                                                ? surface(
                                                                        world,
                                                                        x,
                                                                        z,
                                                                        origin.getYaw(),
                                                                        origin.getPitch())
                                                                : null;
                                                if (to != null && !border) {
                                                    double dx = to.getX() - origin.getX(),
                                                            dz = to.getZ() - origin.getZ(),
                                                            distance = Math.hypot(dx, dz);
                                                    if (distance
                                                                    < MaceSettings.number(
                                                                            config,
                                                                            "teleport.min-distance",
                                                                            15,
                                                                            1,
                                                                            128)
                                                            || distance
                                                                    > MaceSettings.number(
                                                                            config,
                                                                            "teleport.max-distance",
                                                                            20,
                                                                            1,
                                                                            128)) to = null;
                                                }
                                                if (to == null) {
                                                    attempt(p, token, origin, border, tries + 1);
                                                    return;
                                                }
                                                Location destination = to;
                                                ServerCompat.teleportAsync(
                                                                p,
                                                                destination,
                                                                PlayerTeleportEvent.TeleportCause
                                                                        .PLUGIN)
                                                        .whenComplete(
                                                                (success, failure) -> {
                                                                    if (!plugin.isEnabled()) return;
                                                                    Bukkit.getScheduler()
                                                                            .runTask(
                                                                                    plugin,
                                                                                    () -> {
                                                                                        // A quit or
                                                                                        // a second
                                                                                        // request
                                                                                        // must
                                                                                        // never be
                                                                                        // overwritten by an older completion.
                                                                                        if (!token
                                                                                                .equals(
                                                                                                        pending
                                                                                                                .get(
                                                                                                                        p
                                                                                                                                .getUniqueId())))
                                                                                            return;
                                                                                        pending
                                                                                                .remove(
                                                                                                        p
                                                                                                                .getUniqueId());
                                                                                        if (failure
                                                                                                        == null
                                                                                                && Boolean
                                                                                                        .TRUE
                                                                                                        .equals(
                                                                                                                success)
                                                                                                && p
                                                                                                        .isOnline()
                                                                                                && !p
                                                                                                        .isDead()) {
                                                                                            p
                                                                                                    .setVelocity(
                                                                                                            new Vector());
                                                                                            p
                                                                                                    .setFallDistance(
                                                                                                            0);
                                                                                            long
                                                                                                    grace =
                                                                                                            System
                                                                                                                            .currentTimeMillis()
                                                                                                                    + 2500;
                                                                                            fallGrace
                                                                                                    .put(
                                                                                                            p
                                                                                                                    .getUniqueId(),
                                                                                                            grace);
                                                                                            Bukkit
                                                                                                    .getScheduler()
                                                                                                    .runTaskLater(
                                                                                                            plugin,
                                                                                                            () ->
                                                                                                                    fallGrace
                                                                                                                            .remove(
                                                                                                                                    p
                                                                                                                                            .getUniqueId(),
                                                                                                                                    grace),
                                                                                                            51);
                                                                                            rift(
                                                                                                    destination);
                                                                                            destination
                                                                                                    .getWorld()
                                                                                                    .playSound(
                                                                                                            destination,
                                                                                                            "minecraft:entity.enderman.teleport",
                                                                                                            .65f,
                                                                                                            .75f);
                                                                                        }
                                                                                    });
                                                                });
                                            });
                        });
    }

    static boolean hazard(Material type) {
        String n = type.name();
        return type == Material.LAVA
                || type == Material.WATER
                || type == Material.FIRE
                || type == Material.SOUL_FIRE
                || type == Material.CACTUS
                || type == Material.MAGMA_BLOCK
                || type == Material.POWDER_SNOW
                || type == Material.SWEET_BERRY_BUSH
                || type == Material.WITHER_ROSE
                || type == Material.POINTED_DRIPSTONE
                || n.contains("CAMPFIRE")
                || n.contains("PORTAL");
    }

    static Location surface(World world, int x, int z, float yaw, float pitch) {
        int floorY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        if (floorY < world.getMinHeight() || floorY + 3 >= world.getMaxHeight()) return null;
        Block floor = world.getBlockAt(x, floorY, z),
                feet = world.getBlockAt(x, floorY + 1, z),
                head = world.getBlockAt(x, floorY + 2, z);
        if (!floor.getType().isSolid()
                || hazard(floor.getType())
                || floor.isLiquid()
                || !feet.isPassable()
                || !head.isPassable()
                || feet.isLiquid()
                || head.isLiquid()
                || hazard(feet.getType())
                || hazard(head.getType())) return null;
        var box = floor.getBoundingBox();
        // Fences/walls and partial supports must not place feet inside collision or over an edge.
        if (box.getMaxY() > floorY + 1.001
                || box.getMaxY() < floorY + .999
                || box.getWidthX() < .6
                || box.getWidthZ() < .6) return null;
        Location to = new Location(world, x + .5, floorY + 1, z + .5, yaw, pitch);
        WorldBorder border = world.getWorldBorder();
        for (double dx : new double[] {-.35, .35})
            for (double dz : new double[] {-.35, .35})
                if (!border.isInside(to.clone().add(dx, 0, dz))) return null;
        return to;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fall(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)
                || e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Long until = fallGrace.remove(p.getUniqueId());
        if (until != null && until >= System.currentTimeMillis()) {
            e.setCancelled(true);
            p.setFallDistance(0);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        clear(e.getPlayer());
    }

    @EventHandler
    public void death(PlayerDeathEvent e) {
        clear(e.getEntity());
    }

    private void clear(Player p) {
        pending.remove(p.getUniqueId());
        fallGrace.remove(p.getUniqueId());
    }

    void disable() {
        pending.clear();
        fallGrace.clear();
    }
}
