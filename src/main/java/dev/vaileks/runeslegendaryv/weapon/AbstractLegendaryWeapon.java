package dev.vaileks.runeslegendaryv.weapon;

import dev.vaileks.runeslegendaryv.compat.Attributes;
import dev.vaileks.runeslegendaryv.compat.ServerCompat;
import dev.vaileks.runeslegendaryv.support.Presentation;
import dev.vaileks.runeslegendaryv.util.ItemUtil;
import dev.vaileks.runeslegendaryv.util.Keys;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public abstract class AbstractLegendaryWeapon {
    public static final NamespacedKey TYPE = new NamespacedKey("runeslegendaryv", "item"),
            ID = new NamespacedKey("runeslegendaryv", "id");
    protected final ULegendaryContext context;
    protected final dev.vaileks.runeslegendaryv.config.Settings settings;
    protected final Keys keys;
    protected final String key;
    protected final org.bukkit.configuration.ConfigurationSection config;
    private ItemStack cachedItem;

    protected AbstractLegendaryWeapon(ULegendaryContext context, String key) {
        this.context = context;
        this.settings = context.settings();
        this.keys = context.keys();
        this.key = key;
        this.config = settings.section(key);
    }

    public String getName() {
        return key;
    }

    public ItemStack getItem() {
        if (cachedItem == null) cachedItem = buildItem();
        ItemStack result = cachedItem.clone();
        var meta = result.getItemMeta();
        String id = UUID.randomUUID().toString();
        meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, id);
        ServerCompat.cooldownGroup(meta, new NamespacedKey("runeslegendaryv", "item_" + id));
        result.setItemMeta(meta);
        return result;
    }

    public void refreshText(ItemStack item) {
        if (!isItem(item)) return;
        var meta = item.getItemMeta();
        ServerCompat.displayName(meta, ItemUtil.parse(config.getString("name", key)));
        item.setItemMeta(meta);
        Presentation.decorate(item, config, key);
    }

    public ItemStack getProcessedItem() {
        return getItem();
    }

    public boolean isItem(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && key.equals(
                        item.getItemMeta()
                                .getPersistentDataContainer()
                                .get(TYPE, PersistentDataType.STRING));
    }

    protected ItemStack buildItem() {
        ItemStack item =
                ItemUtil.base(
                        ItemUtil.material(config.getString("material", "STONE"), Material.STONE),
                        config.getString("name", key),
                        List.of());
        if (config.contains("custom_model_data")) {
            var modelMeta = item.getItemMeta();
            modelMeta.setCustomModelData(config.getInt("custom_model_data"));
            item.setItemMeta(modelMeta);
        }
        applyItemModel(item, config.getString("item_model"));
        double damage = config.getDouble("damage"),
                speed = config.getDouble("attack_speed"),
                movement = config.getDouble("speed_modifier");
        if (damage != 0)
            ItemUtil.addAttribute(
                    item,
                    keys.attribute(key + "_damage"),
                    Attributes.ATTACK_DAMAGE,
                    damage - 1,
                    EquipmentSlotGroup.MAINHAND);
        if (speed != 0)
            ItemUtil.addAttribute(
                    item,
                    keys.attribute(key + "_speed"),
                    Attributes.ATTACK_SPEED,
                    speed,
                    EquipmentSlotGroup.MAINHAND);
        if (movement != 0)
            ItemUtil.addAttribute(
                    item,
                    keys.attribute(key + "_movement"),
                    Attributes.MOVEMENT_SPEED,
                    movement,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND);
        var enchants = config.getConfigurationSection("enchants");
        if (enchants != null)
            for (String name : enchants.getKeys(false))
                ItemUtil.enchant(item, name, enchants.getInt(name));
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(TYPE, PersistentDataType.STRING, key);
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(!config.getBoolean("hide_glow", true));
        item.setItemMeta(meta);
        Presentation.decorate(item, config, key);
        return item;
    }

    protected void applyItemModel(ItemStack item, String model) {
        ItemUtil.setItemModel(item, model);
    }
}
