package test;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.compat.ServerCompat;
import dev.vaileks.runeslegendaryv.support.Cooldowner;
import dev.vaileks.runeslegendaryv.support.DamageBridge;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

import java.lang.reflect.*;
import java.util.*;

public final class NewWeaponsAudit implements Listener {
    private static int checks, lightnings, ignites, explosions;
    private static RunesLegendaryV plugin;
    private static Arrow flying;
    private static Zombie prey;
    private static double initialX;
    private static boolean wasForced;
    private static org.bukkit.Chunk testChunk;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    @EventHandler
    public void lightning(LightningStrikeEvent event) {
        lightnings++;
        event.setCancelled(true);
    }

    @EventHandler
    public void ignite(BlockIgniteEvent event) {
        ignites++;
        event.setCancelled(true);
    }

    @EventHandler
    public void explode(EntityExplodeEvent event) {
        explosions++;
        event.setCancelled(true);
    }

    static EntityDamageByEntityEvent strike(
            StandaloneSmoke.Avatar owner, StandaloneSmoke.Avatar target, float strength) {
        plugin.reaperScythe().captureSwing(owner.player, target.player, strength);
        var hit =
                new EntityDamageByEntityEvent(
                        owner.player,
                        target.player,
                        EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                        1);
        plugin.reaperScythe().hit(hit);
        return hit;
    }

