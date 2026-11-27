package test;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.support.Cooldowner;
import dev.vaileks.runeslegendaryv.weapon.DragonKatana;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;

import java.util.*;

public final class KatanaAudit {
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    static PlayerInteractEvent click(
            StandaloneSmoke.Avatar avatar, ItemStack item, EquipmentSlot hand) {
        return new PlayerInteractEvent(
                avatar.player,
                Action.RIGHT_CLICK_AIR,
                item,
                null,
                org.bukkit.block.BlockFace.SELF,
                hand);
    }

    static void run(RunesLegendaryV plugin) {
        var katana = (DragonKatana) plugin.weapons().get("DragonKatana");
        var world = Bukkit.getWorlds().getFirst();
        var at = world.getSpawnLocation().clone().add(0, 30, 0);
        at.setYaw(0);
        at.setPitch(0);
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                world.getChunkAt(at.getChunk().getX() + x, at.getChunk().getZ() + z).load();
        var a = new StandaloneSmoke.Avatar(at);
        ItemStack item = katana.getItem();
        a.slots[0] = item;
        var cd = new Cooldowner("dragon_katana");
        katana.use(click(a, item, EquipmentSlot.OFF_HAND));
        check(a.teleports == 0, "offhand event does not duplicate blink");
        a.teleportAllowed = false;
        katana.use(click(a, item, EquipmentSlot.HAND));
        check(cd.remaining(a.player, item) == 0, "cancelled teleport costs no charge");
        a.teleportAllowed = true;
        Location from = a.location.clone();
        katana.use(click(a, item, EquipmentSlot.HAND));
        check(a.teleports == 1 && a.location.distance(from) > 13, "original 14-block blink");
        check(cd.remaining(a.player, item) > 16000, "successful blink starts 17-second refill");
        katana.use(click(a, item, EquipmentSlot.HAND));
        check(a.teleports == 1, "blink blocked while recharging");
        cd.clear(a.player, item);
        plugin.itemConfig("DragonKatana").set("max_usages", 2);
        katana.use(click(a, item, EquipmentSlot.HAND));
        katana.use(click(a, item, EquipmentSlot.HAND));
        katana.use(click(a, item, EquipmentSlot.HAND));
        check(a.teleports == 3, "two configured charges then cooldown");
        plugin.itemConfig("DragonKatana").set("max_usages", 1);
        cd.clear(a.player, item);
        Location desired = at.clone().add(3, 0, 0);
        Block obstacle = desired.getBlock();
        var old = obstacle.getBlockData();
        try {
            obstacle.setType(Material.STONE, false);
            Location safe = DragonKatana.safeDestination(a.player, desired);
            check(
                    safe != null && safe.getY() >= obstacle.getY() + 1,
                    "solid destination lifted above collision");
            Location outside =
                    world.getWorldBorder().getCenter().add(world.getWorldBorder().getSize(), 30, 0);
            check(DragonKatana.safeDestination(a.player, outside) == null, "border never crossed");
            check(
                    DragonKatana.safeDestination(
                                    a.player, new Location(world, 0, world.getMaxHeight() + 1, 0))
                            == null,
                    "build height respected");
        } finally {
            obstacle.setBlockData(old, false);
        }
        plugin.getLogger().info("KATANA_PASS checks=" + checks);
    }
}
