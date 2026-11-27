package dev.vaileks.runeslegendaryv.compat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Keeps optional Paper APIs out of the shared gameplay code. */
public final class ServerCompat {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();
    private static int ticks;
    private static final Method TICK = optional(Bukkit.class, "getCurrentTick");
    private static final Method NAME_GET = optional(ItemMeta.class, "displayName");
    private static final Method NAME_SET = optional(ItemMeta.class, "displayName", Component.class);
    private static final Method LORE_GET = optional(ItemMeta.class, "lore");
    private static final Method LORE_SET = optional(ItemMeta.class, "lore", List.class);
    private static final Method DESERIALIZE =
            optional(ItemStack.class, "deserializeBytes", byte[].class);
    private static final Method MESSAGE =
            optional(CommandSender.class, "sendMessage", Component.class);
    private static final Method BAR = optional(Player.class, "sendActionBar", Component.class);
    private static final Method CLEAR = optional(Player.class, "clearActiveItem");

    public static void model(ItemMeta meta, NamespacedKey key) {
        Method method = optional(ItemMeta.class, "setItemModel", NamespacedKey.class);
        if (method != null) invoke(method, meta, key);
        else if (key != null) {
            NamespacedKey saved = new NamespacedKey("runeslegendaryv", "legacy_model");
            if (!meta.hasCustomModelData()
                    || meta.getPersistentDataContainer()
                            .has(saved, org.bukkit.persistence.PersistentDataType.STRING))
                meta.setCustomModelData(
                        1000000 + Math.floorMod(key.toString().hashCode(), 1000000));
            meta.getPersistentDataContainer()
                    .set(saved, org.bukkit.persistence.PersistentDataType.STRING, key.toString());
        }
    }

    public static boolean modernModels() {
        return optional(ItemMeta.class, "getItemModel") != null;
    }

    public static NamespacedKey model(ItemMeta meta) {
        Method getter = optional(ItemMeta.class, "getItemModel");
        if (getter != null) return (NamespacedKey) invoke(getter, meta);
        String saved =
                meta.getPersistentDataContainer()
                        .get(
                                new NamespacedKey("runeslegendaryv", "legacy_model"),
                                org.bukkit.persistence.PersistentDataType.STRING);
        return saved == null ? null : NamespacedKey.fromString(saved);
    }

    public static NamespacedKey tooltip(ItemMeta meta) {
        Method getter = optional(ItemMeta.class, "getTooltipStyle");
        return getter == null ? null : (NamespacedKey) invoke(getter, meta);
    }

    public static void tooltip(ItemMeta meta, NamespacedKey key) {
        Method method = optional(ItemMeta.class, "setTooltipStyle", NamespacedKey.class);
        if (method != null) invoke(method, meta, key);
    }

    public static void cooldownGroup(ItemMeta meta, NamespacedKey key) {
        Method getter = optional(ItemMeta.class, "getUseCooldown");
        if (getter == null) return;
        Object component = invoke(getter, meta);
        Class<?> type = getter.getReturnType();
        invoke(optional(type, "setCooldownGroup", NamespacedKey.class), component, key);
        invoke(optional(type, "setCooldownSeconds", float.class), component, .05f);
        invoke(optional(ItemMeta.class, "setUseCooldown", type), meta, component);
    }

    public static void setCooldown(Player player, ItemStack item, int ticks) {
        Method method =
                optional(
                        org.bukkit.entity.HumanEntity.class,
                        "setCooldown",
                        ItemStack.class,
                        int.class);
        if (method != null) invoke(method, player, item, ticks);
        // Older clients only understand material cooldowns. Avoid locking unrelated swords.
        else if (item.getType() == Material.SHIELD || item.getType() == Material.SPYGLASS)
            player.setCooldown(item.getType(), ticks);
    }

    public static boolean paper() {
        return MESSAGE != null;
    }

    public static java.util.concurrent.CompletableFuture<Chunk> loadChunk(
            World world, int x, int z, boolean generate) {
        try {
            Method method =
                    optional(World.class, "getChunkAtAsync", int.class, int.class, boolean.class);
            if (method != null)
                return (java.util.concurrent.CompletableFuture<Chunk>)
                        invoke(method, world, x, z, generate);
            return java.util.concurrent.CompletableFuture.completedFuture(
                    world.getChunkAt(x, z, generate));
        } catch (RuntimeException e) {
            return java.util.concurrent.CompletableFuture.failedFuture(e);
        }
    }

    public static java.util.concurrent.CompletableFuture<Boolean> teleportAsync(
            Player player,
            Location to,
            org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        Method method =
                optional(
                        Player.class,
                        "teleportAsync",
                        Location.class,
                        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.class);
        if (method != null)
            return (java.util.concurrent.CompletableFuture<Boolean>)
                    invoke(method, player, to, cause);
        return java.util.concurrent.CompletableFuture.completedFuture(player.teleport(to, cause));
    }

