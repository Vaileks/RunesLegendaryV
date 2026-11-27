package test;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.compat.Attributes;
import dev.vaileks.runeslegendaryv.compat.ServerCompat;
import dev.vaileks.runeslegendaryv.weapon.gamble.GamblePower;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.*;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.*;

import java.lang.reflect.Field;
import java.util.*;

/** Exercises the new items on a real Paper server, alongside the original four weapons. */
public final class ExpansionAudit {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static String plain(net.kyori.adventure.text.Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    static void run(RunesLegendaryV plugin) throws Exception {
        check(plugin.weapons().size() == 15, "fifteen independently identified items");
        Set<String> epics =
                Set.of("FlamingCrossbow", "NetheriteShield", "TelevisionMace", "GlitchMace");
        for (var entry : plugin.weapons().entrySet()) {
            var meta = entry.getValue().getItem().getItemMeta();
            var rarity = ServerCompat.lore(meta).getFirst();
            check(
                    plain(rarity)
                            .equals(
                                    epics.contains(entry.getKey())
                                            ? "Эпический предмет"
                                            : "Легендарный предмет"),
                    "explicit rarity " + entry.getKey());
            check(
                    rarity.color()
                            .equals(
                                    epics.contains(entry.getKey())
                                            ? NamedTextColor.LIGHT_PURPLE
                                            : NamedTextColor.GOLD),
                    "rarity color " + entry.getKey());
        }
        var shield = plugin.weapons().get("NetheriteShield").getItem();
        for (var pair :
                Map.of(
                                Attributes.MAX_HEALTH,
                                4d,
                                Attributes.ARMOR,
                                4d,
                                Attributes.ARMOR_TOUGHNESS,
                                2d)
                        .entrySet()) {
            var mods = shield.getItemMeta().getAttributeModifiers(pair.getKey());
            check(mods != null && mods.size() == 1, "single shield modifier");
            var mod = mods.iterator().next();
            check(
                    mod.getAmount() == pair.getValue()
                            && mod.getSlotGroup() == EquipmentSlotGroup.OFFHAND,
                    "offhand-only shield stat");
        }
        var world = Bukkit.getWorlds().getFirst();
        Location at = world.getSpawnLocation().clone().add(0, 8, 0);
        at.getChunk().load();
        var avatar = new StandaloneSmoke.Avatar(at);
        Arrow arrow = world.spawn(at, Arrow.class);
        try {
            ItemStack flame = plugin.flamingCrossbow().getItem();
            var fire =
                    new EntityShootBowEvent(
                            avatar.player,
                            flame,
                            new ItemStack(Material.ARROW),
                            arrow,
                            EquipmentSlot.HAND,
                            1,
                            true);
            plugin.flamingCrossbow().shoot(fire);
            check(arrow.getFireTicks() >= 100, "flaming crossbow really ignites arrow");
            arrow.setFireTicks(0);
            var vanilla =
                    new EntityShootBowEvent(
                            avatar.player,
                            new ItemStack(Material.CROSSBOW),
                            new ItemStack(Material.ARROW),
                            arrow,
                            EquipmentSlot.HAND,
                            1,
                            true);
            plugin.flamingCrossbow().shoot(vanilla);
            check(arrow.getFireTicks() == 0, "ordinary crossbows unaffected");
        } finally {
            arrow.remove();
        }
        var gamble = plugin.gambleSword();
        int displaysBefore = world.getEntitiesByClass(ItemDisplay.class).size();
        for (GamblePower power : GamblePower.values()) {
            ItemStack item = gamble.getItem();
            int originalSharpness =
                    item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS);
            avatar.slots[0] = item;
            gamble.activate(avatar.player, item, power);
            check(
                    new dev.vaileks.runeslegendaryv.support.Cooldowner("gamble")
                                    .remaining(avatar.player, item)
                            > 0,
                    "reserved cooldown for " + power);
            if (power == GamblePower.JACKPOT) {
                ItemStack held = avatar.inv.getItemInMainHand();
                check(
                        held.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS)
                                == 18,
                        "jackpot sharpness");
                check(
                        ServerCompat.model(held.getItemMeta()).getKey().contains("gold"),
                        "jackpot alternate model");
                gamble.clearAll();
                check(
                        avatar.inv
                                        .getItemInMainHand()
                                        .getEnchantmentLevel(
                                                org.bukkit.enchantments.Enchantment.SHARPNESS)
                                == originalSharpness,
                        "sharpness restored on cleanup");
                check(
                        ServerCompat.model(avatar.inv.getItemInMainHand().getItemMeta())
                                .getKey()
                                .contains("default"),
                        "model restored on cleanup");
            } else gamble.clearAll();
            Field sessions = gamble.getClass().getDeclaredField("sessions");
            sessions.setAccessible(true);
            check(((Map<?, ?>) sessions.get(gamble)).isEmpty(), "session cleanup " + power);
            Field webs = gamble.getClass().getDeclaredField("webs");
            webs.setAccessible(true);
            check(((Map<?, ?>) webs.get(gamble)).isEmpty(), "temporary block cleanup " + power);
        }
        check(
                world.getEntitiesByClass(ItemDisplay.class).size() == displaysBefore,
                "no leaked ability displays");
        avatar.online = false;
        System.out.println("EXPANSION_PASS checks=" + checks);
    }
}
