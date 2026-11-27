package dev.vaileks.runeslegendaryv.support;

import dev.vaileks.runeslegendaryv.compat.Attributes;
import dev.vaileks.runeslegendaryv.compat.ServerCompat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class Presentation {
    private static final int WIDTH = 38;

    public static void decorate(
            ItemStack item, org.bukkit.configuration.ConfigurationSection config, String key) {
        var meta = item.getItemMeta();
        ServerCompat.displayName(
                meta,
                ServerCompat.displayName(meta)
                        .font(net.kyori.adventure.key.Key.key("minecraft:default"))
                        .decoration(TextDecoration.ITALIC, false));
        ServerCompat.lore(
                meta,
                render(
                        config.getStringList("lore-description"),
                        config.getStringList("lore-abilities"),
                        value(item, meta, Attributes.ATTACK_DAMAGE, 1),
                        value(item, meta, Attributes.ATTACK_SPEED, 4)));
        var lore = new ArrayList<>(ServerCompat.lore(meta));
        boolean epic = config.getString("rarity", "legendary").equalsIgnoreCase("epic");
        lore.add(
                0,
                dev.vaileks.runeslegendaryv.config.Language.component(
                                epic ? "rarity.epic" : "rarity.legendary")
                        .color(epic ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
        lore.add(1, Component.empty());
        ServerCompat.lore(meta, lore);
        String palette =
                key.equals("mechzhizni") ? "earth" : key.equals("JudgeSword") ? "gold" : "shard";
        ServerCompat.tooltip(
                meta,
                NamespacedKey.fromString(
                        config.getString("tooltip-style", "minecraft:" + palette)));
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
    }

    public static double value(ItemStack item, ItemMeta meta, Attribute attribute, double base) {
        var attributes = meta.getAttributeModifiers();
        Collection<AttributeModifier> modifiers =
                attributes == null
                        ? item.getType()
                                .getDefaultAttributeModifiers(EquipmentSlot.HAND)
                                .get(attribute)
                        : attributes.get(attribute);
        double add = 0, scalar = 0, multiplier = 1;
        for (AttributeModifier modifier : modifiers) {
            if (!modifier.getSlotGroup().test(EquipmentSlot.HAND)) continue;
            switch (modifier.getOperation()) {
                case ADD_NUMBER -> add += modifier.getAmount();
                case ADD_SCALAR -> scalar += modifier.getAmount();
                case MULTIPLY_SCALAR_1 -> multiplier *= 1 + modifier.getAmount();
            }
        }
        double result = (base + add) * (1 + scalar) * multiplier;
        int sharpness = 0;
        if (attribute == Attributes.ATTACK_DAMAGE && meta.getEnchants() != null)
            for (var enchantment : meta.getEnchants().entrySet())
                if (enchantment.getKey().getKey().toString().equals("minecraft:sharpness"))
                    sharpness = enchantment.getValue();
        if (sharpness > 0) result += .5 * sharpness + .5;
        return Math.max(0, result);
    }

    private static Component line(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private static String number(double value) {
        return java.math.BigDecimal.valueOf(value)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    public static List<Component> render(
            List<String> description, List<String> abilities, double damage, double speed) {
        List<Component> result = new ArrayList<>();
        for (String paragraph : description)
            for (String row : wrap(paragraph, WIDTH)) result.add(line(row, NamedTextColor.GRAY));
        result.add(Component.empty());
        for (String ability : abilities) {
            String[] pieces = ability.split(" \\u2014 ", 2);
            String trigger = pieces.length == 2 ? pieces[0] : "",
                    body = pieces.length == 2 ? pieces[1] : pieces[0];
            String prefix = trigger.isEmpty() ? "" : trigger + " - ";
            List<String> rows = wrap(body, Math.max(8, WIDTH - prefix.length()));
            for (int i = 0; i < rows.size(); i++) {
                if (i == 0 && !prefix.isEmpty())
                    result.add(
                            line(trigger, NamedTextColor.WHITE)
                                    .append(line(" - " + rows.get(i), NamedTextColor.GRAY)));
                else result.add(line(rows.get(i), NamedTextColor.GRAY));
            }
        }
        result.add(Component.empty());
        result.add(
                line(
                        dev.vaileks.runeslegendaryv.config.Language.text("tooltip.mainhand"),
                        NamedTextColor.GRAY));
        result.add(
                line(
                        " "
                                + dev.vaileks.runeslegendaryv.config.Language.text("tooltip.damage")
                                + number(damage),
                        NamedTextColor.DARK_GREEN));
        result.add(
                line(
                        " "
                                + dev.vaileks.runeslegendaryv.config.Language.text("tooltip.speed")
                                + number(speed),
                        NamedTextColor.DARK_GREEN));
        return result;
    }

    public static List<String> wrap(String paragraph, int limit) {
        List<String> rows = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        for (String word : paragraph.trim().split("\\s+")) {
            if (row.length() > 0 && row.length() + 1 + word.length() > limit) {
                rows.add(row.toString());
                row.setLength(0);
            }
            if (row.length() > 0) row.append(' ');
            row.append(word);
        }
        if (row.length() > 0) rows.add(row.toString());
        return rows;
    }
}
