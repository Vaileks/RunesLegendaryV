package dev.vaileks.runeslegendaryv.weapon.artemis;

import dev.vaileks.runeslegendaryv.compat.ServerCompat;
import dev.vaileks.runeslegendaryv.config.Language;
import dev.vaileks.runeslegendaryv.support.TeamAllies;
import dev.vaileks.runeslegendaryv.weapon.AbstractLegendaryWeapon;
import dev.vaileks.runeslegendaryv.weapon.ULegendaryContext;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Original weighted Artemis arrows, with bounded homing and independent impact rolls. */
public final class ArtemisBow extends AbstractLegendaryWeapon implements Listener {
    private record Flight(AbstractArrow arrow, Player owner, int launched) {}

    private final NamespacedKey marker = new NamespacedKey("runeslegendaryv", "artemis_arrow");
    private final NamespacedKey grief = new NamespacedKey("runeslegendaryv", "artemis_grief");
    private final NamespacedKey stopped = new NamespacedKey("runeslegendaryv", "artemis_stopped");
    private final Map<UUID, Flight> flights = new HashMap<>();

    public ArtemisBow(ULegendaryContext context) {
        super(context, "ArtemisBow");
    }

    @Override
    protected ItemStack buildItem() {
        ItemStack item = super.buildItem();
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer()
                .set(grief, PersistentDataType.BOOLEAN, config.getBoolean("grief_enabled", true));
        item.setItemMeta(meta);
        return item;
    }