    static void run(RunesLegendaryV instance) throws Exception {
        plugin = instance;
        var world = Bukkit.getWorlds().getFirst();
        Location at = world.getSpawnLocation().clone().add(0, 40, 0);
        at.getChunk().load();
        testChunk = at.getChunk();
        wasForced = testChunk.isForceLoaded();
        testChunk.setForceLoaded(true);
        var owner = new StandaloneSmoke.Avatar(at);
        var target = new StandaloneSmoke.Avatar(at.clone().add(2, 0, 0));
        owner.health = 10;
        target.absorption = 4;
        target.effects.put(
                PotionEffectType.SPEED, new PotionEffect(PotionEffectType.SPEED, 200, 1));
        owner.slots[0] = plugin.reaperScythe().getItem();
        var cooldown = new Cooldowner("reaper_scythe");
        strike(owner, target, .2f);
        check(
                target.health == 20 && owner.health == 10 && target.absorption == 4,
                "spam clicks do not steal");
        var hit = strike(owner, target, 1);
        check(hit.getDamage() == 0, "special strike replaces ordinary damage");
        check(
                target.health == 18 && target.absorption == 0,
                "six damage consumes golden hearts first");
        check(owner.health == 16, "owner heals only the amount actually stolen");
        check(
                target.effects.isEmpty() && owner.effects.containsKey(PotionEffectType.SPEED),
                "active potion effects stolen");
        check(
                cooldown.remaining(owner.player, owner.slots[0]) > 54000,
                "55-second scythe cooldown");
        strike(owner, target, 1);
        check(target.health == 18, "cooldown prevents repeated theft");
        cooldown.clear(owner.player, owner.slots[0]);
        target.cancelDamage = true;
        target.effects.put(
                PotionEffectType.REGENERATION,
                new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        double hp = owner.health;
        strike(owner, target, 1);
        check(
                target.health == 18
                        && owner.health == hp
                        && target.effects.containsKey(PotionEffectType.REGENERATION),
                "cancelled damage steals nothing");
        check(
                cooldown.remaining(owner.player, owner.slots[0]) == 0,
                "denied strike consumes no cooldown");
        target.cancelDamage = false;
        DamageBridge.resurrected(target.player);
        strike(owner, target, 1);
        check(
                target.health == 12 && !target.effects.containsKey(PotionEffectType.REGENERATION),
                "scythe can execute through recent totem protection");
        DamageBridge.clear();
        var bow = plugin.artemisBow();
        ItemStack item = bow.getItem();
        check(
                item.getEnchantmentLevel(Enchantment.POWER) == 12
                        && item.getEnchantmentLevel(Enchantment.FLAME) == 2,
                "original bow enchantments");
        check(
                ServerCompat.model(item.getItemMeta())
                        .toString()
                        .equals("civilization:tools/artemis_bow/default"),
                "original Artemis model");
        owner.slots[0] = item;
        owner.sneaking = true;
        var toggle =
                new PlayerInteractEvent(
                        owner.player,
                        Action.LEFT_CLICK_AIR,
                        item,
                        null,
                        BlockFace.SELF,
                        EquipmentSlot.HAND);
        bow.toggle(toggle);
        var grief = new NamespacedKey("runeslegendaryv", "artemis_grief");
        check(
                !owner.slots[0]
                        .getItemMeta()
                        .getPersistentDataContainer()
                        .get(grief, PersistentDataType.BOOLEAN),
                "shift-left-click disables area effects");
        var cfg = plugin.itemConfig("ArtemisBow");
        cfg.set("auto_aim_weight", 0);
        for (String key : List.of("lightning_weight", "fire_ring_weight", "explosion_weight"))
            cfg.set(key, 1);
        var probe = new NewWeaponsAudit();
        Bukkit.getPluginManager()
                .registerEvents(probe, Bukkit.getPluginManager().getPlugin("StandaloneSmoke"));
        var arrow = world.spawn(at, Arrow.class);
        List<org.bukkit.block.BlockState> restore = new ArrayList<>();
        try {
            bow.shoot(
                    new EntityShootBowEvent(
                            owner.player,
                            owner.slots[0],
                            new ItemStack(Material.ARROW),
                            arrow,
                            EquipmentSlot.HAND,
                            1,
                            true));
            bow.impact(
                    new ProjectileHitEvent(
                            arrow, null, world.getBlockAt(at.clone().add(0, -1, 0)), BlockFace.UP));
            check(
                    lightnings == 0 && ignites == 0 && explosions == 0,
                    "mode off suppresses all random area effects");
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 2; z++) {
                    var block =
                            world.getBlockAt(
                                    at.getBlockX() + x, at.getBlockY() - 1, at.getBlockZ() + z);
                    restore.add(block.getState());
                    block.setType(Material.STONE, false);
                }
            bow.shoot(
                    new EntityShootBowEvent(
                            owner.player,
                            item,
                            new ItemStack(Material.ARROW),
                            arrow,
                            EquipmentSlot.HAND,
                            1,
                            true));
            // item was toggled in place; restore the area-mode flag explicitly for this shot.
            arrow.getPersistentDataContainer().set(grief, PersistentDataType.BOOLEAN, true);
            bow.impact(
                    new ProjectileHitEvent(
                            arrow, null, world.getBlockAt(at.clone().add(0, -1, 0)), BlockFace.UP));
            check(lightnings == 1, "actual lightning native event");
            check(ignites > 0, "fire ring uses cancellable ignition events");
            check(explosions == 1, "actual explosion native event");
            var canceled =
                    new EntityShootBowEvent(
                            owner.player,
                            bow.getItem(),
                            new ItemStack(Material.ARROW),
                            arrow,
                            EquipmentSlot.HAND,
                            1,
                            true);
            arrow.getPersistentDataContainer()
                    .remove(new NamespacedKey("runeslegendaryv", "artemis_arrow"));
            canceled.setCancelled(true);
            Bukkit.getPluginManager().callEvent(canceled);
            check(
                    !arrow.getPersistentDataContainer()
                            .has(new NamespacedKey("runeslegendaryv", "artemis_arrow")),
                    "cancelled shots untouched");
        } finally {
            arrow.remove();
            HandlerList.unregisterAll(probe);
            for (var state : restore) state.update(true, false);
            for (String key :
                    List.of(
                            "auto_aim_weight",
                            "lightning_weight",
                            "fire_ring_weight",
                            "explosion_weight")) cfg.set(key, null);
        }
        var ally = new StandaloneSmoke.Avatar(at.clone().add(1, 0, 0));
        var enemy = new StandaloneSmoke.Avatar(at.clone().add(5, 0, 0));
        var team =
                Bukkit.getScoreboardManager().getMainScoreboard().registerNewTeam("artemis_test");
        team.setAllowFriendlyFire(false);
        team.addEntry(owner.player.getName());
        team.addEntry(ally.player.getName());
        prey =
                world.spawn(
                        at.clone().add(6, 0, 0),
                        Zombie.class,
                        z -> {
                            z.setAI(false);
                            z.setSilent(true);
                        });
        try {
            var fake =
                    StandaloneSmoke.proxy(
                            AbstractArrow.class,
                            (o, m, a) ->
                                    switch (m.getName()) {
                                        case "getNearbyEntities" ->
                                                List.of(
                                                        owner.player,
                                                        ally.player,
                                                        prey,
                                                        enemy.player);
                                        case "getLocation" -> at.clone();
                                        default -> StandaloneSmoke.zero(m.getReturnType());
                                    });
            check(
                    bow.nearestTarget(fake, owner.player) == enemy.player,
                    "homing ignores owner and teammate, prioritizes an enemy player");
            enemy.gameMode = GameMode.CREATIVE;
            check(
                    bow.nearestTarget(fake, owner.player) == prey,
                    "creative players excluded, mobs eligible");
        } finally {
            team.unregister();
        }
        cfg.set("auto_aim_weight", 1);
        flying = world.spawn(at, Arrow.class);
        flying.setVelocity(new Vector(0, 0, 1.2));
        initialX = at.getX();
        bow.shoot(
                new EntityShootBowEvent(
                        owner.player,
                        bow.getItem(),
                        new ItemStack(Material.ARROW),
                        flying,
                        EquipmentSlot.HAND,
                        1,
                        true));
    }

    static void finish() throws Exception {
        try {
            check(
                    flying.isValid() && flying.getLocation().getX() > initialX + .1,
                    "live homing arrow curves toward its target");
            plugin.artemisBow().clear();
            Field field = plugin.artemisBow().getClass().getDeclaredField("flights");
            field.setAccessible(true);
            check(
                    ((Map<?, ?>) field.get(plugin.artemisBow())).isEmpty(),
                    "reload cleanup stops every homing flight");
            plugin.getLogger().info("NEW_WEAPONS_PASS checks=" + checks);
        } finally {
            testChunk.setForceLoaded(wasForced);
            flying.remove();
            prey.remove();
            plugin.itemConfig("ArtemisBow").set("auto_aim_weight", null);
        }
    }
}
