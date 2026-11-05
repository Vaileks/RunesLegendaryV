package dev.vaileks.runeslegendaryv.util;

import dev.vaileks.runeslegendaryv.support.ShadowText;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemUtil {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(Map.entry(Character.valueOf('0'), "<black>"), Map.entry(Character.valueOf('1'), "<dark_blue>"), Map.entry(Character.valueOf('2'), "<dark_green>"), Map.entry(Character.valueOf('3'), "<dark_aqua>"), Map.entry(Character.valueOf('4'), "<dark_red>"), Map.entry(Character.valueOf('5'), "<dark_purple>"), Map.entry(Character.valueOf('6'), "<gold>"), Map.entry(Character.valueOf('7'), "<gray>"), Map.entry(Character.valueOf('8'), "<dark_gray>"), Map.entry(Character.valueOf('9'), "<blue>"), Map.entry(Character.valueOf('a'), "<green>"), Map.entry(Character.valueOf('b'), "<aqua>"), Map.entry(Character.valueOf('c'), "<red>"), Map.entry(Character.valueOf('d'), "<light_purple>"), Map.entry(Character.valueOf('e'), "<yellow>"), Map.entry(Character.valueOf('f'), "<white>"), Map.entry(Character.valueOf('k'), "<obfuscated>"), Map.entry(Character.valueOf('l'), "<bold>"), Map.entry(Character.valueOf('m'), "<strikethrough>"), Map.entry(Character.valueOf('n'), "<underlined>"), Map.entry(Character.valueOf('o'), "<italic>"), Map.entry(Character.valueOf('r'), "<reset>"));

    private ItemUtil() {
    }

    public static Component parse(String string) {
        return ShadowText.mini(ItemUtil.legacy(string == null ? "" : string));
    }

    private static String legacy(String string) {
        if (string.indexOf(38) < 0 && string.indexOf(167) < 0) {
            return string;
        }
        StringBuilder stringBuilder = new StringBuilder(string.length() + 16);
        for (int i = 0; i < string.length(); ++i) {
            char c = string.charAt(i);
            if ((c == '&' || c == '\u00a7') && i + 1 < string.length()) {
                String string2;
                char c2 = Character.toLowerCase(string.charAt(i + 1));
                if (c2 == 'x' && (string2 = ItemUtil.hexAt(string, i + 2)) != null) {
                    stringBuilder.append('<').append('#').append(string2).append('>');
                    i += 7;
                    continue;
                }
                string2 = LEGACY_TAGS.get(Character.valueOf(c2));
                if (string2 == null) {
                    stringBuilder.append(c);
                    continue;
                }
                stringBuilder.append(string2);
                ++i;
                continue;
            }
            stringBuilder.append(c);
        }
        return stringBuilder.toString();
    }

    private static String hexAt(String string, int n) {
        if (string.length() > n + 5 && string.charAt(n) == '#') {
            StringBuilder stringBuilder = new StringBuilder(6);
            for (int i = n + 1; i < n + 7; ++i) {
                char c = string.charAt(i);
                if (Character.digit(c, 16) < 0) {
                    return null;
                }
                stringBuilder.append(c);
            }
            return stringBuilder.toString();
        }
        StringBuilder stringBuilder = new StringBuilder(6);
        for (int i = n; stringBuilder.length() < 6 && i < string.length(); i += 2) {
            char c = string.charAt(i);
            if ((c == '&' || c == '\u00a7') && i + 1 < string.length()) {
                char c2 = Character.toLowerCase(string.charAt(i + 1));
                if (Character.digit(c2, 16) < 0) {
                    return null;
                }
                stringBuilder.append(c2);
                continue;
            }
            return null;
        }
        return stringBuilder.length() == 6 ? stringBuilder.toString() : null;
    }

    public static List<Component> parseList(List<String> list) {
        ArrayList<Component> arrayList = new ArrayList<Component>();
        if (list == null) {
            return arrayList;
        }
        for (String string : list) {
            arrayList.add(ItemUtil.parse(string));
        }
        return arrayList;
    }

    public static ItemStack base(Material material, String string, List<String> list) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setUnbreakable(true);
        itemMeta.displayName(ItemUtil.parse(string));
        if (list != null && !list.isEmpty()) {
            itemMeta.lore(ItemUtil.parseList(list));
        }
        itemMeta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_DYE});
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public static void setItemModel(ItemStack itemStack, String string) {
        if (string != null && !string.isEmpty() && itemStack.hasItemMeta()) {
            NamespacedKey namespacedKey;
            NamespacedKey namespacedKey2 = namespacedKey = string.contains(":") ? NamespacedKey.fromString((String)string) : null;
            if (namespacedKey != null) {
                ItemMeta itemMeta = itemStack.getItemMeta();
                itemMeta.setItemModel(namespacedKey);
                itemStack.setItemMeta(itemMeta);
            }
        }
    }

    public static void addAttribute(ItemStack itemStack, NamespacedKey namespacedKey, Attribute attribute, double d, EquipmentSlotGroup equipmentSlotGroup) {
        ItemUtil.addAttribute(itemStack, namespacedKey, attribute, d, AttributeModifier.Operation.ADD_NUMBER, equipmentSlotGroup);
    }

    public static void addAttribute(ItemStack itemStack, NamespacedKey namespacedKey, Attribute attribute, double d, AttributeModifier.Operation operation, EquipmentSlotGroup equipmentSlotGroup) {
        if (d != 0.0) {
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.addAttributeModifier(attribute, new AttributeModifier(namespacedKey, d, operation, equipmentSlotGroup));
            itemStack.setItemMeta(itemMeta);
        }
    }

    public static void addPlayerAttribute(Attributable attributable, Attribute attribute, double d, EquipmentSlotGroup equipmentSlotGroup, NamespacedKey namespacedKey) {
        AttributeInstance attributeInstance = attributable.getAttribute(attribute);
        if (attributeInstance != null && d != 0.0 && attributeInstance.getModifier((Key)namespacedKey) == null) {
            attributeInstance.addModifier(new AttributeModifier(namespacedKey, d, AttributeModifier.Operation.ADD_NUMBER, equipmentSlotGroup));
        }
    }

    public static void removePlayerAttribute(Attributable attributable, Attribute attribute, NamespacedKey namespacedKey) {
        AttributeModifier attributeModifier;
        AttributeInstance attributeInstance = attributable.getAttribute(attribute);
        if (attributeInstance != null && (attributeModifier = attributeInstance.getModifier((Key)namespacedKey)) != null) {
            attributeInstance.removeModifier(attributeModifier);
        }
    }

    public static void enchant(ItemStack itemStack, String string, int n) {
        Enchantment enchantment = ItemUtil.enchantment(string);
        if (enchantment != null) {
            itemStack.addUnsafeEnchantment(enchantment, n);
        }
    }

    public static Enchantment enchantment(String string) {
        if (string == null) {
            return null;
        }
        String string2 = string.toLowerCase().replace(' ', '_');
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            String string3 = enchantment.getKey().getKey();
            if (!string3.equals(string2) && !string3.endsWith("_" + string2) && !string3.equals(string.toLowerCase())) continue;
            return enchantment;
        }
        return null;
    }

    public static Material material(String string, Material material) {
        if (string == null) {
            return material;
        }
        Material material2 = Material.matchMaterial((String)string);
        return material2 == null ? material : material2;
    }

    public static List<String> strings(List<?> list) {
        ArrayList<String> arrayList = new ArrayList<String>();
        if (list == null) {
            return arrayList;
        }
        for (Object obj : list) {
            arrayList.add(String.valueOf(obj));
        }
        return arrayList;
    }

    public static List<String> list(String ... stringArray) {
        return Arrays.asList(stringArray);
    }
}

