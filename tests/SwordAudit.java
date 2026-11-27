package test;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.compat.ServerCompat;
import dev.vaileks.runeslegendaryv.support.Cooldowner;
import dev.vaileks.runeslegendaryv.support.DamageGuard;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;

import java.lang.reflect.*;
import java.util.*;

public final class SwordAudit {
    private static int checks;
    private static RunesLegendaryV plugin;
    private static StandaloneSmoke.Avatar expiring;

    static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    static void run(RunesLegendaryV value) throws Exception {
        plugin = value;
        var world = Bukkit.getWorlds().getFirst();
        Location at = world.getSpawnLocation().clone().add(0, 20, 0);
        var owner = new StandaloneSmoke.Avatar(at);
        var first = new StandaloneSmoke.Avatar(at.clone().add(2, 0, 0));
        var second = new StandaloneSmoke.Avatar(at.clone().add(3, 0, 0));
        var midas = plugin.midasSword();
        ItemStack sword = midas.getItem();
        check(sword.getEnchantmentLevel(Enchantment.SHARPNESS) == 5, "Midas starts at Sharpness V");
        check(
                midas.improve(sword, owner.player, first.player)
                        && sword.getEnchantmentLevel(Enchantment.SHARPNESS) == 6,
                "new player raises Midas Sharpness");
        check(
                !midas.improve(sword, owner.player, first.player)
                        && sword.getEnchantmentLevel(Enchantment.SHARPNESS) == 6,
                "repeat victim cannot farm Sharpness");
        byte[] encoded = ServerCompat.serializeAsBytes(sword);
        sword = ServerCompat.deserializeBytes(encoded);
        check(
                !midas.improve(sword, owner.player, first.player)
                        && midas.improve(sword, owner.player, second.player),
                "victim history persists across item storage");
        check(!sword.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS), "Midas Sharpness visible");
        owner.slots[0] = sword;
        LivingEntity mob =
                StandaloneSmoke.proxy(
                        LivingEntity.class,
                        (o, m, a) ->
                                switch (m.getName()) {
                                    case "getKiller" -> owner.player;
                                    default -> StandaloneSmoke.zero(m.getReturnType());
                                });
        var death =
                new EntityDeathEvent(
                        mob,
                        org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC)
                                .build(),
                        new ArrayList<>());
        midas.death(death);
        check(
                death.getDrops().stream()
                        .anyMatch(i -> i.getType() == Material.GOLD_NUGGET && i.getAmount() == 8),
                "mob kill awards eight gold nuggets");
        plugin.itemConfig("MidasSword").set("max_sharpness", 7);
        check(
                !midas.improve(sword, owner.player, new StandaloneSmoke.Avatar(at).player),
                "Midas maximum enforced");
        plugin.itemConfig("MidasSword").set("max_sharpness", null);
        var blade = plugin.emeraldBlade();
        ItemStack emerald = blade.getItem();
        ItemStack[] contents = new ItemStack[18];
        ItemStack custom = new ItemStack(Material.EMERALD_BLOCK, 64);
        var meta = custom.getItemMeta();
        ServerCompat.displayName(meta, net.kyori.adventure.text.Component.text("Stored emeralds"));
        custom.setItemMeta(meta);
        contents[0] = custom;
        blade.save(emerald, contents);
        check(
                emerald.getEnchantmentLevel(Enchantment.SHARPNESS) == 1,
                "one stack grants Sharpness I");
        check(blade.contents(emerald)[0].isSimilar(custom), "stored currency metadata retained");
        for (int i = 0; i < 18; i++) contents[i] = new ItemStack(Material.EMERALD_BLOCK, 64);
        blade.save(emerald, contents);
        check(
                emerald.getEnchantmentLevel(Enchantment.SHARPNESS) == 18,
                "full storage grants Sharpness XVIII");
        check(
                !emerald.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS),
                "Emerald Sharpness visible");
        var old = emerald.clone();
        contents[0] = new ItemStack(Material.DIAMOND);
        boolean denied = false;
        try {
            blade.save(emerald, contents);
        } catch (IllegalArgumentException e) {
            denied = true;
        }
        check(denied && emerald.equals(old), "invalid deposit rejected atomically");
        owner.slots[0] = emerald;
        owner.sneaking = true;
        var open =
                new PlayerInteractEvent(
                        owner.player,
                        Action.RIGHT_CLICK_AIR,
                        emerald,
                        null,
                        org.bukkit.block.BlockFace.SELF,
                        EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(open);
        check(
                owner.currentView != null && owner.currentView.getTopInventory().getSize() == 18,
                "Shift-right-click opens two-row Emerald storage");
        var view = owner.currentView;
        owner.cursor = new ItemStack(Material.DIAMOND);
        var click =
                new InventoryClickEvent(
                        view,
                        InventoryType.SlotType.CONTAINER,
                        0,
                        ClickType.LEFT,
                        InventoryAction.PLACE_ALL);
        Bukkit.getPluginManager().callEvent(click);
        check(click.isCancelled(), "foreign material blocked in GUI");
        owner.cursor = new ItemStack(Material.AIR);
        var lockClick =
                new InventoryClickEvent(
                        view,
                        InventoryType.SlotType.QUICKBAR,
                        18,
                        ClickType.SHIFT_LEFT,
                        InventoryAction.MOVE_TO_OTHER_INVENTORY);
        Bukkit.getPluginManager().callEvent(lockClick);
        check(lockClick.isCancelled(), "physical sword cannot move while storage open");
        view.getTopInventory().clear();
        view.getTopInventory().setItem(0, new ItemStack(Material.EMERALD_BLOCK, 64));
        view.getTopInventory().setItem(1, new ItemStack(Material.EMERALD_BLOCK, 64));
        owner.player.closeInventory();
        emerald = owner.slots[0];
        check(
                emerald.getEnchantmentLevel(Enchantment.SHARPNESS) == 2
                        && blade.contents(emerald)[1].getAmount() == 64,
                "closing saves only actual remaining resources");
        check(blade.contents(blade.getItem())[0] == null, "another sword has independent contents");
        var excalibur = plugin.excalibur();
        var protectedOwner = new StandaloneSmoke.Avatar(at);
        protectedOwner.slots[0] = excalibur.getItem();
        check(
                excalibur.activate(protectedOwner.player, protectedOwner.slots[0]),
                "Excalibur activates");
        check(
                !excalibur.activate(protectedOwner.player, protectedOwner.slots[0]),
                "active ward cannot restart");
        var cooldown = new Cooldowner("excalibur");
        check(
                cooldown.remaining(protectedOwner.player, protectedOwner.slots[0]) > 76000,
                "full duration plus recharge reserved");
        DamageGuard.apply(protectedOwner.player, null, 50);
        check(protectedOwner.health == 20, "Excalibur blocks lethal pure damage before totems");
        var wards = (Map<?, ?>) MaceAudit.field(excalibur, "wards");
        Object ward = wards.get(protectedOwner.id);
        check((int) MaceAudit.field(ward, "hits") == 5, "blocked hit consumes one guard");
        excalibur.block(protectedOwner.player, 3);
        check(
                (int) MaceAudit.field(ward, "hits") == 5,
                "same-tick hits do not consume several guards");
        for (int i = 0; i < 5; i++) {
            Field next = ward.getClass().getDeclaredField("nextHit");
            next.setAccessible(true);
            next.setInt(ward, ServerCompat.tick() - 1);
            check(
                    excalibur.block(protectedOwner.player, 3),
                    "each remaining guarded hit cancelled");
        }
        check(!excalibur.block(protectedOwner.player, 3), "ward ends after configured hit count");
        long remaining = cooldown.remaining(protectedOwner.player, protectedOwner.slots[0]);
        check(
                remaining > 51000 && remaining <= 52000,
                "52-second cooldown begins when final guard ends");
        plugin.itemConfig("Excalibur").set("duration_ticks", 1);
        expiring = new StandaloneSmoke.Avatar(at);
        expiring.slots[0] = excalibur.getItem();
        excalibur.activate(expiring.player, expiring.slots[0]);
        plugin.itemConfig("Excalibur").set("duration_ticks", null);
    }

    static void finish() {
        check(
                !plugin.excalibur().block(expiring.player, 3),
                "timed ward expires without further hits");
        long left = new Cooldowner("excalibur").remaining(expiring.player, expiring.slots[0]);
        check(left > 50000 && left <= 52000, "timed expiry starts normal recharge");
        plugin.getLogger().info("SWORDS_PASS checks=" + checks);
    }
}
