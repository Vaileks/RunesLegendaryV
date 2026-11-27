package dev.vaileks.runeslegendaryv.weapon.maces;

import dev.vaileks.runeslegendaryv.config.Language;
import dev.vaileks.runeslegendaryv.support.Presentation;
import dev.vaileks.runeslegendaryv.weapon.AbstractLegendaryWeapon;
import dev.vaileks.runeslegendaryv.weapon.ULegendaryContext;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class MaceWeapon extends AbstractLegendaryWeapon {
    private static final NamespacedKey CURSED = new NamespacedKey("runeslegendaryv", "mace_cursed");

    public MaceWeapon(ULegendaryContext context, String key) {
        super(context, key);
    }

    @Override
    protected ItemStack buildItem() {
        ItemStack item = super.buildItem();
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer()
                .set(CURSED, PersistentDataType.BOOLEAN, config.getBoolean("curse_enabled", true));
        item.setItemMeta(meta);
        return item;
    }

    public boolean cursed(ItemStack item) {
        return isItem(item)
                && item.getItemMeta()
                        .getPersistentDataContainer()
                        .getOrDefault(CURSED, PersistentDataType.BOOLEAN, true);
    }

    public void setCursed(ItemStack item, boolean enabled) {
        if (!isItem(item)) return;
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(CURSED, PersistentDataType.BOOLEAN, enabled);
        item.setItemMeta(meta);
        refreshText(item);
    }

    @Override
    public void refreshText(ItemStack item) {
        super.refreshText(item);
        if (!isItem(item)) return;
        cap(item);
        if (!cursed(item)) {
            var text = new org.bukkit.configuration.file.YamlConfiguration();
            text.set("rarity", "epic");
            text.set("tooltip-style", config.getString("tooltip-style", "minecraft:shard"));
            text.set("lore-description", config.getStringList("lore-description"));
            text.set("lore-abilities", List.of(Language.text("messages.mace-no-curse")));
            Presentation.decorate(item, text, key);
        }
    }

    @Override
    public ItemStack getItem() {
        ItemStack item = super.getItem();
        refreshText(item);
        return item;
    }

    static boolean sameIdentity(ItemStack a, ItemStack b) {
        if (a == null || b == null || !a.hasItemMeta() || !b.hasItemMeta()) return false;
        String id = a.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING);
        return id != null
                && id.equals(
                        b.getItemMeta()
                                .getPersistentDataContainer()
                                .get(ID, PersistentDataType.STRING));
    }

    static void cap(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        var meta = item.getItemMeta();
        if (meta.getEnchantLevel(Enchantment.DENSITY) > 4)
            meta.addEnchant(Enchantment.DENSITY, 4, true);
        if (meta.getEnchantLevel(Enchantment.BREACH) > 3)
            meta.addEnchant(Enchantment.BREACH, 3, true);
        item.setItemMeta(meta);
    }
}
