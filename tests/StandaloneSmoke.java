package test;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.compat.Attributes;
import dev.vaileks.runeslegendaryv.compat.ServerCompat;
import dev.vaileks.runeslegendaryv.support.*;
import dev.vaileks.runeslegendaryv.util.Keys;
import dev.vaileks.runeslegendaryv.weapon.*;
import dev.vaileks.runeslegendaryv.weapon.judge.*;
import dev.vaileks.runeslegendaryv.weapon.mechzhizni.*;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.util.*;

public final class StandaloneSmoke extends JavaPlugin {
    int checks;

    void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public void onEnable() {
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            try {
                                run();
                                CompatibilityAudit.run(
                                        (RunesLegendaryV)
                                                Bukkit.getPluginManager()
                                                        .getPlugin("LegendaryWeaponsVL"));
                                ExpansionAudit.run(
                                        (RunesLegendaryV)
                                                Bukkit.getPluginManager()
                                                        .getPlugin("LegendaryWeaponsVL"));
                                KatanaAudit.run(
                                        (RunesLegendaryV)
                                                Bukkit.getPluginManager()
                                                        .getPlugin("LegendaryWeaponsVL"));
                                LocalizationAudit.run(
                                        (RunesLegendaryV)
                                                Bukkit.getPluginManager()
                                                        .getPlugin("LegendaryWeaponsVL"));
                                NewWeaponsAudit.run(
                                        (RunesLegendaryV)
                                                Bukkit.getPluginManager()
                                                        .getPlugin("LegendaryWeaponsVL"));
                                MaceAudit.run(
                                        (RunesLegendaryV)
                                                Bukkit.getPluginManager()
                                                        .getPlugin("LegendaryWeaponsVL"));
                                SwordAudit.run(
                                        (RunesLegendaryV)
                                                Bukkit.getPluginManager()
                                                        .getPlugin("LegendaryWeaponsVL"));
                                CombatRegressionAudit.run(
                                        (RunesLegendaryV)
                                                Bukkit.getPluginManager()
                                                        .getPlugin("LegendaryWeaponsVL"));
                                int beforeTick = ServerCompat.tick();
                                Bukkit.getScheduler()
                                        .runTaskLater(
                                                this,
                                                () -> {
                                                    try {
                                                        NewWeaponsAudit.finish();
                                                        MaceAudit.finish();
                                                        SwordAudit.finish();
                                                        CombatRegressionAudit.finish();
                                                        NativeCombatAudit.run(
                                                                (RunesLegendaryV)
                                                                        Bukkit.getPluginManager()
                                                                                .getPlugin(
                                                                                        "LegendaryWeaponsVL"));
                                                        check(
                                                                ServerCompat.tick() - beforeTick
                                                                        >= 2,
                                                                "compatibility clock resumes after"
                                                                        + " configuration reload");
                                                        getLogger()
                                                                .info(
                                                                        "STANDALONE_PASS checks="
                                                                                + checks);
                                                    } catch (Throwable error) {
                                                        getLogger()
                                                                .severe("STANDALONE_FAIL " + error);
                                                        error.printStackTrace();
                                                        Bukkit.shutdown();
                                                        // NativeCombatAudit owns the later shutdown
                                                        // after its real attacks finish.
                                                    }
                                                },
                                                10);
                            } catch (Throwable e) {
                                getLogger().severe("STANDALONE_FAIL " + e);
                                e.printStackTrace();
                                Bukkit.shutdown();
                            }
                        },
                        30);
    }

    static Object zero(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        return null;
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler h) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[] {type}, h);
    }

    static final class Avatar {
        final UUID id = UUID.randomUUID();
        final ItemStack[] slots = new ItemStack[41];
        final ItemStack data = new ItemStack(Material.PAPER);
        final PersistentDataContainer pdc = data.getItemMeta().getPersistentDataContainer();
        final PlayerInventory inv;
        final Player player;
        final Location location;
        boolean online = true;
        boolean raised;
        boolean sneaking;
        boolean cancelDamage;
        double health = 20, absorption;
        float attackStrength = 1;
        GameMode gameMode = GameMode.SURVIVAL;
        final Map<org.bukkit.potion.PotionEffectType, org.bukkit.potion.PotionEffect> effects =
                new HashMap<>();
        boolean teleportAllowed = true;
        int teleports;
        ItemStack activeItem;
        InventoryView currentView;
        ItemStack cursor = new ItemStack(Material.AIR);
        final java.util.List<net.kyori.adventure.text.Component>
                messages = new java.util.ArrayList<>(),
                actionBars = new java.util.ArrayList<>();
        final Map<Object, Integer> cooldowns = new HashMap<>();

        Avatar(Location at) {
            location = at;
            inv =
                    proxy(
                            PlayerInventory.class,
                            (o, m, a) ->
                                    switch (m.getName()) {
                                        case "getItemInMainHand" -> copy(slots[0]);
                                        case "getItemInOffHand" -> copy(slots[40]);
                                        case "getItem" ->
                                                copy(
                                                        slots[
                                                                a[0] instanceof EquipmentSlot hand
                                                                        ? (hand
                                                                                        == EquipmentSlot
                                                                                                .OFF_HAND
                                                                                ? 40
                                                                                : 0)
                                                                        : (int) a[0]]);
                                        case "setItem" -> {
                                            slots[
                                                            a[0] instanceof EquipmentSlot hand
                                                                    ? (hand
                                                                                    == EquipmentSlot
                                                                                            .OFF_HAND
                                                                            ? 40
                                                                            : 0)
                                                                    : (int) a[0]] =
                                                    copy((ItemStack) a[1]);
                                            yield null;
                                        }
                                        case "setItemInMainHand" -> {
                                            slots[0] = copy((ItemStack) a[0]);
                                            yield null;
                                        }
                                        case "setItemInOffHand" -> {
                                            slots[40] = copy((ItemStack) a[0]);
                                            yield null;
                                        }
                                        case "addItem" -> {
                                            Map<Integer, ItemStack> left = new HashMap<>();
                                            ItemStack[] adding = (ItemStack[]) a[0];
                                            for (int n = 0; n < adding.length; n++) {
                                                int empty = -1;
                                                for (int i = 0; i < 36; i++)
                                                    if (slots[i] == null
                                                            || slots[i].getType().isAir()) {
                                                        empty = i;
                                                        break;
                                                    }
                                                if (empty < 0) left.put(n, adding[n].clone());
                                                else slots[empty] = adding[n].clone();
                                            }
                                            yield left;
                                        }
                                        case "contains" ->
                                                java.util.Arrays.stream(slots)
                                                        .anyMatch(
                                                                i ->
                                                                        i != null
                                                                                && i.getType()
                                                                                        == a[0]);
                                        case "removeItem" -> {
                                            var missing =
                                                    new java.util.HashMap<Integer, ItemStack>();
                                            ItemStack[] requested = (ItemStack[]) a[0];
                                            for (int n = 0; n < requested.length; n++) {
                                                int left = requested[n].getAmount();
                                                for (int i = 0; i < 36 && left > 0; i++)
                                                    if (slots[i] != null
                                                            && slots[i].isSimilar(requested[n])) {
                                                        int used =
                                                                Math.min(
                                                                        left, slots[i].getAmount());
                                                        left -= used;
                                                        slots[i].setAmount(
                                                                slots[i].getAmount() - used);
                                                    }
                                                if (left > 0) {
                                                    var rest = requested[n].clone();
                                                    rest.setAmount(left);
                                                    missing.put(n, rest);
                                                }
                                            }
                                            yield missing;
                                        }
                                        case "getContents" ->
                                                Arrays.stream(slots)
                                                        .map(Avatar::copy)
                                                        .toArray(ItemStack[]::new);
                                        case "getStorageContents" ->
                                                Arrays.stream(Arrays.copyOf(slots, 36))
                                                        .map(Avatar::copy)
                                                        .toArray(ItemStack[]::new);
                                        case "setStorageContents" -> {
                                            ItemStack[] input = (ItemStack[]) a[0];
                                            for (int i = 0; i < 36; i++) slots[i] = copy(input[i]);
                                            yield null;
                                        }
                                        case "getArmorContents" -> new ItemStack[4];
                                        case "getHeldItemSlot" -> 0;
                                        case "getSize" -> 41;
                                        case "getMaxStackSize" -> 64;
                                        default -> zero(m.getReturnType());
                                    });
            player =
                    proxy(
                            Player.class,
                            (o, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getName" -> id.toString().substring(0, 12);
                                        case "isOnline", "isValid" -> online;
                                        case "isDead" -> health <= 0;
                                        case "getGameMode" -> gameMode;
                                        case "getInventory" -> inv;
                                        case "getLocation" -> location.clone();
                                        case "getEyeLocation" -> location.clone().add(0, 1.62, 0);
                                        case "getWorld" -> location.getWorld();
                                        case "getPersistentDataContainer" -> pdc;
                                        case "getAttackCooldown" -> attackStrength;
                                        case "getHeight" -> 1.8;
                                        case "getWidth" -> .6;
                                        case "teleport", "teleportAsync" -> {
                                            if (!teleportAllowed)
                                                yield m.getName().equals("teleportAsync")
                                                        ? java.util.concurrent.CompletableFuture
                                                                .completedFuture(false)
                                                        : false;
                                            Location to = (Location) a[0];
                                            location.setX(to.getX());
                                            location.setY(to.getY());
                                            location.setZ(to.getZ());
                                            teleports++;
                                            yield m.getName().equals("teleportAsync")
                                                    ? java.util.concurrent.CompletableFuture
                                                            .completedFuture(true)
                                                    : true;
                                        }
                                        case "getBoundingBox" ->
                                                new org.bukkit.util.BoundingBox(
                                                        location.getX() - .3,
                                                        location.getY(),
                                                        location.getZ() - .3,
                                                        location.getX() + .3,
                                                        location.getY() + 1.8,
                                                        location.getZ() + .3);
                                        case "isHandRaised" -> raised;
                                        case "getItemInUse" ->
                                                activeItem == null
                                                        ? inv.getItemInMainHand()
                                                        : activeItem;
                                        case "getHealth" -> health;
                                        case "getAbsorptionAmount" -> absorption;
                                        case "setHealth" -> {
                                            health = (double) a[0];
                                            yield null;
                                        }
                                        case "isSneaking" -> sneaking;
                                        case "isBlocking" -> raised;
                                        case "getActivePotionEffects" ->
                                                new ArrayList<>(effects.values());
                                        case "addPotionEffect" -> {
                                            var effect = (org.bukkit.potion.PotionEffect) a[0];
                                            effects.put(effect.getType(), effect);
                                            yield true;
                                        }
                                        case "removePotionEffect" -> {
                                            effects.remove(a[0]);
                                            yield null;
                                        }
                                        case "damage" -> {
                                            double damage = (double) a[0];
                                            Entity damager =
                                                    a.length > 1 && a[1] instanceof Entity source
                                                            ? source
                                                            : null;
                                            EntityDamageEvent hit =
                                                    damager == null
                                                            ? new EntityDamageEvent(
                                                                    (Entity) o,
                                                                    EntityDamageEvent.DamageCause
                                                                            .CUSTOM,
                                                                    damage)
                                                            : new EntityDamageByEntityEvent(
                                                                    damager,
                                                                    (Entity) o,
                                                                    EntityDamageEvent.DamageCause
                                                                            .ENTITY_ATTACK,
                                                                    damage);
                                            hit.setCancelled(cancelDamage);
                                            Bukkit.getPluginManager().callEvent(hit);
                                            if (!hit.isCancelled()) {
                                                double left = hit.getFinalDamage();
                                                double absorbed = Math.min(left, absorption);
                                                absorption -= absorbed;
                                                health = Math.max(0, health - left + absorbed);
                                            }
                                            yield null;
                                        }
                                        case "getVelocity" -> new org.bukkit.util.Vector();
                                        case "sendMessage" -> {
                                            if (a[0]
                                                    instanceof net.kyori.adventure.text.Component c)
                                                messages.add(c);
                                            else if (a[0] instanceof String line)
                                                messages.add(
                                                        net.kyori.adventure.text.serializer.legacy
                                                                .LegacyComponentSerializer
                                                                .legacySection()
                                                                .deserialize(line));
                                            yield null;
                                        }
                                        case "sendActionBar" -> {
                                            if (a[0]
                                                    instanceof net.kyori.adventure.text.Component c)
                                                actionBars.add(c);
                                            yield null;
                                        }
                                        case "spigot" ->
                                                new Player.Spigot() {
                                                    @Override
                                                    public void sendMessage(
                                                            net.md_5.bungee.api.ChatMessageType
                                                                    type,
                                                            net.md_5.bungee.api.chat.BaseComponent
                                                                            ...
                                                                    parts) {
                                                        actionBars.add(
                                                                net.kyori.adventure.text.serializer
                                                                        .legacy
                                                                        .LegacyComponentSerializer
                                                                        .legacySection()
                                                                        .deserialize(
                                                                                net.md_5.bungee.api
                                                                                        .chat
                                                                                        .TextComponent
                                                                                        .toLegacyText(
                                                                                                parts)));
                                                    }
                                                };
                                        case "getOpenInventory" -> currentView;
                                        case "getItemOnCursor" -> cursor;
                                        case "setItemOnCursor" -> {
                                            cursor = copy((ItemStack) a[0]);
                                            yield null;
                                        }
                                        case "openInventory" -> {
                                            Inventory top = (Inventory) a[0];
                                            currentView =
                                                    proxy(
                                                            InventoryView.class,
                                                            (view, method, input) ->
                                                                    switch (method.getName()) {
                                                                        case "getTopInventory" ->
                                                                                top;
                                                                        case "getBottomInventory" ->
                                                                                inv;
                                                                        case "getPlayer" -> o;
                                                                        case "getType" ->
                                                                                org.bukkit.event
                                                                                        .inventory
                                                                                        .InventoryType
                                                                                        .CHEST;
                                                                        case "getCursor" -> cursor;
                                                                        case "setCursor" -> {
                                                                            cursor =
                                                                                    copy(
                                                                                            (ItemStack)
                                                                                                    input[
                                                                                                            0]);
                                                                            yield null;
                                                                        }
                                                                        case "getInventory" ->
                                                                                (int) input[0] < 0
                                                                                        ? null
                                                                                        : (int)
                                                                                                                input[
                                                                                                                        0]
                                                                                                        < top
                                                                                                                .getSize()
                                                                                                ? top
                                                                                                : inv;
                                                                        case "getItem" ->
                                                                                (int) input[0]
                                                                                                < top
                                                                                                        .getSize()
                                                                                        ? top
                                                                                                .getItem(
                                                                                                        (int)
                                                                                                                input[
                                                                                                                        0])
                                                                                        : inv
                                                                                                .getItem(
                                                                                                        (int)
                                                                                                                        input[
                                                                                                                                0]
                                                                                                                - top
                                                                                                                        .getSize());
                                                                        case "convertSlot" ->
                                                                                (int) input[0]
                                                                                                < top
                                                                                                        .getSize()
                                                                                        ? (int)
                                                                                                input[
                                                                                                        0]
                                                                                        : (int)
                                                                                                        input[
                                                                                                                0]
                                                                                                - top
                                                                                                        .getSize();
                                                                        default ->
                                                                                zero(
                                                                                        method
                                                                                                .getReturnType());
                                                                    });
                                            yield currentView;
                                        }
                                        case "closeInventory" -> {
                                            InventoryView view = currentView;
                                            currentView = null;
                                            if (view != null)
                                                Bukkit.getPluginManager()
                                                        .callEvent(
                                                                new org.bukkit.event.inventory
                                                                        .InventoryCloseEvent(view));
                                            yield null;
                                        }
                                        case "getScoreboard" ->
                                                Bukkit.getScoreboardManager().getMainScoreboard();
                                        case "setCooldown" -> {
                                            cooldowns.put(a[0], (int) a[1]);
                                            yield null;
                                        }
                                        case "getCooldown" -> cooldowns.getOrDefault(a[0], 0);
                                        case "hasCooldown" -> cooldowns.getOrDefault(a[0], 0) > 0;
                                        case "hashCode" -> id.hashCode();
                                        case "equals" -> o == a[0];
                                        default -> zero(m.getReturnType());
                                    });
        }

        static ItemStack copy(ItemStack item) {
            return item == null ? new ItemStack(Material.AIR) : item.clone();
        }
    }

    void run() throws Exception {
        var plugin = (RunesLegendaryV) Bukkit.getPluginManager().getPlugin("LegendaryWeaponsVL");
        check(
                plugin != null && plugin.isEnabled(),
                "plugin enabled without any other gameplay plugin");
        check(
                plugin.getDescription().getDepend().isEmpty()
                        && plugin.getDescription().getSoftDepend().isEmpty(),
                "zero plugin dependencies");
        check(plugin.getDescription().getAuthors().equals(List.of("Vaileks")), "author");
        check(
                Bukkit.getPluginManager().getPlugins().length == 2,
                "only standalone and test helper installed");
        check(plugin.weapons().size() == 15, "eleven legendary and four epic items");
        check(plugin.getName().equals("LegendaryWeaponsVL"), "renamed plugin metadata");
        check(
                new Keys(plugin).totemCount().toString().equals("runeslegendaryv:totem_count"),
                "old totem-count key retained");
        check(
                new Keys(plugin)
                        .attribute("GambleSword_damage")
                        .toString()
                        .equals("runeslegendaryv:gamblesword_damage"),
                "old attribute namespace retained");
        check(
                plugin.getCommand("lwv") != null && plugin.getCommand("rlv") != null,
                "new command and old alias available");
        if (plugin.getConfig().isSet("rename-migration-check")) {
            check(
                    plugin.getConfig().getString("rename-migration-check", "").equals("preserved"),
                    "legacy configuration migrated");
            check(
                    new java.io.File(plugin.getDataFolder(), "migration-marker.txt").isFile(),
                    "all legacy files preserved");
            check(
                    dev.vaileks.runeslegendaryv.config.Language.text("messages.reloaded")
                            .contains("LegendaryWeaponsVL"),
                    "legacy default reload text renamed");
        }
        Set<String> ids = new HashSet<>();
        for (var entry : plugin.weapons().entrySet()) {
            ItemStack item = entry.getValue().getProcessedItem(),
                    other = entry.getValue().getProcessedItem();
            var meta = item.getItemMeta();
            check(entry.getValue().isItem(item), "own identity " + entry.getKey());
            check(meta.isUnbreakable(), "unbreakable " + entry.getKey());
            check(
                    ServerCompat.model(meta) != null || meta.hasCustomModelData(),
                    "model " + entry.getKey());
            check(
                    !ServerCompat.modernModels() || ServerCompat.tooltip(meta) != null,
                    "tooltip " + entry.getKey());
            check(meta.hasLore() && ServerCompat.lore(meta).size() > 5, "lore " + entry.getKey());
            String id =
                    meta.getPersistentDataContainer()
                            .get(AbstractLegendaryWeapon.ID, PersistentDataType.STRING);
            check(
                    ids.add(id)
                            && !id.equals(
                                    other.getItemMeta()
                                            .getPersistentDataContainer()
                                            .get(
                                                    AbstractLegendaryWeapon.ID,
                                                    PersistentDataType.STRING)),
                    "per-issued-item identity");
            for (var different : plugin.weapons().values())
                if (different != entry.getValue())
                    check(!different.isItem(item), "cross-weapon isolation");
            check(
                    !meta.getPersistentDataContainer().getKeys().stream()
                            .anyMatch(
                                    k ->
                                            k.getNamespace().equals("fullleg")
                                                    || k.getNamespace().equals("commonbloodlib")),
                    "no old plugin data dependency");
        }
        ItemStack glove = plugin.gauntlet().getItem();
        var health = glove.getItemMeta().getAttributeModifiers(Attributes.MAX_HEALTH);
        check(
                health != null
                        && health.stream()
                                .anyMatch(
                                        a ->
                                                a.getAmount() == 4
                                                        && a.getSlotGroup()
                                                                == EquipmentSlotGroup.OFFHAND),
                "two hearts only in offhand");
        ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
        var tm = totem.getItemMeta();
        ServerCompat.model(tm, NamespacedKey.fromString("fixture:custom_totem"));
        tm.setCustomModelData(431);
        tm.addAttributeModifier(
                Attributes.MAX_HEALTH,
                new AttributeModifier(
                        new NamespacedKey("fixture", "health"),
                        2,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.OFFHAND));
        totem.setItemMeta(tm);
        plugin.gauntlet().setStoredTotems(glove, List.of(totem));
        ItemStack saved = ServerCompat.deserializeBytes(ServerCompat.serializeAsBytes(glove));
        check(
                plugin.gauntlet().storedTotems(saved).getFirst().isSimilar(totem),
                "stored totem retains model, CMD and attributes across native serialization");
        check(plugin.gauntlet().getTotems(saved) == 1, "totem count retained");
        plugin.gauntlet().setTotems(saved, 0);
        check(plugin.gauntlet().getTotems(saved) == 0, "withdraw storage count");
        var charges = new TargetCharge();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        charges.select(first);
        charges.record(1000, 40000, .075);
        check(
                charges.bonus(1001) == .075 && !charges.ready(1001, 500),
                "full-hit charge and click interval");
        charges.select(second);
        check(
                charges.bonus(1001) == 0 && charges.ready(1001, 500),
                "damage charge cannot transfer victims");
        check(
                plugin.itemConfig("relsatron").getInt("charge.segments") == 2,
                "rail two display segments");
        check(
                plugin.itemConfig("perchatkastoikosti").getDouble("bash.damage") == 8,
                "gauntlet four hearts true damage");
        var world = Bukkit.getWorlds().getFirst();
        Location at = world.getSpawnLocation().clone().add(0, 5, 0);
        at.getChunk().load();
        var a = new Avatar(at);
        var b = new Avatar(at.clone().add(2, 0, 0));
        ItemStack bashGlove = plugin.gauntlet().getItem();
        Method bashCooldown =
                plugin.gauntlet()
                        .getClass()
                        .getDeclaredMethod(
                                "startBashCooldown", Player.class, ItemStack.class, double.class);
        bashCooldown.setAccessible(true);
        bashCooldown.invoke(plugin.gauntlet(), a.player, bashGlove, 3d);
        check(
                !a.cooldowns.isEmpty(),
                "bash uses the item-stack cooldown API available since Paper 1.21.4");
        check(
                !plugin.gauntlet()
                        .canUseShield(a.player, a.inv.getItemInMainHand(), EquipmentSlot.HAND),
                "bash blocks shield use during its cooldown");
        plugin.resetCooldowns(a.player);
        check(
                plugin.gauntlet().bashReady(a.player, a.inv.getItemInMainHand()),
                "admin reset clears bash cooldown");
        a.slots[0] = plugin.swordOfNature().getItem();
        plugin.swordOfNature().arm(a.player, a.inv.getItemInMainHand());
        check(plugin.swordOfNature().isArmed(a.player), "life sword armed by RMB");
        plugin.swordOfNature().onHit(a.player, b.player);
        check(plugin.swordOfNature().isFrozen(b.player), "first valid hit binds victim");
        long displays = world.getEntitiesByClass(ItemDisplay.class).size();
        check(displays >= 3, "animated multipart vine spawned without model plugins");
        plugin.swordOfNature().clear(b.player);
        check(!plugin.swordOfNature().isFrozen(b.player), "root cleanup");
        var cd = new Cooldowner("fixture");
        ItemStack item = plugin.judgeSword().getItem();
        cd.setCooldownWithoutIndicator(a.player, item, 40);
        check(cd.remaining(a.player, item) > 1500, "persistent player/item cooldown");
        ItemStack roundtrip = ServerCompat.deserializeBytes(ServerCompat.serializeAsBytes(item));
        check(cd.ticksUntilFinish(roundtrip) > 0, "cooldown persists across item serialization");
        cd.clear(a.player, item);
        check(cd.remaining(a.player, item) == 0, "cooldown reset");
        a.slots[0] = plugin.judgeSword().getItem();
        cd.setCooldownWithoutIndicator(a.player, a.slots[0], 100);
        plugin.resetCooldowns(a.player);
        check(
                cd.remaining(a.player, a.inv.getItemInMainHand()) == 0,
                "admin reset writes cooldown cleanup back to inventory copies");
        for (int i = 0; i < 36; i++) a.slots[i] = null;
        a.slots[0] = new ItemStack(Material.DIAMOND, 3);
        a.slots[4] = new ItemStack(Material.EMERALD, 2);
        var before = Map.of(Material.DIAMOND, 3, Material.EMERALD, 2);
        for (int i = 0; i < 40; i++) {
            check(JudgeSwordShuffleStrikeListener.shuffleInventory(a.inv), "judge shuffle");
            var counted = new EnumMap<Material, Integer>(Material.class);
            for (var stack : a.inv.getStorageContents())
                if (stack != null && !stack.getType().isAir())
                    counted.merge(stack.getType(), stack.getAmount(), Integer::sum);
            check(counted.equals(before), "shuffle conserves items");
        }
        var zombie =
                world.spawn(
                        at.clone().add(5, 0, 0),
                        Zombie.class,
                        z -> {
                            z.setAI(false);
                            z.setSilent(true);
                        });
        zombie.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(4);
        zombie.setAbsorptionAmount(4);
        DamageGuard.apply(zombie, null, 6);
        check(
                zombie.getAbsorptionAmount() == 0 && Math.abs(zombie.getHealth() - 18) < .001,
                "pure damage consumes golden hearts first");
        zombie.remove();
        var savedMob =
                world.spawn(
                        at.clone().add(6, 0, 0),
                        Zombie.class,
                        z -> {
                            z.setAI(false);
                            z.setSilent(true);
                            z.getEquipment()
                                    .setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
                        });
        DamageGuard.apply(savedMob, null, 1000);
        check(
                !savedMob.isDead() && savedMob.getHealth() > 0,
                "native totem survives lethal pure damage");
        savedMob.remove();
        a.online = false;
        b.online = false;
        plugin.swordOfNature().clear(a.player);
    }
}
