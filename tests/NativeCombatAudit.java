package test;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.compat.Attributes;
import dev.vaileks.runeslegendaryv.weapon.gamble.GamblePower;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.*;
import java.util.*;

/**
 * Server-side native players exercise the actual melee and resurrection paths, not proxy damage.
 */
public final class NativeCombatAudit {
    private static final List<NativePlayer> players = new ArrayList<>();
    private static int checks, attacks;
    private static Throwable failure;
    private static RunesLegendaryV plugin;
    private static NativePlayer frostOwner, frostTarget;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static Class<?> type(String name) throws Exception {
        return Class.forName(name);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static final class NativePlayer {
        final Object handle, level, playerList;
        final Player player;

        NativePlayer(World world, String name, double x, double y, double z) throws Exception {
            Object server =
                    Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            level = world.getClass().getMethod("getHandle").invoke(world);
            Object profile =
                    type("com.mojang.authlib.GameProfile")
                            .getConstructor(UUID.class, String.class)
                            .newInstance(UUID.randomUUID(), name);
            Object info =
                    type("net.minecraft.server.level.ClientInformation")
                            .getMethod("createDefault")
                            .invoke(null);
            Class<?> sp = type("net.minecraft.server.level.ServerPlayer");
            handle =
                    sp.getConstructor(
                                    type("net.minecraft.server.MinecraftServer"),
                                    type("net.minecraft.server.level.ServerLevel"),
                                    profile.getClass(),
                                    info.getClass())
                            .newInstance(server, level, profile, info);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object flow =
                    Enum.valueOf(
                            (Class) type("net.minecraft.network.protocol.PacketFlow"),
                            "SERVERBOUND");
            Object connection =
                    type("net.minecraft.network.Connection")
                            .getConstructor(flow.getClass())
                            .newInstance(flow);
            Object cookie =
                    type("net.minecraft.server.network.CommonListenerCookie")
                            .getMethod("createInitial", profile.getClass(), boolean.class)
                            .invoke(null, profile, false);
            Object listener =
                    type("net.minecraft.server.network.ServerGamePacketListenerImpl")
                            .getConstructor(
                                    type("net.minecraft.server.MinecraftServer"),
                                    connection.getClass(),
                                    sp,
                                    cookie.getClass())
                            .newInstance(server, connection, handle, cookie);
            sp.getField("connection").set(handle, listener);
            Method loaded = listener.getClass().getDeclaredMethod("markClientLoaded");
            loaded.setAccessible(true);
            loaded.invoke(listener);
            try {
                sp.getField("invulnerableTime").setInt(handle, 0);
            } catch (NoSuchFieldException ignored) {
                // The old spawn-immunity counter was removed in 26.3.
            }
            sp.getMethod("setPos", double.class, double.class, double.class)
                    .invoke(handle, x, y, z);
            player = (Player) sp.getMethod("getBukkitEntity").invoke(handle);
            playerList = server.getClass().getMethod("getPlayerList").invoke(server);
            @SuppressWarnings("unchecked")
            List<Object> list =
                    (List<Object>) playerList.getClass().getMethod("getPlayers").invoke(playerList);
            list.add(handle);
            for (Class<?> c = playerList.getClass(); c != null; c = c.getSuperclass())
                for (Field f : c.getDeclaredFields())
                    if (f.getName().equals("playersByUUID")) {
                        f.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Map<UUID, Object> map = (Map<UUID, Object>) f.get(playerList);
                        map.put(player.getUniqueId(), handle);
                    }
            level.getClass().getMethod("addNewPlayer", sp).invoke(level, handle);
            player.setGameMode(GameMode.SURVIVAL);
            player.setInvulnerable(false);
            player.setGravity(false);
            players.add(this);
        }

        void strike(Player target, int strengthTicks) throws Exception {
            field(type("net.minecraft.world.entity.LivingEntity"), "attackStrengthTicker")
                    .setInt(handle, strengthTicks);
            target.setHealth(target.getAttribute(Attributes.MAX_HEALTH).getValue());
            target.setNoDamageTicks(0);
            player.attack(target);
        }

        void close() throws Exception {
            @SuppressWarnings("unchecked")
            List<Object> list =
                    (List<Object>) playerList.getClass().getMethod("getPlayers").invoke(playerList);
            list.remove(handle);
            for (Class<?> c = playerList.getClass(); c != null; c = c.getSuperclass())
                for (Field f : c.getDeclaredFields())
                    if (f.getName().equals("playersByUUID")) {
                        f.setAccessible(true);
                        ((Map<?, ?>) f.get(playerList)).remove(player.getUniqueId());
                    }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object reason =
                    Enum.valueOf(
                            (Class) type("net.minecraft.world.entity.Entity$RemovalReason"),
                            "DISCARDED");
            level.getClass()
                    .getMethod("removePlayerImmediately", handle.getClass(), reason.getClass())
                    .invoke(level, handle, reason);
        }
    }

    static void run(RunesLegendaryV instance) throws Exception {
        plugin = instance;
        World world = Bukkit.getWorlds().getFirst();
        Location at = world.getSpawnLocation().clone().add(0, 50, 0);
        at.getChunk().setForceLoaded(true);
        var gloveOwner =
                new NativePlayer(world, "GloveAudit", at.getX() - 100, at.getY(), at.getZ());
        Player player = gloveOwner.player;
        check(
                player.isValid() && player.isOnline(),
                "native player registered in world and player list");
        ItemStack glove = plugin.gauntlet().getItem();
        plugin.gauntlet().setTotems(glove, 2);
        player.getInventory().setItemInOffHand(glove);
        player.setHealth(20);
        player.damage(100);
        check(
                !player.isDead() && player.getHealth() > 0,
                "stored gauntlet totem rescues native zero-health player");
        check(
                plugin.gauntlet().getTotems(player.getInventory().getItemInOffHand()) == 1,
                "native lethal hit consumes exactly one stored totem");
        double hp = player.getHealth();
        player.setNoDamageTicks(0);
        player.damage(5);
        check(player.getHealth() == hp, "post-totem ward blocks subsequent native damage");
        check(
                plugin.gauntlet().getTotems(player.getInventory().getItemInOffHand()) == 1,
                "post-totem protection does not consume another stored totem");
        var shieldTarget =
                new NativePlayer(world, "ShieldTarget", at.getX() + 40, at.getY(), at.getZ());
        var shieldAttacker =
                new NativePlayer(world, "ShieldAttacker", at.getX() + 40, at.getY(), at.getZ() - 2);
        shieldTarget.player.getInventory().setItemInOffHand(plugin.gauntlet().getItem());
        shieldAttacker
                .player
                .getInventory()
                .setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        shieldTarget
                .handle
                .getClass()
                .getMethod("setYRot", float.class)
                .invoke(shieldTarget.handle, 180f);
        Player.class
                .getMethod("startUsingItem", org.bukkit.inventory.EquipmentSlot.class)
                .invoke(shieldTarget.player, org.bukkit.inventory.EquipmentSlot.OFF_HAND);
        Bukkit.getScheduler()
                .runTaskLater(
                        Bukkit.getPluginManager().getPlugin("StandaloneSmoke"),
                        () -> {
                            try {
                                check(
                                        shieldTarget.player.isHandRaised(),
                                        "native gauntlet use remains active");
                                // Server-only players have no client connection tick to advance
                                // shield warm-up.
                                Field remaining =
                                        type("net.minecraft.world.entity.LivingEntity")
                                                .getField("useItemRemaining");
                                remaining.setInt(
                                        shieldTarget.handle,
                                        remaining.getInt(shieldTarget.handle) - 10);
                                check(
                                        shieldTarget.player.isBlocking(),
                                        "gauntlet raises as a native shield");
                                shieldTarget
                                        .handle
                                        .getClass()
                                        .getMethod("setYHeadRot", float.class)
                                        .invoke(shieldTarget.handle, 180f);
                                shieldAttacker.strike(shieldTarget.player, 100);
                                check(
                                        shieldTarget.player.getHealth()
                                                == shieldTarget
                                                        .player
                                                        .getAttribute(Attributes.MAX_HEALTH)
                                                        .getValue(),
                                        "gauntlet shield blocks a real frontal melee hit");
                            } catch (Throwable e) {
                                failure = e;
                            }
                        },
                        8);
        frostOwner = new NativePlayer(world, "FrostOwner", at.getX() + 3, at.getY(), at.getZ());
        frostTarget = new NativePlayer(world, "FrostTarget", at.getX() + 5, at.getY(), at.getZ());
        frostTarget.player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        ItemStack lucky = plugin.gambleSword().getItem();
        frostOwner.player.getInventory().setItemInMainHand(lucky);
        plugin.gambleSword().activate(frostOwner.player, lucky, GamblePower.FROST);
        Bukkit.getScheduler()
                .runTaskTimer(
                        Bukkit.getPluginManager().getPlugin("StandaloneSmoke"),
                        task -> {
                            try {
                                frostOwner.strike(frostTarget.player, 0);
                                check(
                                        frostTarget.player.hasPotionEffect(
                                                org.bukkit.potion.PotionEffectType.SLOWNESS),
                                        "native uncharged lucky sword strike applies original"
                                                + " frost");
                                if (++attacks >= 8) task.cancel();
                            } catch (Throwable e) {
                                failure = e;
                                task.cancel();
                            }
                        },
                        1,
                        10);
        Bukkit.getScheduler()
                .runTaskLater(
                        Bukkit.getPluginManager().getPlugin("StandaloneSmoke"),
                        () -> {
                            try {
                                finish();
                            } catch (Throwable error) {
                                System.err.println("NATIVE_COMBAT_FAIL " + error);
                                error.printStackTrace();
                            } finally {
                                Bukkit.shutdown();
                            }
                        },
                        85);
    }

    static void finish() throws Exception {
        try {
            if (failure != null) throw new AssertionError("native frost failure", failure);
            check(attacks == 8, "eight native uncharged attacks tested");
            Location from = frostTarget.player.getLocation();
            PlayerMoveEvent move =
                    new PlayerMoveEvent(frostTarget.player, from, from.clone().add(1, 0, 0));
            plugin.gambleSword().freeze(move);
            check(
                    move.getTo().distanceSquared(from) < 0.001,
                    "native frost build-up actually anchors movement");
            var teleport =
                    new org.bukkit.event.player.PlayerTeleportEvent(
                            frostTarget.player, from, from.clone().add(10, 0, 0));
            plugin.gambleSword().freezeTeleport(teleport);
            check(teleport.isCancelled(), "original frost stun blocks teleporting");
            check(
                    frostTarget
                                    .player
                                    .getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS)
                                    .getAmplifier()
                            == 100,
                    "original full frost stun amplifier");
            plugin.gambleSword().clearAll();
            var released = new PlayerMoveEvent(frostTarget.player, from, from.clone().add(1, 0, 0));
            plugin.gambleSword().freeze(released);
            check(released.getTo().distanceSquared(from) > .9, "cleanup releases frozen movement");
            System.out.println("NATIVE_COMBAT_PASS checks=" + checks);
        } finally {
            plugin.gambleSword().clearAll();
            for (NativePlayer player : players) player.close();
            players.clear();
        }
    }
}
