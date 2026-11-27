package dev.vaileks.runeslegendaryv.weapon.maces;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.support.TeamAllies;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public final class MaceCurses implements Listener {
    private final RunesLegendaryV plugin;
    private final FlyingMaces flying;
    private final SafeTeleports teleports;

    public MaceCurses(RunesLegendaryV plugin) {
        this.plugin = plugin;
        try {
            flying = new FlyingMaces(plugin, plugin.itemConfig("TelevisionMace"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Missing television catch mask", e);
        }
        teleports = new SafeTeleports(plugin, plugin.itemConfig("GlitchMace"));
    }

    public void start() {
        flying.enable();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(teleports, plugin);
    }

    public boolean catchAt(Player player) {
        return flying.catchAt(player);
    }

    public void attack(Player owner, Entity attacked) {
        if (catchAt(owner)) return;
        if (!(attacked instanceof Player victim)
                || owner.equals(victim)
                || owner.getGameMode() == GameMode.SPECTATOR
                || TeamAllies.areAllies(owner, victim)) return;
        ItemStack held = owner.getInventory().getItemInMainHand();
        boolean tv = plugin.televisionMace().cursed(held);
        if (!tv && !plugin.glitchMace().cursed(held)) return;
        double roll = ThreadLocalRandom.current().nextDouble();
        if (tv) {
            if (roll
                    >= MaceSettings.number(
                            plugin.itemConfig("TelevisionMace"), "chance", .05, 0, 1)) return;
            int slot = owner.getInventory().getHeldItemSlot();
            var expected = held.clone();
            var target = victim.getLocation().clone();
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (owner.isOnline() && !owner.isDead())
                                    flying.launch(owner, slot, expected, target);
                            });
        } else {
            var config = plugin.itemConfig("GlitchMace");
            double border = MaceSettings.number(config, "border_chance", .01, 0, 1);
            var proc =
                    Mechanics.roll(
                            roll,
                            Math.min(
                                    1 - border,
                                    MaceSettings.number(config, "teleport_chance", .05, 0, 1)),
                            border);
            if (proc != Mechanics.Proc.NONE)
                plugin.getServer()
                        .getScheduler()
                        .runTask(
                                plugin,
                                () -> teleports.request(owner, proc == Mechanics.Proc.BORDER));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void spigotSwing(org.bukkit.event.player.PlayerAnimationEvent event) {
        if (dev.vaileks.runeslegendaryv.compat.ServerCompat.paper()
                || event.getAnimationType()
                        != org.bukkit.event.player.PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        if (catchAt(player)) {
            event.setCancelled(true);
            return;
        }
        if (!isMace(player.getInventory().getItemInMainHand())) return;
        var hit =
                player.getWorld()
                        .rayTrace(
                                player.getEyeLocation(),
                                player.getEyeLocation().getDirection(),
                                3,
                                org.bukkit.FluidCollisionMode.NEVER,
                                true,
                                .05,
                                e -> e instanceof Player && !e.equals(player));
        if (hit != null && hit.getHitEntity() != null) attack(player, hit.getHitEntity());
    }

    private boolean isMace(ItemStack item) {
        return plugin.televisionMace().isItem(item) || plugin.glitchMace().isItem(item);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void enchant(EnchantItemEvent event) {
        if (!isMace(event.getItem())) return;
        event.getEnchantsToAdd()
                .computeIfPresent(
                        org.bukkit.enchantments.Enchantment.DENSITY, (k, v) -> Math.min(v, 4));
        event.getEnchantsToAdd()
                .computeIfPresent(
                        org.bukkit.enchantments.Enchantment.BREACH, (k, v) -> Math.min(v, 3));
        MaceWeapon.cap(event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void anvil(PrepareAnvilEvent event) {
        if (isMace(event.getResult())) {
            MaceWeapon.cap(event.getResult());
            event.setResult(event.getResult());
        }
    }

    public void clear() {
        flying.disable();
        teleports.disable();
    }
}
