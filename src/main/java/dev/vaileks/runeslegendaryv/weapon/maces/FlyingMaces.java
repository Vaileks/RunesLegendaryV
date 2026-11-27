package dev.vaileks.runeslegendaryv.weapon.maces;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.compat.ServerCompat;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.*;

final class FlyingMaces implements Listener {
    private final RunesLegendaryV plugin;
    private final ConfigurationSection config;
    private final NamespacedKey payloadKey, displayKey, unlockKey;
    private final TextureHitbox mask;
    private final Map<UUID, Flight> flights = new HashMap<>();
    private BukkitTask ticker;

    private static final class Flight {
        final Item item;
        final ItemDisplay display;
        final Location start;
        final Vector end;
        final int duration, hover, liftTicks;
        final float scale;
        final double lift;
        int age;
        Quaternionf rotation = new Quaternionf();

        Flight(
                Item item,
                ItemDisplay display,
                Location start,
                Vector end,
                int duration,
                int hover,
                float scale,
                int liftTicks,
                double lift) {
            this.item = item;
            this.display = display;
            this.start = start;
            this.end = end;
            this.duration = duration;
            this.hover = hover;
            this.scale = scale;
            this.liftTicks = liftTicks;
            this.lift = lift;
        }
    }

    FlyingMaces(RunesLegendaryV plugin, ConfigurationSection config) throws IOException {
        this.plugin = plugin;
        this.config = config;
        payloadKey = new NamespacedKey("runeslegendaryv", "tv_flight_payload");
        displayKey = new NamespacedKey("runeslegendaryv", "tv_flight_visual");
        unlockKey = new NamespacedKey("runeslegendaryv", "tv_pickup_unlock_at");
        mask = new TextureHitbox(plugin.getResource("tv-mask.txt"));
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (World world : Bukkit.getWorlds()) for (Entity e : world.getEntities()) recover(e);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    boolean launch(Player player, int slot, ItemStack expected, Location target) {
        ItemStack live = player.getInventory().getItem(slot);
        // The triggering hit may consume durability, or an attribute swap may move the mace.
        // Compare its stable identity, then keep the CURRENT payload including new
        // durability/enchantments.
        if (!MaceWeapon.sameIdentity(live, expected)) {
            slot = -1;
            for (int i = 0; i < player.getInventory().getSize(); i++)
                if (MaceWeapon.sameIdentity(player.getInventory().getItem(i), expected)) {
                    slot = i;
                    break;
                }
            if (slot < 0) return false;
            live = player.getInventory().getItem(slot);
        }
        final ItemStack payload = live.clone();
        Location start = player.getLocation().add(0, 1.2, 0);
        start.setYaw(0);
        start.setPitch(0);
        double length = MaceSettings.number(config, "flight.distance", 3.5, 1, 8);
        Vector end =
                Mechanics.toward(
                        start.toVector(),
                        target.toVector(),
                        player.getLocation().getDirection(),
                        length);
        int duration = MaceSettings.integer(config, "flight.duration-ticks", 100, 40, 400),
                hover = MaceSettings.integer(config, "flight.hover-ticks", 400, 20, 1200);
        int liftTicks = MaceSettings.integer(config, "flight.lift-ticks", 20, 20, duration - 20);
        float scale = (float) MaceSettings.number(config, "flight.scale", .85, .2, 2);
        Item item = null;
        ItemDisplay display = null;
        try {
            item =
                    start.getWorld()
                            .dropItem(
                                    start,
                                    payload.clone(),
                                    e -> {
                                        ServerCompat.visible(plugin, e, false);
                                        e.setGravity(false);
                                        e.setInvulnerable(true);
                                        e.setVelocity(new Vector());
                                        e.setPickupDelay(Integer.MAX_VALUE);
                                        e.getPersistentDataContainer()
                                                .set(payloadKey, PersistentDataType.BYTE, (byte) 1);
                                        e.getPersistentDataContainer()
                                                .set(
                                                        unlockKey,
                                                        PersistentDataType.LONG,
                                                        System.currentTimeMillis());
                                    });
            display =
                    start.getWorld()
                            .spawn(
                                    start,
                                    ItemDisplay.class,
                                    e -> {
                                        e.setItemStack(payload.clone());
                                        e.setItemDisplayTransform(
                                                ItemDisplay.ItemDisplayTransform.NONE);
                                        e.setBillboard(Display.Billboard.FIXED);
                                        e.setBrightness(new Display.Brightness(15, 15));
                                        e.setPersistent(false);
                                        e.setInvulnerable(true);
                                        e.setGravity(false);
                                        e.setViewRange(1);
                                        e.setDisplayWidth(24);
                                        e.setDisplayHeight(
                                                6); // Rendering cull bounds, not a pickup hitbox.
                                        e.setInterpolationDelay(0);
                                        e.setInterpolationDuration(1);
                                        e.setTransformation(
                                                new Transformation(
                                                        new Vector3f(),
                                                        new Quaternionf(),
                                                        new Vector3f(scale),
                                                        new Quaternionf()));
                                        e.getPersistentDataContainer()
                                                .set(displayKey, PersistentDataType.BYTE, (byte) 1);
                                    });
            Flight flight =
                    new Flight(
                            item,
                            display,
                            start,
                            end,
                            duration,
                            hover,
                            scale,
                            liftTicks,
                            MaceSettings.number(config, "flight.lift-height", .7, .1, 3));
            flights.put(item.getUniqueId(), flight);
            player.getInventory().setItem(slot, null);
            return true;
        } catch (RuntimeException e) {
            if (item != null) item.remove();
            if (display != null) display.remove();
            plugin.getLogger().warning("Не удалось запустить вылет мейса: " + e.getMessage());
            return false;
        }
    }

    private void tick() {
        for (Flight f : List.copyOf(flights.values())) {
            if (!f.item.isValid() || !f.display.isValid() || ++f.age > f.duration + f.hover) {
                release(f);
                continue;
            }
            Vector offset = Mechanics.displacement(f.end, f.age, f.liftTicks, f.duration, f.lift);
            Location point = f.start.clone().add(offset);
            if (!point.getWorld().isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4)) {
                release(f);
                continue;
            }
            // Hidden payload moves only for persistence/recovery. The visual never teleports each
            // tick.
            if (!f.item.teleport(point)) {
                release(f);
                continue;
            }
            f.item.setVelocity(new Vector());
            f.rotation = Mechanics.rotation(f.age);
            f.display.setInterpolationDelay(0);
            f.display.setTransformation(
                    new Transformation(
                            offset.toVector3f(),
                            f.rotation,
                            new Vector3f(f.scale),
                            new Quaternionf()));
            if (f.age % 2 == 0)
                for (int side = 0; side < 2; side++) {
                    double angle = f.age * .32 + side * Math.PI;
                    point.getWorld()
                            .spawnParticle(
                                    Particle.DUST,
                                    point.clone()
                                            .add(Math.cos(angle) * .15, Math.sin(angle) * .15, 0),
                                    1,
                                    0,
                                    0,
                                    0,
                                    0,
                                    new Particle.DustOptions(
                                            Color.fromRGB(side == 0 ? 0x284eae : 0x4864bb), .55f));
                }
            point.getWorld()
                    .spawnParticle(
                            Particle.DUST,
                            point,
                            3,
                            .08,
                            .08,
                            .08,
                            0,
                            new Particle.DustOptions(
                                    MaceSettings.color(config),
                                    (float)
                                            MaceSettings.number(
                                                    config, "flight.dust-size", .65, .1, 2)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void swing(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) catchAt(event.getPlayer());
    }

    // Also used by actual attack packets: swinging at another entity must still permit catching.
    boolean catchAt(Player player) {
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR || flights.isEmpty())
            return false;
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double reach = MaceSettings.number(config, "flight.catch-reach", 3, .5, 4.5);
        var wall =
                eye.getWorld()
                        .rayTraceBlocks(eye, direction, reach, FluidCollisionMode.NEVER, true);
        if (wall != null) reach = Math.min(reach, wall.getHitPosition().distance(eye.toVector()));
        Flight nearest = null;
        double distance = reach;
        for (Flight f : flights.values()) {
            if (!Mechanics.canCatch(f.age)
                    || !f.start.getWorld().equals(eye.getWorld())
                    || !f.item.isValid()) continue;
            // Match the interpolated visual, without enlarging its opaque pixel hitbox.
            for (int frame = 0; frame <= 6; frame++) {
                double age = Math.max(0, f.age - frame * .5);
                Vector pos =
                        f.start
                                .toVector()
                                .add(
                                        Mechanics.displacement(
                                                f.end, age, f.liftTicks, f.duration, f.lift));
                double hit =
                        mask.intersect(
                                eye.toVector(),
                                direction,
                                pos,
                                Mechanics.rotation(age),
                                f.scale,
                                distance);
                if (hit < distance) {
                    distance = hit;
                    nearest = f;
                }
            }
        }
        if (nearest == null) return false;
        ItemStack payload = nearest.item.getItemStack();
        Map<Integer, ItemStack> rest = player.getInventory().addItem(payload.clone());
        boolean collected =
                rest.isEmpty()
                        || rest.values().stream().mapToInt(ItemStack::getAmount).sum()
                                < payload.getAmount();
        if (rest.isEmpty()) {
            nearest.item.remove();
            finish(nearest);
        } else {
            ItemStack remaining = rest.values().iterator().next();
            nearest.item.setItemStack(remaining);
            nearest.display.setItemStack(remaining.clone());
        }
        return collected;
    }

    private boolean marked(Item item) {
        return item.getPersistentDataContainer().has(payloadKey, PersistentDataType.BYTE);
    }

    private void recover(Entity entity) {
        if (entity.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) {
            entity.remove();
            return;
        }
        if (!(entity instanceof Item item) || !marked(item)) return;
        item.getPersistentDataContainer().remove(payloadKey);
        ServerCompat.visible(plugin, item, true);
        item.setGravity(true);
        item.setInvulnerable(false);
        item.getPersistentDataContainer().remove(unlockKey);
        item.setPickupDelay(0);
    }

    private void finish(Flight f) {
        flights.remove(f.item.getUniqueId());
        f.display.remove();
    }

    private void release(Flight f) {
        if (f.item.isValid()) recover(f.item);
        finish(f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (marked(event.getItem())) event.setCancelled(true);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        for (Flight flight : flights.values()) event.getPlayer().hideEntity(plugin, flight.item);
    }

    @EventHandler(ignoreCancelled = true)
    public void merge(ItemMergeEvent e) {
        if (marked(e.getEntity()) || marked(e.getTarget())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void hopper(InventoryPickupItemEvent e) {
        if (marked(e.getItem())) e.setCancelled(true);
    }

    @EventHandler
    public void unload(EntitiesUnloadEvent e) {
        for (Entity entity : e.getEntities())
            if (entity instanceof Item item) {
                Flight f = flights.get(item.getUniqueId());
                if (f != null) release(f);
                else recover(item);
            }
    }

    @EventHandler
    public void load(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities())
            if (!flights.containsKey(entity.getUniqueId())) recover(entity);
    }

    void disable() {
        if (ticker != null) ticker.cancel();
        for (Flight f : List.copyOf(flights.values())) release(f);
    }
}
