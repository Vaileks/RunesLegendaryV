package test;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.compat.ServerCompat;
import dev.vaileks.runeslegendaryv.config.Cfg;
import dev.vaileks.runeslegendaryv.support.DamageGuard;
import dev.vaileks.runeslegendaryv.weapon.gamble.GamblePower;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.potion.*;

import java.lang.reflect.Field;
import java.util.*;

public final class CombatRegressionAudit {
    private static int checks;
    private static RunesLegendaryV plugin;
    private static StandaloneSmoke.Avatar gambler, victim, deniedOwner, deniedVictim;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static EntityDamageByEntityEvent hit(
            StandaloneSmoke.Avatar owner, StandaloneSmoke.Avatar target) {
        return new EntityDamageByEntityEvent(
                owner.player, target.player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1);
    }

    private static Map<?, ?> sessions() throws Exception {
        Field f = plugin.gambleSword().getClass().getDeclaredField("sessions");
        f.setAccessible(true);
        return (Map<?, ?>) f.get(plugin.gambleSword());
    }

    static void run(RunesLegendaryV instance) throws Exception {
        plugin = instance;
        World world = Bukkit.getWorlds().getFirst();
        Location at = world.getSpawnLocation().clone().add(0, 50, 0);
        var owner = new StandaloneSmoke.Avatar(at);
        var target = new StandaloneSmoke.Avatar(at.clone().add(2, 0, 0));
        owner.slots[0] = plugin.gauntlet().getItem();
        owner.attackStrength = 0; // Native melee resets this before EntityDamageByEntityEvent.
        plugin.gauntlet().captureSwing(owner.player, target.player, 1);
        var bash = hit(owner, target);
        plugin.gauntlet().onAttack(owner.player, target.player, bash);
        check(bash.getDamage() == 8, "gauntlet uses pre-reset attack strength");
        var second = new StandaloneSmoke.Avatar(at);
        second.slots[0] = plugin.gauntlet().getItem();
        target.gameMode = GameMode.CREATIVE;
        var creative = hit(second, target);
        plugin.gauntlet().captureSwing(second.player, target.player, 1);
        plugin.gauntlet().onAttack(second.player, target.player, creative);
        check(creative.getDamage() == 1, "creative targets receive no gauntlet bash");
        check(
                !DamageGuard.execute(target.player, owner.player, 100),
                "creative immune to execution");
        target.gameMode = GameMode.SPECTATOR;
        check(
                !DamageGuard.apply(target.player, owner.player, 100),
                "spectators immune to pure damage");
        var cfg = new org.bukkit.configuration.file.YamlConfiguration();
        cfg.set("value", Double.NaN);
        check(
                Cfg.of(cfg).number("value", 4) == 4,
                "non-finite config cannot enter damage or velocity");
        cfg.set("value", Double.MAX_VALUE);
        check(Cfg.of(cfg).decimal("value", 1) == 1, "float overflow uses finite fallback");

        Zombie nativeTarget = world.spawn(at.clone().add(0, 5, 0), Zombie.class);
        try {
            nativeTarget.setSilent(true);
            nativeTarget.getEquipment().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            nativeTarget.setHealth(4);
            check(
                    DamageGuard.apply(nativeTarget, null, 6),
                    "ordinary native lethal damage accepted");
            check(!nativeTarget.isDead(), "ordinary damage still permits totems");
            nativeTarget.remove();
            nativeTarget = world.spawn(at.clone().add(0, 7, 0), Zombie.class);
            nativeTarget.getEquipment().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            nativeTarget.setHealth(4);
            check(DamageGuard.execute(nativeTarget, null, 6), "execution native damage accepted");
            check(nativeTarget.isDead(), "scythe execution bypasses a held totem");
            check(!DamageGuard.isExecution(nativeTarget), "execution context cleared after damage");
        } finally {
            nativeTarget.remove();
        }
        var frostOwner = new StandaloneSmoke.Avatar(at);
        var frostVictim = new StandaloneSmoke.Avatar(at);
        frostOwner.slots[0] = plugin.gambleSword().getItem();
        plugin.gambleSword().activate(frostOwner.player, frostOwner.slots[0], GamblePower.FROST);
        frostOwner.attackStrength = 0;
        plugin.gambleSword().captureSwing(frostOwner.player, frostVictim.player, 1);
        plugin.gambleSword().hit(hit(frostOwner, frostVictim));
        check(
                frostVictim.effects.containsKey(PotionEffectType.SLOWNESS),
                "lucky frost also uses pre-reset strength");
        plugin.gambleSword().clearAll();
        Field noFallField = plugin.gambleSword().getClass().getDeclaredField("noFall");
        noFallField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Long> noFall = (Map<UUID, Long>) noFallField.get(plugin.gambleSword());
        noFall.put(frostOwner.id, (long) ServerCompat.tick() - 1);
        var expiredFall =
                new EntityDamageEvent(frostOwner.player, EntityDamageEvent.DamageCause.FALL, 5);
        plugin.gambleSword().protect(expiredFall);
        check(!expiredFall.isCancelled(), "expired fall protection cannot cancel a fall");
        noFall.put(frostOwner.id, (long) ServerCompat.tick() + 20);
        var protectedFall =
                new EntityDamageEvent(frostOwner.player, EntityDamageEvent.DamageCause.FALL, 5);
        plugin.gambleSword().protect(protectedFall);
        check(protectedFall.isCancelled(), "active fall protection works");
        check(!noFall.containsKey(frostOwner.id), "fall protection is consumed once");
        var victim2 = new StandaloneSmoke.Avatar(at);
        victim2.cancelDamage = true;
        check(
                !DamageGuard.apply(victim2.player, owner.player, 8),
                "cancelled damage reports failure");
        check(victim2.health == 20, "cancelled damage cannot remove health");

        gambler = new StandaloneSmoke.Avatar(at);
        victim = new StandaloneSmoke.Avatar(at.clone().add(1, 0, 0));
        gambler.health = 10;
        victim.absorption = 2;
        victim.effects.put(
                PotionEffectType.SPEED, new PotionEffect(PotionEffectType.SPEED, 200, 1));
        gambler.slots[0] = plugin.gambleSword().getItem();
        plugin.gambleSword().activate(gambler.player, gambler.slots[0], GamblePower.REAPER);
        gambler.attackStrength = 0;
        plugin.gambleSword().captureSwing(gambler.player, victim.player, 1);
        plugin.gambleSword().hit(hit(gambler, victim));
        deniedOwner = new StandaloneSmoke.Avatar(at);
        deniedVictim = new StandaloneSmoke.Avatar(at.clone().add(1, 0, 0));
        deniedOwner.slots[0] = plugin.gambleSword().getItem();
        deniedVictim.cancelDamage = true;
        plugin.gambleSword().activate(deniedOwner.player, deniedOwner.slots[0], GamblePower.REAPER);
        plugin.gambleSword().captureSwing(deniedOwner.player, deniedVictim.player, 1);
        plugin.gambleSword().hit(hit(deniedOwner, deniedVictim));
        Bukkit.getScheduler()
                .runTaskLater(
                        Bukkit.getPluginManager().getPlugin("StandaloneSmoke"),
                        () -> {
                            try {
                                check(
                                        sessions().containsKey(deniedOwner.id),
                                        "blocked lucky scythe does not consume its power");
                                deniedVictim.cancelDamage = false;
                                plugin.gambleSword()
                                        .captureSwing(deniedOwner.player, deniedVictim.player, 1);
                                plugin.gambleSword().hit(hit(deniedOwner, deniedVictim));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        3);
    }

    static void finish() throws Exception {
        check(
                victim.health == 16 && victim.absorption == 0,
                "lucky scythe works after attack reset and consumes absorption first");
        check(gambler.health == 16, "lucky scythe heals exactly stolen health");
        check(
                gambler.effects.containsKey(PotionEffectType.SPEED) && victim.effects.isEmpty(),
                "lucky scythe transfers existing effects");
        check(!sessions().containsKey(gambler.id), "successful lucky scythe is consumed once");
        check(deniedVictim.health == 14, "blocked lucky scythe can retry a valid hit");
        check(!sessions().containsKey(deniedOwner.id), "retry consumes only successful power");
        plugin.gambleSword().clearAll();
        System.out.println("COMBAT_REGRESSION_PASS checks=" + checks);
    }
}
