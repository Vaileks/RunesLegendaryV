package test;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.compat.ServerCompat;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.*;
import java.util.*;

public final class MaceAudit {
    private static int checks;
    private static RunesLegendaryV plugin;
    private static StandaloneSmoke.Avatar glitchOwner;
    private static Location before;
    private static Object teleports;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }

    static Object call(Object object, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method m = object.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(object, args);
    }

    static void aim(StandaloneSmoke.Avatar avatar, Location start) throws Exception {
        // Opaque pixel near the centre of the sprite, not the surrounding display bounds.
        String[] mask;
        try (var r =
                new java.io.BufferedReader(
                        new java.io.InputStreamReader(plugin.getResource("tv-mask.txt")))) {
            mask = r.lines().toArray(String[]::new);
        }
        int x = -1, y = -1;
        outer:
        for (int row = 6; row < 11; row++)
            for (int col = 6; col < 11; col++)
                if (mask[row].charAt(col) == '1') {
                    x = col;
                    y = row;
                    break outer;
                }
        Location target =
                start.clone().add(((x + .5) / 16 - .5) * .85, ((15 - y + .5) / 16 - .5) * .85, 0);
        avatar.location.setDirection(
                target.toVector().subtract(avatar.player.getEyeLocation().toVector()));
    }

    static void run(RunesLegendaryV value) throws Exception {
        plugin = value;
        var tv = plugin.televisionMace();
        var glitch = plugin.glitchMace();
        ItemStack mace = tv.getItem();
        check(
                tv.cursed(mace)
                        && mace.getEnchantmentLevel(Enchantment.DENSITY) == 4
                        && mace.getEnchantmentLevel(Enchantment.WIND_BURST) == 2,
                "TV starts with original curse and enchantments");
        check(
                glitch.getItem().getItemMeta().getCustomModelData() == 138
                        && glitch.getItem().getEnchantmentLevel(Enchantment.WIND_BURST) == 1,
                "correct full-size Glitch model and enchantments");
        mace.addUnsafeEnchantment(Enchantment.DENSITY, 5);
        mace.addUnsafeEnchantment(Enchantment.BREACH, 4);
        tv.refreshText(mace);
        check(
                mace.getEnchantmentLevel(Enchantment.DENSITY) == 4
                        && mace.getEnchantmentLevel(Enchantment.BREACH) == 3,
                "enchantment caps retained");
        tv.setCursed(mace, false);
        check(!tv.cursed(mace), "uncursed TV supported");
        tv.setCursed(mace, true);
        var world = Bukkit.getWorlds().getFirst();
        Location at = world.getSpawnLocation().clone().add(0, 35, 0);
        at.getChunk().load();
        var owner = new StandaloneSmoke.Avatar(at);
        owner.slots[0] = mace;
        Object flying = field(plugin.maceCurses(), "flying");
        ItemStack expected = mace.clone();
        mace.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        owner.slots[0] = mace;
        check(
                (boolean)
                        call(
                                flying,
                                "launch",
                                new Class[] {
                                    Player.class, int.class, ItemStack.class, Location.class
                                },
                                owner.player,
                                0,
                                expected,
                                at.clone().add(3, 0, 0)),
                "original identity survives changed enchantments");
        check(owner.inv.getItemInMainHand().getType().isAir(), "launched TV removed once");
        Map<?, ?> flights = (Map<?, ?>) field(flying, "flights");
        Object flight = flights.values().iterator().next();
        check(
                (int) field(flight, "age") == 0,
                "catch tested immediately, before a tick or cooldown");
        aim(owner, (Location) field(flight, "start"));
        check(
                plugin.maceCurses().catchAt(owner.player),
                "owner catches immediately without cooldown");
        check(flights.isEmpty() && tv.isItem(owner.slots[0]), "payload returned exactly once");
        check(
                owner.slots[0].getEnchantmentLevel(Enchantment.UNBREAKING) == 3,
                "all current item metadata preserved");
        check(!plugin.maceCurses().catchAt(owner.player), "second click cannot duplicate payload");
        var other = new StandaloneSmoke.Avatar(at.clone().add(0, 0, 2));
        expected = owner.slots[0].clone();
        call(
                flying,
                "launch",
                new Class[] {Player.class, int.class, ItemStack.class, Location.class},
                owner.player,
                0,
                expected,
                at.clone().add(3, 0, 0));
        flight = flights.values().iterator().next();
        aim(other, (Location) field(flight, "start"));
        check(
                plugin.maceCurses().catchAt(other.player) && tv.isItem(other.slots[0]),
                "another player may catch immediately too");
        owner.slots[0] = other.slots[0];
        other.slots[0] = null;
        call(
                flying,
                "launch",
                new Class[] {Player.class, int.class, ItemStack.class, Location.class},
                owner.player,
                0,
                owner.slots[0].clone(),
                at.clone().add(3, 0, 0));
        flight = flights.values().iterator().next();
        Arrays.fill(other.slots, new ItemStack(Material.STONE, 64));
        aim(other, (Location) field(flight, "start"));
        check(
                !plugin.maceCurses().catchAt(other.player) && flights.size() == 1,
                "full inventory neither loses nor falsely catches TV");
        call(flying, "release", new Class[] {flight.getClass()}, flight);
        Item payload = (Item) field(flight, "item");
        check(
                payload.isValid() && payload.getPickupDelay() == 0,
                "released payload has no residual pickup cooldown");
        check(((ItemDisplay) field(flight, "display")).isDead(), "display cleaned on release");
        payload.remove();
        glitchOwner = new StandaloneSmoke.Avatar(at);
        glitchOwner.slots[0] = glitch.getItem();
        glitchOwner.attackStrength = 0;
        before = glitchOwner.location.clone();
        var config = plugin.itemConfig("GlitchMace");
        config.set("teleport_chance", 1);
        config.set("border_chance", 0);
        teleports = field(plugin.maceCurses(), "teleports");
        if (ServerCompat.paper()) {
            var event =
                    new io.papermc.paper.event.player.PrePlayerAttackEntityEvent(
                            glitchOwner.player, other.player, false);
            Bukkit.getPluginManager().callEvent(event);
            check(event.isCancelled(), "non-damaging rapid click keeps vanilla cancellation");
        } else plugin.maceCurses().attack(glitchOwner.player, other.player);
    }

    static void finish() throws Exception {
        try {
            check(glitchOwner.teleports == 1, "rapid click still rolls Glitch curse");
            double distance =
                    Math.hypot(
                            glitchOwner.location.getX() - before.getX(),
                            glitchOwner.location.getZ() - before.getZ());
            check(distance >= 15 && distance <= 20, "Glitch teleports its owner 15–20 blocks");
            check(
                    glitchOwner.location.getBlock().isPassable()
                            && glitchOwner
                                    .location
                                    .clone()
                                    .subtract(0, 1, 0)
                                    .getBlock()
                                    .getType()
                                    .isSolid(),
                    "Glitch destination has safe floor and headroom");
            var fall =
                    new EntityDamageEvent(
                            glitchOwner.player, EntityDamageEvent.DamageCause.FALL, 10);
            call(teleports, "fall", new Class[] {EntityDamageEvent.class}, fall);
            check(fall.isCancelled(), "successful Glitch grants temporary fall protection");
            plugin.getLogger().info("MACE_PASS checks=" + checks);
        } finally {
            plugin.itemConfig("GlitchMace").set("teleport_chance", null);
            plugin.itemConfig("GlitchMace").set("border_chance", null);
        }
    }
}