    public static void visible(Plugin plugin, Entity entity, boolean visible) {
        Method method = optional(Entity.class, "setVisibleByDefault", boolean.class);
        if (method != null) invoke(method, entity, visible);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (visible) player.showEntity(plugin, entity);
            else player.hideEntity(plugin, entity);
        }
    }

    public static void start(Plugin plugin) {
        if (TICK == null) Bukkit.getScheduler().runTaskTimer(plugin, () -> ticks++, 1, 1);
    }

    public static void registerCombat(dev.vaileks.runeslegendaryv.RunesLegendaryV plugin) {
        org.bukkit.event.Listener listener;
        if (paper()) {
            try {
                listener =
                        (org.bukkit.event.Listener)
                                Class.forName(
                                                "dev.vaileks.runeslegendaryv.compat.PaperCombatListener")
                                        .getConstructor(plugin.getClass())
                                        .newInstance(plugin);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot register Paper combat events", e);
            }
        } else listener = new SpigotCombatListener(plugin);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    public static void addTransientModifier(
            AttributeInstance attribute, AttributeModifier modifier) {
        Method nativeMethod =
                optional(AttributeInstance.class, "addTransientModifier", AttributeModifier.class);
        if (nativeMethod != null) invoke(nativeMethod, attribute, modifier);
        else attribute.addModifier(modifier);
    }

    public static int tick() {
        return TICK == null ? ticks : (int) invoke(TICK, null);
    }

    private static Method optional(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Server API call failed: " + method.getName(), e);
        }
    }

    public static String legacy(Component text) {
        return text == null ? "" : LEGACY.serialize(text);
    }

    public static Component displayName(ItemMeta meta) {
        return NAME_GET != null
                ? (Component) invoke(NAME_GET, meta)
                : meta.hasDisplayName() ? LEGACY.deserialize(meta.getDisplayName()) : null;
    }

    public static void displayName(ItemMeta meta, Component text) {
        if (NAME_SET != null) invoke(NAME_SET, meta, text);
        else meta.setDisplayName(text == null ? null : legacy(text));
    }

    @SuppressWarnings("unchecked")
    public static List<Component> lore(ItemMeta meta) {
        return LORE_GET != null
                ? (List<Component>) invoke(LORE_GET, meta)
                : meta.hasLore()
                        ? meta.getLore().stream()
                                .map(LEGACY::deserialize)
                                .map(c -> (Component) c)
                                .toList()
                        : null;
    }

    public static void lore(ItemMeta meta, List<Component> lines) {
        if (LORE_SET != null) invoke(LORE_SET, meta, lines);
        else meta.setLore(lines == null ? null : lines.stream().map(ServerCompat::legacy).toList());
    }

    public static void sendMessage(CommandSender sender, Component text) {
        if (MESSAGE != null) invoke(MESSAGE, sender, text);
        else sender.sendMessage(legacy(text));
    }

    public static void sendActionBar(Player player, Component text) {
        if (BAR != null) invoke(BAR, player, text);
        else
            player.spigot()
                    .sendMessage(
                            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(legacy(text)));
    }

    public static Inventory createInventory(InventoryHolder holder, int size, Component title) {
        return Bukkit.createInventory(holder, size, legacy(title));
    }

    public static Inventory createInventory(
            InventoryHolder holder, InventoryType type, Component title) {
        return Bukkit.createInventory(holder, type, legacy(title));
    }

    public static void deathMessage(PlayerDeathEvent event, Component text) {
        Method nativeMethod = optional(event.getClass(), "deathMessage", Component.class);
        if (nativeMethod != null) invoke(nativeMethod, event, text);
        else event.setDeathMessage(legacy(text));
    }

    public static boolean cancelled(Object event) {
        return event instanceof org.bukkit.event.Cancellable c && c.isCancelled();
    }

    public static boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    public static void clearActiveItem(Player player) {
        if (CLEAR != null) invoke(CLEAR, player);
        else if (player.isHandRaised()) player.setItemInUseTicks(0);
    }

    public static AttributeModifier modifier(AttributeInstance attribute, NamespacedKey key) {
        return attribute.getModifiers().stream()
                .filter(m -> m.getKey().equals(key))
                .findFirst()
                .orElse(null);
    }

    public static void removeModifier(AttributeInstance attribute, NamespacedKey key) {
        var found = modifier(attribute, key);
        if (found != null) attribute.removeModifier(found);
    }

    public static byte[] serializeAsBytes(ItemStack item) {
        try (var bytes = new ByteArrayOutputStream();
                var out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(item);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot store totem", e);
        }
    }

    public static ItemStack deserializeBytes(byte[] bytes) {
        if (bytes.length >= 2 && bytes[0] == (byte) 0xac && bytes[1] == (byte) 0xed) {
            try (var in = new StoredItemInputStream(new ByteArrayInputStream(bytes))) {
                Object item = in.readObject();
                if (item instanceof ItemStack stack) return stack;
                throw new IOException("Stored object is not an item");
            } catch (IOException | ClassNotFoundException e) {
                throw new IllegalStateException("Cannot read stored totem", e);
            }
        }
        if (DESERIALIZE != null) return (ItemStack) invoke(DESERIALIZE, null, (Object) bytes);
        throw new IllegalStateException(
                "This totem was stored in Paper NBT format. Withdraw it on Paper before moving this"
                        + " item to Spigot.");
    }
}
