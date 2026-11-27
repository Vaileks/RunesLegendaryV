package dev.vaileks.runeslegendaryv.weapon.epic;

import dev.vaileks.runeslegendaryv.compat.Attributes;
import dev.vaileks.runeslegendaryv.util.ItemUtil;
import dev.vaileks.runeslegendaryv.weapon.AbstractLegendaryWeapon;
import dev.vaileks.runeslegendaryv.weapon.ULegendaryContext;

import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

/** Ordinary shield blocking with passive, offhand-only netherite bonuses. */
public final class NetheriteShield extends AbstractLegendaryWeapon {
    public NetheriteShield(ULegendaryContext context) {
        super(context, "NetheriteShield");
    }

    @Override
    protected ItemStack buildItem() {
        ItemStack item = super.buildItem();
        add(item, Attributes.MAX_HEALTH, "health", 4);
        add(item, Attributes.ARMOR, "armor", 4);
        add(item, Attributes.ARMOR_TOUGHNESS, "toughness", 2);
        return item;
    }

    private void add(ItemStack item, Attribute attribute, String key, double fallback) {
        double amount = config.getDouble("offhand." + key, fallback);
        if (Double.isFinite(amount) && amount > 0)
            ItemUtil.addAttribute(
                    item,
                    keys.attribute("netherite_shield_" + key),
                    attribute,
                    Math.min(40, amount),
                    EquipmentSlotGroup.OFFHAND);
    }
}
