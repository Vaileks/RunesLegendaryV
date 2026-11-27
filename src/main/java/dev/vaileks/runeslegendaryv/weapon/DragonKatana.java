package dev.vaileks.runeslegendaryv.weapon;

import dev.vaileks.runeslegendaryv.compat.Attributes;
import dev.vaileks.runeslegendaryv.compat.ServerCompat;
import dev.vaileks.runeslegendaryv.support.Cooldowner;
import dev.vaileks.runeslegendaryv.support.Feedback;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** The original Dragon Katana blink, with rechargeable uses and safe destinations. */
public final class DragonKatana extends AbstractLegendaryWeapon implements Listener {
    private final Cooldowner cooldown = new Cooldowner("dragon_katana");

    public DragonKatana(ULegendaryContext context) {
        super(context, "DragonKatana");
    }

    @Override
    protected ItemStack buildItem() {
        ItemStack item = super.buildItem();
        var meta = item.getItemMeta();
        meta.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                new AttributeModifier(
                        keys.attribute("dragon_katana_movement"),
                        config.getDouble("movement_multiplier", .1),
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                        EquipmentSlotGroup.MAINHAND));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void use(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !isItem(event.getItem())
                || !ServerCompat.isRightClick(event.getAction())
                || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        int refill = Math.max(1, Math.min(72000, config.getInt("cooldown_ticks", 340)));
        int charges = Math.max(1, Math.min(20, config.getInt("max_usages", 1)));
        long debt = cooldown.remaining(player, item);
        if (debt > (long) refill * 50 * (charges - 1)) {
            Feedback.waiting(player, debt - (long) refill * 50 * (charges - 1));
            return;
        }
        Location from = player.getLocation();
        Location destination = destination(player);
        if (destination == null || !player.teleport(destination)) return;
        int ticks = (int) ((debt + 49) / 50) + refill;
        cooldown.setCooldownWithoutIndicator(player, item, ticks);
        effect(from, player.getWidth(), player.getHeight());
        effect(player.getLocation(), player.getWidth(), player.getHeight());
    }

    public Location destination(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        int range = Math.max(1, Math.min(128, config.getInt("teleport_distance", 14)));
        Location desired = eye.clone().add(direction.clone().multiply(range));
        // Ordinary terrain can be crossed; bedrock and other unbreakable blocks stop the blink.
        BlockIterator blocks =
                new BlockIterator(eye.getWorld(), eye.toVector(), direction, 0, range);
        while (blocks.hasNext()) {
            var block = blocks.next();
            if (block.getType().getBlastResistance() <= 10000) continue;
            var hit = block.rayTrace(eye, direction, range, FluidCollisionMode.NEVER);
            if (hit != null) {
                desired = hit.getHitPosition().toLocation(eye.getWorld()).subtract(direction);
                break;
            }
        }
        return safeDestination(player, desired);
    }

    public static Location safeDestination(Player player, Location desired) {
        var world = desired.getWorld();
        if (world == null || !world.getWorldBorder().isInside(desired)) return null;
        double max = world.getMaxHeight() - player.getHeight();
        if (desired.getY() < world.getMinHeight() || desired.getY() > max) return null;
        Location result = desired.clone();
        Location origin = player.getLocation();
        while (result.getY() <= max) {
            BoundingBox box =
                    player.getBoundingBox()
                            .clone()
                            .shift(
                                    result.getX() - origin.getX(),
                                    result.getY() - origin.getY(),
                                    result.getZ() - origin.getZ());
            boolean blocked = false;
            for (int x = (int) Math.floor(box.getMinX());
                    x <= (int) Math.floor(box.getMaxX() - 1e-7) && !blocked;
                    x++)
                for (int y = (int) Math.floor(box.getMinY());
                        y <= (int) Math.floor(box.getMaxY() - 1e-7) && !blocked;
                        y++)
                    for (int z = (int) Math.floor(box.getMinZ());
                            z <= (int) Math.floor(box.getMaxZ() - 1e-7);
                            z++) {
                        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
                        var block = world.getBlockAt(x, y, z);
                        Material type = block.getType();
                        if (type == Material.LAVA
                                || type == Material.FIRE
                                || type == Material.SOUL_FIRE
                                || type == Material.POWDER_SNOW
                                || type == Material.CACTUS
                                || block.getCollisionShape()
                                        .overlaps(box.clone().shift(-x, -y, -z))) {
                            blocked = true;
                            break;
                        }
                    }
            if (!blocked) return result;
            result.setY(Math.floor(result.getY()) + 1);
        }
        return null;
    }

    private void effect(Location location, double width, double height) {
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);
        location.getWorld()
                .spawnParticle(
                        Particle.PORTAL,
                        location.clone().add(0, height / 2, 0),
                        150,
                        width / 2,
                        height / 2,
                        width / 2,
                        .25);
    }
}
