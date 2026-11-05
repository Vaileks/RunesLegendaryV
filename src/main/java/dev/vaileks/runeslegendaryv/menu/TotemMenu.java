package dev.vaileks.runeslegendaryv.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import dev.vaileks.runeslegendaryv.config.Cfg;
import dev.vaileks.runeslegendaryv.util.ItemUtil;

public final class TotemMenu
implements InventoryHolder {
    private final ItemStack gauntlet;
    private final java.util.UUID owner;
    private final int sourceSlot;
    private final String storageId;
    private final Inventory inventory;
    private final int max;
    private final String filledName;
    private final String emptyName;
    private final String insertSound;
    private final String removeSound;
    private final String denied;

    public TotemMenu(Cfg cfg, ItemStack itemStack, int n, int n2) {
        this(cfg, itemStack, n, n2, null, -1, "");
    }

    public TotemMenu(Cfg cfg, ItemStack itemStack, int n, int n2, java.util.UUID owner, int sourceSlot, String storageId) {
        this.gauntlet = itemStack;
        this.owner = owner;
        this.sourceSlot = sourceSlot;
        this.storageId = storageId;
        this.max = n2;
        this.filledName = cfg.string("filled_name", "&d\u0422\u043e\u0442\u0435\u043c \u043d\u0435\u0436\u0438\u0442\u0438");
        this.emptyName = cfg.string("empty_name", "&7\u041f\u0443\u0441\u0442\u043e\u0439 \u0441\u043b\u043e\u0442");
        this.insertSound = cfg.string("insert_sound", "block.beacon.power_select");
        this.removeSound = cfg.string("remove_sound", "block.respawn_anchor.deplete");
        this.denied = cfg.string("denied", "&c\u0412 \u043f\u0435\u0440\u0447\u0430\u0442\u043a\u0435 \u0443\u0436\u0435 &f%max% &c\u0442\u043e\u0442\u0435\u043c\u0430");
        String string = cfg.string("title", "&7\u041f\u0435\u0440\u0447\u0430\u0442\u043a\u0430 \u0441\u0442\u043e\u0439\u043a\u043e\u0441\u0442\u0438");
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (InventoryType)InventoryType.HOPPER, (Component)ItemUtil.parse(string));
        int n3 = Math.max(0, Math.min(n2, this.inventory.getSize() - 1));
        for (int i = 0; i < n3; ++i) {
            if (i >= n) continue;
            this.inventory.setItem(i, this.totem());
        }
        Material material = ItemUtil.material(cfg.string("filler", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
        ItemStack itemStack2 = new ItemStack(material);
        for (int i = n3; i < this.inventory.getSize(); ++i) {
            this.inventory.setItem(i, itemStack2);
        }
    }

    private ItemStack totem() {
        return new ItemStack(Material.TOTEM_OF_UNDYING);
    }

    private ItemStack empty() {
        return null;
    }

    public java.util.UUID owner() { return owner; }
    public int sourceSlot() { return sourceSlot; }
    public String storageId() { return storageId; }
    public void refresh(int count) {
        for (int slot = 0; slot < totemSlotCount(); slot++) inventory.setItem(slot, slot < count ? totem() : null);
    }

    public void refresh(java.util.List<ItemStack> items) {
        for (int slot = 0; slot < totemSlotCount(); slot++) inventory.setItem(slot, slot < items.size() ? items.get(slot).clone() : null);
    }

    public ItemStack gauntlet() {
        return this.gauntlet;
    }

    public int totemSlotCount() {
        return Math.max(0, Math.min(this.max, this.inventory.getSize() - 1));
    }

    public boolean isEmptySlot(ItemStack itemStack) {
        if (itemStack != null && itemStack.getType() != Material.AIR) {
            return itemStack.getType() == Material.GRAY_STAINED_GLASS_PANE;
        }
        return true;
    }

    public int countStored() {
        int n = 0;
        for (int i = 0; i < this.totemSlotCount(); ++i) {
            ItemStack itemStack = this.inventory.getItem(i);
            if (itemStack == null || itemStack.getType() != Material.TOTEM_OF_UNDYING) continue;
            n += itemStack.getAmount();
        }
        return n;
    }

    public boolean hasFreeSlot() {
        for (int i = 0; i < this.totemSlotCount(); ++i) {
            if (!this.isEmptySlot(this.inventory.getItem(i))) continue;
            return true;
        }
        return false;
    }

    public void insertTotem(int n) {
        this.inventory.setItem(n, this.totem());
    }

    public void removeTotem(int n) {
        this.inventory.setItem(n, this.empty());
    }

    public String deniedMessage() {
        return this.denied.replace("%max%", String.valueOf(this.max));
    }

    public String insertSound() {
        return this.insertSound;
    }

    public String removeSound() {
        return this.removeSound;
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}

