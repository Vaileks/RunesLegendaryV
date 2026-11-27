package dev.vaileks.runeslegendaryv.weapon.emerald;

import dev.vaileks.runeslegendaryv.compat.ServerCompat;
import dev.vaileks.runeslegendaryv.weapon.AbstractLegendaryWeapon;
import dev.vaileks.runeslegendaryv.weapon.ULegendaryContext;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.*;

/** Storage and Sharpness belong to the item, never to the current wielder. */
public final class EmeraldBlade extends AbstractLegendaryWeapon {
    private static final NamespacedKey CONTENTS =
            new NamespacedKey("runeslegendaryv", "emerald_contents");

    public EmeraldBlade(ULegendaryContext context) {
        super(context, "EmeraldBlade");
    }

    public Material currency() {
        Material type = Material.matchMaterial(config.getString("currency", "EMERALD_BLOCK"));
        return type == null ? Material.EMERALD_BLOCK : type;
    }

    public int size() {
        return Math.max(1, Math.min(6, config.getInt("rows", 2))) * 9;
    }

    public boolean fullByDefault() {
        return config.getBoolean("full_by_default", false);
    }

    @Override
    public ItemStack getItem() {
        ItemStack item = super.getItem();
        if (fullByDefault())
            item.addUnsafeEnchantment(
                    Enchantment.SHARPNESS,
                    Math.max(1, Math.min(255, config.getInt("max_sharpness", 18))));
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

    public ItemStack[] contents(ItemStack sword) {
        byte[] bytes =
                sword.getItemMeta()
                        .getPersistentDataContainer()
                        .get(CONTENTS, PersistentDataType.BYTE_ARRAY);
        if (bytes == null) return new ItemStack[size()];
        if (bytes.length > 4 * 1024 * 1024)
            throw new IllegalArgumentException("Emerald contents exceed storage limit");
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != 0x454d5231)
                throw new IOException("Unknown Emerald contents format");
            int count = input.readUnsignedByte();
            if (count < 9 || count > 54 || count % 9 != 0)
                throw new IOException("Invalid Emerald inventory size");
            ItemStack[] items = new ItemStack[Math.max(size(), count)];
            for (int i = 0; i < count; i++) {
                int length = input.readInt();
                if (length < 0 || length > 1024 * 1024 || length > input.available())
                    throw new IOException("Invalid stored item length");
                if (length > 0) items[i] = ServerCompat.deserializeBytes(input.readNBytes(length));
            }
            if (input.available() != 0) throw new IOException("Trailing Emerald contents data");
            return items;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read Emerald Blade contents", error);
        }
    }

    public void save(ItemStack sword, ItemStack[] items) {
        if (!isItem(sword) || items.length < 9 || items.length > 54 || items.length % 9 != 0)
            throw new IllegalArgumentException("Invalid Emerald inventory");
        int count = 0;
        try (var bytes = new ByteArrayOutputStream();
                var output = new DataOutputStream(bytes)) {
            output.writeInt(0x454d5231);
            output.writeByte(items.length);
            for (ItemStack item : items) {
                if (item != null && !item.getType().isAir() && item.getType() != currency())
                    throw new IllegalArgumentException(
                            "Only configured currency belongs in Emerald Blade");
                byte[] encoded =
                        item == null || item.getType().isAir()
                                ? new byte[0]
                                : ServerCompat.serializeAsBytes(item);
                output.writeInt(encoded.length);
                output.write(encoded);
                if (encoded.length > 0) count += item.getAmount();
            }
            if (bytes.size() > 4 * 1024 * 1024)
                throw new IllegalArgumentException("Emerald contents exceed storage limit");
            var meta = sword.getItemMeta();
            meta.getPersistentDataContainer()
                    .set(CONTENTS, PersistentDataType.BYTE_ARRAY, bytes.toByteArray());
            int sharp =
                    Math.min(
                            Math.max(0, config.getInt("max_sharpness", 18)),
                            count / Math.max(1, config.getInt("material_per_sharpness", 64)));
            if (sharp > 0) meta.addEnchant(Enchantment.SHARPNESS, Math.min(255, sharp), true);
            else meta.removeEnchant(Enchantment.SHARPNESS);
            sword.setItemMeta(meta);
            refreshText(sword);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot save Emerald Blade contents", error);
        }
    }
}