    public void start() {
        context.tasks().globalTimer(1, 1, this::tick);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void shoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player owner)
                || !isItem(event.getBow())
                || !(event.getProjectile() instanceof AbstractArrow arrow)) return;
        arrow.getPersistentDataContainer().set(marker, PersistentDataType.BOOLEAN, true);
        arrow.getPersistentDataContainer()
                .set(
                        grief,
                        PersistentDataType.BOOLEAN,
                        event.getBow()
                                .getItemMeta()
                                .getPersistentDataContainer()
                                .getOrDefault(grief, PersistentDataType.BOOLEAN, true));
        arrow.getPersistentDataContainer().remove(stopped);
        if (roll("auto_aim_weight", 3))
            flights.put(arrow.getUniqueId(), new Flight(arrow, owner, ServerCompat.tick()));
    }

    private boolean roll(String setting, int fallback) {
        int weight = config.getInt(setting, fallback);
        return weight > 0 && ThreadLocalRandom.current().nextInt(weight) == 0;
    }

    private void tick() {
        Iterator<Flight> iterator = flights.values().iterator();
        while (iterator.hasNext()) {
            Flight flight = iterator.next();
            AbstractArrow arrow = flight.arrow;
            int age = ServerCompat.tick() - flight.launched;
            if (!arrow.isValid()
                    || arrow.isDead()
                    || arrow.isOnGround()
                    || arrow.isInBlock()
                    || age > 1200
                    || !flight.owner.isOnline()
                    || arrow.getPersistentDataContainer().has(stopped)) {
                iterator.remove();
                continue;
            }
            if (age < 3) continue;
            LivingEntity target = nearestTarget(arrow, flight.owner);
            if (target == null) continue;
            Vector toward =
                    target.getLocation().add(0, .5, 0).subtract(arrow.getLocation()).toVector();
            Vector velocity = arrow.getVelocity();
            if (toward.lengthSquared() < 1e-8 || velocity.lengthSquared() < 1e-8) continue;
            Vector direction = velocity.clone().normalize().add(toward.clone().normalize());
            if (direction.lengthSquared() < 1e-8) direction = toward;
            double speed = Math.max(.2, (velocity.length() - .05) * .99 - .05);
            arrow.setVelocity(direction.normalize().multiply(speed));
        }
    }

    public LivingEntity nearestTarget(AbstractArrow arrow, Player owner) {
        double range = Math.max(0, Math.min(32, config.getDouble("homing_range", 8)));
        LivingEntity nearest = null;
        Player nearestPlayer = null;
        double entityDistance = Double.MAX_VALUE, playerDistance = Double.MAX_VALUE;
        Entity vehicle = owner.getVehicle();
        for (Entity entity : arrow.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity living)
                    || entity.equals(owner)
                    || entity.equals(vehicle)
                    || living.isDead()
                    || !living.isValid()) continue;
            if (living instanceof Player player
                    && (player.getGameMode() != GameMode.SURVIVAL
                            || TeamAllies.areAllies(owner, player))) continue;
            double distance = arrow.getLocation().distanceSquared(living.getLocation());
            if (living instanceof Player player && distance < playerDistance) {
                nearestPlayer = player;
                playerDistance = distance;
            }
            if (distance < entityDistance) {
                nearest = living;
                entityDistance = distance;
            }
        }
        return nearestPlayer == null ? nearest : nearestPlayer;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void impact(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)
                || !arrow.getPersistentDataContainer()
                        .getOrDefault(marker, PersistentDataType.BOOLEAN, false)) return;
        arrow.getPersistentDataContainer().set(stopped, PersistentDataType.BOOLEAN, true);
        flights.remove(arrow.getUniqueId());
        if (event.getHitEntity() instanceof Player target && target.isBlocking())
            arrow.setPierceLevel(1);
        if (!arrow.getPersistentDataContainer()
                .getOrDefault(grief, PersistentDataType.BOOLEAN, true)) return;
        Location at = arrow.getLocation();
        if (roll("lightning_weight", 5)) at.getWorld().strikeLightning(at);
        if (roll("fire_ring_weight", 5)) fireRing(at, arrow);
        if (roll("explosion_weight", 16))
            at.getWorld()
                    .createExplosion(
                            at,
                            (float)
                                    Math.max(
                                            0,
                                            Math.min(
                                                    16, config.getDouble("explosion_power", 3.75))),
                            false,
                            config.getBoolean("break_blocks", true),
                            arrow);
    }

    private void fireRing(Location at, AbstractArrow arrow) {
        int radius = Math.max(0, Math.min(8, config.getInt("fire_ring_radius", 2)));
        for (int x = -radius; x <= radius; x++)
            for (int z = -radius; z <= radius; z++)
                for (int y = -1; y <= radius; y++) {
                    var block =
                            at.getWorld()
                                    .getBlockAt(
                                            at.getBlockX() + x,
                                            at.getBlockY() + y,
                                            at.getBlockZ() + z);
                    if (!block.getType().isAir()
                            || !block.getRelative(0, -1, 0).getType().isSolid()) continue;
                    var ignite =
                            new BlockIgniteEvent(block, BlockIgniteEvent.IgniteCause.ARROW, arrow);
                    context.plugin().getServer().getPluginManager().callEvent(ignite);
                    if (!ignite.isCancelled()) block.setType(Material.FIRE);
                    break;
                }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void toggle(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !isItem(event.getItem())
                || !event.getPlayer().isSneaking()
                || event.useItemInHand() == org.bukkit.event.Event.Result.DENY
                || (event.getAction() != Action.LEFT_CLICK_AIR
                        && event.getAction() != Action.LEFT_CLICK_BLOCK)) return;
        event.setCancelled(true);
        ItemStack item = event.getItem();
        var meta = item.getItemMeta();
        boolean enabled =
                !meta.getPersistentDataContainer()
                        .getOrDefault(grief, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(grief, PersistentDataType.BOOLEAN, enabled);
        item.setItemMeta(meta);
        event.getPlayer().getInventory().setItemInMainHand(item);
        ServerCompat.sendMessage(
                event.getPlayer(),
                Language.component(
                        enabled ? "messages.artemis-grief-on" : "messages.artemis-grief-off"));
        event.getPlayer()
                .playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1, 1);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        flights.values()
                .removeIf(f -> f.owner.getUniqueId().equals(event.getPlayer().getUniqueId()));
    }

    public void clear() {
        flights.clear();
    }
}
