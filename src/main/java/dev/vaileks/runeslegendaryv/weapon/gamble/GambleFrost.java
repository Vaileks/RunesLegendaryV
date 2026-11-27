package dev.vaileks.runeslegendaryv.weapon.gamble;

import dev.vaileks.runeslegendaryv.compat.ServerCompat;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.joml.Matrix4f;

import java.util.*;

/** Original FrostSword contract: remaining cold decays, hits extend it, then a fixed stun. */
final class GambleFrost {
    private static final class State {
        final Player player;
        final List<BlockDisplay> ice = new ArrayList<>();
        int until, stunUntil;
        Location anchor;

        State(Player player, int until) {
            this.player = player;
            this.until = until;
        }
    }

    private final ConfigurationSection config;
    private final Map<UUID, State> states = new HashMap<>();

    GambleFrost(ConfigurationSection config) {
        this.config = config;
    }

    private int ticks(String key, int fallback) {
        return Math.max(1, Math.min(72000, config.getInt("frost." + key, fallback)));
    }

    void hit(Player player) {
        int now = ServerCompat.tick();
        State state = states.get(player.getUniqueId());
        if (state == null || state.until <= now && state.stunUntil <= now) {
            if (state != null) clear(state);
            state = new State(player, now);
            states.put(player.getUniqueId(), state);
        }
        state.until = Math.max(now, state.until) + ticks("ticks_per_hit", 55);
        update(state, now);
    }

    boolean stunned(Player player) {
        State state = states.get(player.getUniqueId());
        return state != null && state.stunUntil > ServerCompat.tick();
    }

    void move(PlayerMoveEvent event) {
        State state = states.get(event.getPlayer().getUniqueId());
        if (state == null || state.stunUntil <= ServerCompat.tick() || state.anchor == null) return;
        if (event instanceof PlayerTeleportEvent) {
            event.setCancelled(true);
            return;
        }
        Location to = event.getTo();
        if (to != null
                && (to.getWorld() != state.anchor.getWorld()
                        || to.distanceSquared(state.anchor) > 1e-8)) {
            Location anchor = state.anchor.clone();
            anchor.setYaw(to.getYaw());
            anchor.setPitch(to.getPitch());
            event.setTo(anchor);
        }
    }

    void tick() {
        int now = ServerCompat.tick();
        var iterator = states.values().iterator();
        while (iterator.hasNext()) {
            State state = iterator.next();
            if (!state.player.isOnline()
                    || state.player.isDead()
                    || (state.stunUntil > 0 ? state.stunUntil <= now : state.until <= now)) {
                clear(state);
                iterator.remove();
            } else update(state, now);
        }
    }

    private void update(State state, int now) {
        Player player = state.player;
        int remaining = state.until - now;
        int slow = ticks("ticks_to_slow", 40), freeze = ticks("ticks_to_freeze", 230);
        if (state.stunUntil == 0 && remaining >= ticks("ticks_to_stun", 350)) {
            state.stunUntil = now + ticks("stun_duration_ticks", 100);
            state.anchor = player.getLocation().clone();
            player.getWorld().playSound(state.anchor, Sound.BLOCK_GLASS_BREAK, 1, 1);
        }
        if (state.stunUntil > now) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOWNESS, state.stunUntil - now, 100));
        } else {
            if (remaining >= slow)
                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.SLOWNESS,
                                ticks("effect_reapply_ticks", 40),
                                Math.max(
                                        0,
                                        Math.min(
                                                100,
                                                config.getInt("frost.slowness_amplifier", 0)))));
            if (remaining >= freeze) player.setFreezeTicks(ticks("freeze_duration_ticks", 200));
        }
        visual(state, remaining, slow, freeze);
    }

    private BlockDisplay block(State state) {
        BlockDisplay display =
                state.player
                        .getWorld()
                        .spawn(
                                state.player.getLocation(),
                                BlockDisplay.class,
                                d -> {
                                    d.setBlock(Material.ICE.createBlockData());
                                    d.setPersistent(false);
                                    d.setInterpolationDelay(0);
                                    d.setInterpolationDuration(2);
                                    d.setTeleportDuration(1);
                                });
        state.ice.add(display);
        return display;
    }

    private void visual(State state, int remaining, int slow, int freeze) {
        while (state.ice.size() < 2) block(state);
        Location at = state.player.getLocation().clone();
        at.setPitch(0);
        float first = .4f * Math.max(0, Math.min(1, (float) remaining / slow));
        float second =
                .3f
                        * Math.max(
                                0,
                                Math.min(
                                        1,
                                        (float) (remaining - slow) / Math.max(1, freeze - slow)));
        for (int i = 0; i < 2; i++) {
            BlockDisplay display = state.ice.get(i);
            if (display.isValid()) {
                display.teleport(at);
                display.setTransformationMatrix(
                        i == 0
                                ? new Matrix4f().translate(0, .2f, .1f).scale(first)
                                : new Matrix4f().translate(-.4f, 1.3f, .2f).scale(second));
            }
        }
        if (state.stunUntil > ServerCompat.tick()) {
            if (state.ice.size() < 3) block(state);
            var box = state.player.getBoundingBox();
            BlockDisplay full = state.ice.get(2);
            Location around = state.anchor.clone().subtract(box.getWidthX(), 0, box.getWidthZ());
            around.setYaw(0);
            around.setPitch(0);
            full.teleport(around);
            full.setTransformationMatrix(
                    new Matrix4f()
                            .scale(
                                    (float) box.getWidthX() * 2,
                                    (float) box.getHeight() + .2f,
                                    (float) box.getWidthZ() * 2));
        }
    }

    void remove(UUID id) {
        State state = states.remove(id);
        if (state != null) clear(state);
    }

    private void clear(State state) {
        state.ice.forEach(BlockDisplay::remove);
        state.ice.clear();
        PotionEffect current = state.player.getPotionEffect(PotionEffectType.SLOWNESS);
        if (current != null
                && state.stunUntil > 0
                && current.getAmplifier() == 100
                && current.getDuration() <= ticks("stun_duration_ticks", 100))
            state.player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    void clear() {
        states.values().forEach(this::clear);
        states.clear();
    }
}
