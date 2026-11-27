package test;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.compat.*;
import dev.vaileks.runeslegendaryv.weapon.mechzhizni.TargetCharge;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.*;
import java.util.*;

public final class CompatibilityAudit {
    private static int checks;

    private static void check(boolean good, String message) {
        checks++;
        if (!good) throw new AssertionError(message);
    }

    @org.bukkit.configuration.serialization.SerializableAs("test_unsupported_value")
    public static final class UnsupportedValue
            implements org.bukkit.configuration.serialization.ConfigurationSerializable {
        static boolean decoded;

        public Map<String, Object> serialize() {
            return Map.of("value", 1);
        }

        public static UnsupportedValue deserialize(Map<String, Object> data) {
            decoded = true;
            return new UnsupportedValue();
        }
    }

    static void run(RunesLegendaryV plugin) throws Exception {
        boolean nativePaper;
        try {
            Class.forName("io.papermc.paper.event.player.PrePlayerAttackEntityEvent");
            nativePaper = true;
        } catch (ClassNotFoundException e) {
            nativePaper = false;
        }
        check(ServerCompat.paper() == nativePaper, "native Paper text binding matches server");
        String expectedAdapter = nativePaper ? "PaperCombatListener" : "SpigotCombatListener";
        var adapter =
                HandlerList.getRegisteredListeners(plugin).stream()
                        .map(l -> l.getListener())
                        .filter(l -> l.getClass().getSimpleName().equals(expectedAdapter))
                        .findFirst()
                        .orElseThrow();
        check(adapter != null, "correct combat adapter registered");
        var world = Bukkit.getWorlds().getFirst();
        var at = world.getSpawnLocation().clone().add(0, 8, 0);
        at.getChunk().load();
        var stand = world.spawn(at, ArmorStand.class);
        try {
            var attr = stand.getAttribute(Attributes.SCALE);
            double before = attr.getValue();
            var key = new NamespacedKey("test", "temporary_scale");
            ServerCompat.addTransientModifier(
                    attr,
                    new AttributeModifier(
                            key,
                            -.5,
                            AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                            EquipmentSlotGroup.ANY));
            check(
                    Math.abs(attr.getValue() - before * .5) < .001,
                    "temporary scale works on actual server entity");
            ServerCompat.removeModifier(attr, key);
            check(
                    Math.abs(attr.getValue() - before) < .001,
                    "temporary attribute removed completely");
        } finally {
            stand.remove();
        }
        var totem = new ItemStack(Material.TOTEM_OF_UNDYING);
        var meta = totem.getItemMeta();
        meta.setCustomModelData(12345);
        meta.getPersistentDataContainer()
                .set(
                        new NamespacedKey("test", "opaque"),
                        PersistentDataType.BYTE_ARRAY,
                        new byte[] {1, 4, 9});
        totem.setItemMeta(meta);
        byte[] stored = ServerCompat.serializeAsBytes(totem);
        check(
                stored[0] == (byte) 0xac && stored[1] == (byte) 0xed,
                "portable serialization format on every core");
        check(
                ServerCompat.deserializeBytes(stored).isSimilar(totem),
                "model and custom bytes survive portable storage");
        try {
            var old = ItemStack.class.getMethod("serializeAsBytes");
            byte[] legacy = (byte[]) old.invoke(totem);
            check(
                    ServerCompat.deserializeBytes(legacy).isSimilar(totem),
                    "existing native Paper totems remain readable");
        } catch (NoSuchMethodException ignored) {
        }
        var rejected = new java.io.ByteArrayOutputStream();
        org.bukkit.configuration.serialization.ConfigurationSerialization.registerClass(
                UnsupportedValue.class);
        try {
            try (var out = new org.bukkit.util.io.BukkitObjectOutputStream(rejected)) {
                out.writeObject(new UnsupportedValue());
            }
            boolean denied = false;
            try {
                ServerCompat.deserializeBytes(rejected.toByteArray());
            } catch (IllegalStateException expected) {
                denied = true;
            }
            check(
                    denied && !UnsupportedValue.decoded,
                    "plugin-defined serialization aliases rejected before their decoder runs");
        } finally {
            org.bukkit.configuration.serialization.ConfigurationSerialization.unregisterClass(
                    UnsupportedValue.class);
        }
        if (nativePaper) {
            var glove = plugin.gauntlet().getItem();
            plugin.gauntlet().setStoredTotems(glove, List.of(totem));
            byte[] old = (byte[]) ItemStack.class.getMethod("serializeAsBytes").invoke(totem);
            var gm = glove.getItemMeta();
            gm.getPersistentDataContainer()
                    .set(
                            new NamespacedKey("runeslegendaryv", "gauntlet_totem_0"),
                            PersistentDataType.BYTE_ARRAY,
                            old);
            glove.setItemMeta(gm);
            plugin.gauntlet().refreshText(glove);
            byte[] migrated =
                    glove.getItemMeta()
                            .getPersistentDataContainer()
                            .get(
                                    new NamespacedKey("runeslegendaryv", "gauntlet_totem_0"),
                                    PersistentDataType.BYTE_ARRAY);
            check(
                    migrated[0] == (byte) 0xac && migrated[1] == (byte) 0xed,
                    "old Paper storage migrates on inventory refresh");
            check(
                    plugin.gauntlet().storedTotems(glove).getFirst().isSimilar(totem),
                    "migration preserves the complete custom totem");
        }
        if (adapter instanceof SpigotCombatListener spigot) {
            var owner = new StandaloneSmoke.Avatar(at);
            var victim = new StandaloneSmoke.Avatar(at.clone().add(2, 0, 0));
            owner.slots[0] = plugin.swordOfNature().getItem();
            var event =
                    new EntityDamageByEntityEvent(
                            owner.player,
                            victim.player,
                            EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                            4);
            spigot.attack(event);
            plugin.swordOfNature().prepareHit(owner.player, victim.player, event);
            plugin.swordOfNature().finishHit(event);
            Field charges = plugin.swordOfNature().getClass().getDeclaredField("charges");
            charges.setAccessible(true);
            var charge =
                    (TargetCharge) ((Map<?, ?>) charges.get(plugin.swordOfNature())).get(owner.id);
            check(
                    charge.bonus(System.currentTimeMillis()) > 0,
                    "Spigot direct hit earns nature charge");
            owner.slots[0] = plugin.railGun().getItem();
            owner.slots[1] = new ItemStack(Material.ANVIL, 2);
            Field charged = plugin.railGun().getClass().getDeclaredField("charged");
            charged.setAccessible(true);
            ((Set<UUID>) charged.get(plugin.railGun())).add(owner.id);
            Field pending = SpigotCombatListener.class.getDeclaredField("pendingRelease");
            pending.setAccessible(true);
            ((Map<UUID, Integer>) pending.get(spigot)).put(owner.id, ServerCompat.tick() - 2);
            Method tick = SpigotCombatListener.class.getDeclaredMethod("tickPlayer", Player.class);
            tick.setAccessible(true);
            tick.invoke(spigot, owner.player);
            check(
                    !plugin.railGun().isFullyCharged(owner.player),
                    "release detected without Paper event");
            check(owner.slots[1].getAmount() == 1, "one anvil consumed on completed release");
            Field projectiles = plugin.railGun().getClass().getDeclaredField("projectiles");
            projectiles.setAccessible(true);
            check(
                    !((List<?>) projectiles.get(plugin.railGun())).isEmpty(),
                    "Spigot release spawns a real railgun projectile");
            ((Set<UUID>) charged.get(plugin.railGun())).add(owner.id);
            owner.raised = true;
            owner.activeItem = new ItemStack(Material.SHIELD);
            tick.invoke(spigot, owner.player);
            check(
                    !plugin.railGun().isFullyCharged(owner.player)
                            && owner.slots[1].getAmount() == 1,
                    "raising an offhand shield cannot release a railgun shot");
            plugin.railGun().clearAll();
            plugin.resetCooldowns(owner.player);
            plugin.swordOfNature().clear(owner.player);
        }
        System.out.println("COMPATIBILITY_PASS checks=" + checks);
    }
}
