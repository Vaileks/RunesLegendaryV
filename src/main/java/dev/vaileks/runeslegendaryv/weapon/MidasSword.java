package dev.vaileks.runeslegendaryv.weapon;

import dev.vaileks.runeslegendaryv.support.TeamAllies;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.List;

/** Unique player kills improve this physical sword; all kills yield gold. */
public final class MidasSword extends AbstractLegendaryWeapon implements Listener {
    private static final NamespacedKey VICTIMS =
            new NamespacedKey("runeslegendaryv", "midas_victims");

    public MidasSword(ULegendaryContext context) {
        super(context, "MidasSword");
    }

    @Override
    public ItemStack getItem() {
        ItemStack item = super.getItem();
        refreshText(item);
        return item;
    }

    @Override
    public void refreshText(ItemStack item) {
        super.refreshText(item);
        if (isItem(item)) {
            var meta = item.getItemMeta();
            meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
    }

    public boolean improve(ItemStack item, Player killer, Player victim) {
        if (!isItem(item) || killer.equals(victim) || TeamAllies.areAllies(killer, victim))
            return false;
        int sharp = item.getEnchantmentLevel(Enchantment.SHARPNESS);
        int max = Math.max(0, Math.min(255, config.getInt("max_sharpness", 26)));
        if (sharp >= max) return false;
        var meta = item.getItemMeta();
        String encoded =
                meta.getPersistentDataContainer()
                        .getOrDefault(VICTIMS, PersistentDataType.STRING, "");
        var victims = new HashSet<String>();
        if (!encoded.isBlank()) victims.addAll(List.of(encoded.split(",")));
        if (!victims.add(victim.getUniqueId().toString())) return false;
        meta.getPersistentDataContainer()
                .set(VICTIMS, PersistentDataType.STRING, String.join(",", victims));
        meta.addEnchant(Enchantment.SHARPNESS, Math.min(max, sharp + 1), true);
        item.setItemMeta(meta);
        refreshText(item);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void death(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        ItemStack item = killer.getInventory().getItemInMainHand();
        if (!isItem(item)) return;
        boolean player = event.getEntity() instanceof Player;
        addGold(
                event,
                Material.GOLD_NUGGET,
                config.getInt(
                        player ? "player_gold_nuggets" : "mob_gold_nuggets", player ? 27 : 8));
        if (player) {
            addGold(event, Material.GOLD_INGOT, config.getInt("player_gold_ingots", 6));
            if (improve(item, killer, (Player) event.getEntity()))
                killer.getInventory().setItemInMainHand(item);
        }
    }

    private static void addGold(EntityDeathEvent event, Material material, int amount) {
        int left = Math.max(0, Math.min(4096, amount));
        while (left > 0) {
            int count = Math.min(64, left);
            event.getDrops().add(new ItemStack(material, count));
            left -= count;
        }
    }
}
