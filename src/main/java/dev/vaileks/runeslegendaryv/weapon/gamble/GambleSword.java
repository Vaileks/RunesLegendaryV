package dev.vaileks.runeslegendaryv.weapon.gamble;

import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.compat.Attributes;
import dev.vaileks.runeslegendaryv.compat.ServerCompat;
import dev.vaileks.runeslegendaryv.config.Cfg;
import dev.vaileks.runeslegendaryv.config.Language;
import dev.vaileks.runeslegendaryv.support.AttackStrength;
import dev.vaileks.runeslegendaryv.support.Cooldowner;
import dev.vaileks.runeslegendaryv.support.DamageBridge;
import dev.vaileks.runeslegendaryv.support.DamageGuard;
import dev.vaileks.runeslegendaryv.support.Feedback;
import dev.vaileks.runeslegendaryv.support.TeamAllies;
import dev.vaileks.runeslegendaryv.weapon.AbstractLegendaryWeapon;
import dev.vaileks.runeslegendaryv.weapon.ULegendaryContext;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Standalone lucky-sword runtime. States own their temporary effects and always clean up. */
public final class GambleSword extends AbstractLegendaryWeapon implements Listener {
    private final RunesLegendaryV plugin;
    private final Cooldowner cooldown = new Cooldowner("gamble");
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> rolling = new HashMap<>();
    private final Map<Block, TemporaryWeb> webs = new LinkedHashMap<>();
    private final GambleFrost frost = new GambleFrost(config);
    private final Map<UUID, Long> noFall = new HashMap<>();
    private final AttackStrength swings = new AttackStrength();
    private final Set<UUID> syntheticHits = new HashSet<>();
    private final Set<ItemDisplay> displays = new HashSet<>();
    private final NamespacedKey scaleKey = new NamespacedKey("runeslegendaryv", "gamble_scale");

    private record TemporaryWeb(BlockData previous, long until) {}

    private static final class Session {
        final Player owner;
        final String itemId;
        final GamblePower power;
        final long end;
        final List<PotionEffect> saved = new ArrayList<>();
        final Map<PotionEffectType, PotionEffect> applied = new HashMap<>();
        final Set<UUID> viewers = new HashSet<>();
        long lastHit = Long.MIN_VALUE / 2;
        int shields;

        Session(Player owner, String itemId, GamblePower power, long end) {
            this.owner = owner;
            this.itemId = itemId;
            this.power = power;
            this.end = end;
        }
    }

    public GambleSword(ULegendaryContext context) {
        super(context, "GambleSword");
        plugin = (RunesLegendaryV) context.plugin();
    }

    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            var scale = player.getAttribute(Attributes.SCALE);
            if (scale != null) ServerCompat.removeModifier(scale, scaleKey);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void captureSwing(Player owner, Entity target, float strength) {
        swings.capture(owner, target, strength);
    }

    private String identity(ItemStack item) {
        return item != null && item.hasItemMeta()
                ? item.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING)
                : null;
    }

    private int duration(GamblePower power) {
        return Math.max(
                0,
                Math.min(
                        72000,
                        config.getInt(
                                "powers." + power.key() + ".duration_ticks", power.duration())));
    }

    private double cooldown(GamblePower power) {
        double value = config.getDouble("powers." + power.key() + ".cooldown", power.cooldown());
        return Double.isFinite(value) ? Math.max(0, Math.min(3600, value)) : power.cooldown();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void use(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !ServerCompat.isRightClick(event.getAction())
                || !isItem(event.getItem())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Session current = sessions.get(player.getUniqueId());
        if (current != null) {
            if (current.power == GamblePower.MJOLNIR
                    && Objects.equals(current.itemId, identity(event.getItem())))
                throwThunder(player, event.getItem(), current);
            return;
        }
        if (rolling.containsKey(player.getUniqueId())
                || Feedback.hasCooldown(cooldown, player, event.getItem())) return;
        int ticks = Math.max(1, Math.min(100, config.getInt("roll_ticks", 10)));
        rolling.put(player.getUniqueId(), (long) ServerCompat.tick() + ticks);
    }

    private void roll(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isItem(item) || player.isDead() || !player.isOnline()) return;
        List<GamblePower> pool =
                Arrays.stream(GamblePower.values())
                        .filter(p -> config.getBoolean("powers." + p.key() + ".enabled", true))
                        .toList();
        if (pool.isEmpty()) return;
        GamblePower power =
                pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
        activate(player, item, power);
    }

    /** Also used by integration tests to exercise each outcome without relying on random luck. */
    public void activate(Player player, ItemStack item, GamblePower power) {
        if (sessions.containsKey(player.getUniqueId()) || !isItem(item) || identity(item) == null)
            return;
        int ticks = duration(power);
        Session state =
                new Session(player, identity(item), power, (long) ServerCompat.tick() + ticks);
        if (ticks > 0) sessions.put(player.getUniqueId(), state);
        // Reserve the active window and the following cooldown together, so reconnecting/reloading
        // cannot reset the roll. The cooldown becomes usable only after the power has ended.
        cooldown.setCooldownWithoutIndicator(
                player, item, (int) Math.round(ticks + cooldown(power) * 20));
        writeBack(player, item);
        ServerCompat.sendActionBar(
                player,
                Language.component(
                        "gamble.result", "power", Language.text("gamble.powers." + power.key())));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.2f);
        switch (power) {
            case LIGHT_MACE -> {
                Vector velocity =
                        player.getLocation()
                                .getDirection()
                                .multiply(config.getDouble("light_mace.forward", .6));
                velocity.setY(config.getDouble("light_mace.upward", 1.75));
                player.setVelocity(velocity);
                noFall.put(player.getUniqueId(), (long) ServerCompat.tick() + 200);
                player.getWorld()
                        .spawnParticle(Particle.CLOUD, player.getLocation(), 50, .2, .1, .2, .1);
            }
            case DRAGON -> dragon(player);
            case CRYSTAL -> {
                effect(state, PotionEffectType.HASTE, 1);
                effect(state, PotionEffectType.GLOWING, 0);
            }
            case SPIDER -> cobwebs(player);
            case EXCALIBUR -> state.shields = Math.max(1, config.getInt("excalibur.hits", 6));
            case SHADOW -> {
                effect(state, PotionEffectType.INVISIBILITY, 0);
                for (Player viewer : Bukkit.getOnlinePlayers())
                    if (!viewer.equals(player)) {
                        viewer.hideEntity(plugin, player);
                        state.viewers.add(viewer.getUniqueId());
                    }
            }
            case SHRINKER -> {
                AttributeInstance scale = player.getAttribute(Attributes.SCALE);
                if (scale != null) {
                    ServerCompat.removeModifier(scale, scaleKey);
                    double factor =
                            Math.max(.2, Math.min(2, config.getDouble("shrinker.scale", .5)));
                    ServerCompat.addTransientModifier(
                            scale,
                            new AttributeModifier(
                                    scaleKey,
                                    factor - 1,
                                    AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                                    org.bukkit.inventory.EquipmentSlotGroup.ANY));
                }
            }
            case JACKPOT -> {
                effect(state, PotionEffectType.SPEED, 2);
                effect(state, PotionEffectType.STRENGTH, 2);
                applyJackpot(item, true);
                writeBack(player, item);
                player.playSound(
                        player.getLocation(),
                        config.getString(
                                "jackpot.start_sound", "bloodstone:gamble_sword.gamble_sword2"),
                        1,
                        1);
            }
            case FROST, MJOLNIR, REAPER -> {
                /* Applied by successful melee hits. */
            }
        }
    }

    private void effect(Session state, PotionEffectType type, int amplifier) {
        PotionEffect previous = state.owner.getPotionEffect(type);
        if (previous != null) state.saved.add(previous);
        PotionEffect effect =
                new PotionEffect(
                        type,
                        (int) (state.end - ServerCompat.tick()),
                        amplifier,
                        false,
                        false,
                        true);
        state.applied.put(type, effect);
        state.owner.addPotionEffect(effect);
    }

    private void writeBack(Player player, ItemStack item) {
        String id = identity(item);
        if (id == null) return;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++)
            if (id.equals(identity(inventory.getItem(slot)))) inventory.setItem(slot, item);
    }

    @Override
    public void refreshText(ItemStack item) {
        if (!isItem(item)) return;
        boolean jackpot =
                sessions.values().stream()
                        .anyMatch(
                                s ->
                                        s.power == GamblePower.JACKPOT
                                                && s.itemId.equals(identity(item)));
        applyJackpot(item, jackpot);
        super.refreshText(item);
    }

    private void applyJackpot(ItemStack item, boolean enabled) {
        var meta = item.getItemMeta();
        meta.removeEnchant(Enchantment.SHARPNESS);
        meta.addEnchant(
                Enchantment.SHARPNESS,
                config.getInt(
                        enabled ? "jackpot.sharpness" : "enchants.sharpness", enabled ? 18 : 8),
                true);
        NamespacedKey model =
                NamespacedKey.fromString(
                        config.getString(
                                enabled ? "jackpot.item_model" : "item_model",
                                "bloodstone:tools/gamble_sword/default/gamble_sword"));
        if (model != null) ServerCompat.model(meta, model);
        meta.setEnchantmentGlintOverride(enabled);
        item.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void stopFrozenAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && frost.stunned(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player owner)
                || !(event.getEntity() instanceof LivingEntity target)
                || target.equals(owner)
                || syntheticHits.contains(owner.getUniqueId())
                || event.getFinalDamage() <= 0
                || (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK)
                || !DamageGuard.canDamage(target)
                || !isItem(owner.getInventory().getItemInMainHand())) return;
        if (target instanceof Player player && TeamAllies.areAllies(owner, player)) return;
        Session state = sessions.get(owner.getUniqueId());
        if (state == null
                || !state.itemId.equals(identity(owner.getInventory().getItemInMainHand()))) return;
        long now = ServerCompat.tick();
        float strength = swings.take(owner, target);
        switch (state.power) {
            case FROST -> {
                if (now - state.lastHit < config.getInt("frost.hit_interval_ticks", 10)) return;
                state.lastHit = now;
                if (!(target instanceof Player player)) return;
                frost.hit(player);
                target.getWorld()
                        .spawnParticle(
                                Particle.SNOWFLAKE,
                                target.getLocation().add(0, 1, 0),
                                20,
                                .3,
                                .6,
                                .3,
                                .02);
            }
            case MJOLNIR -> {
                if (strength < 1f) return;
                if (now - state.lastHit < config.getInt("mjolnir.hit_interval_ticks", 25)) return;
                state.lastHit = now;
                afterHit(owner, target, false, () -> lightning(owner, target.getLocation()));
            }
            case REAPER -> {
                if (!(target instanceof Player)) return;
                // Reserve this strike until the native hit has completed. If it is blocked,
                // keep the power available instead of consuming it without any theft.
                if (state.lastHit == now) return;
                state.lastHit = now;
                afterHit(
                        owner,
                        target,
                        true,
                        () -> {
                            if (sessions.get(owner.getUniqueId()) != state
                                    || state.end <= ServerCompat.tick()) return;
                            double before = target.getHealth() + target.getAbsorptionAmount();
                            var potions = new ArrayList<>(target.getActivePotionEffects());
                            double amount =
                                    Math.max(
                                            0,
                                            Math.min(
                                                    100,
                                                    Cfg.of(config).number("reaper.hp_steal", 6)));
                            DamageGuard.execute(target, owner, amount);
                            double dealt =
                                    Math.max(
                                            0,
                                            before
                                                    - target.getHealth()
                                                    - target.getAbsorptionAmount());
                            boolean revived =
                                    !target.isDead() && DamageBridge.justResurrected(target);
                            if (dealt <= 0 && !revived && !target.isDead()) return;
                            if (sessions.get(owner.getUniqueId()) != state) return;
                            sessions.remove(owner.getUniqueId());
                            finish(state);
                            if (revived) return;
                            dealt = Math.min(amount, dealt);
                            for (PotionEffect potion : potions) {
                                owner.addPotionEffect(potion);
                                target.removePotionEffect(potion.getType());
                            }
                            var health = owner.getAttribute(Attributes.MAX_HEALTH);
                            owner.setHealth(
                                    Math.min(
                                            health == null ? 20 : health.getValue(),
                                            owner.getHealth() + dealt));
                            target.getWorld()
                                    .spawnParticle(
                                            Particle.SOUL,
                                            target.getLocation().add(0, 1, 0),
                                            35,
                                            .4,
                                            .5,
                                            .4,
                                            .05);
                        });
            }
            default -> {}
        }
    }

    private void afterHit(Player owner, LivingEntity target, boolean execution, Runnable action) {
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (!owner.isOnline()
                                    || owner.isDead()
                                    || !target.isValid()
                                    || target.isDead()
                                    || !execution && DamageBridge.justResurrected(target)) return;
                            syntheticHits.add(owner.getUniqueId());
                            try {
                                action.run();
                            } finally {
                                syntheticHits.remove(owner.getUniqueId());
                            }
                        });
    }

    private void lightning(Player owner, Location origin) {
        World world = origin.getWorld();
        world.strikeLightningEffect(origin);
        double radius = Math.max(.5, Math.min(8, config.getDouble("mjolnir.radius", 3)));
        for (Entity entity : world.getNearbyEntities(origin, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target)
                    || target.equals(owner)
                    || target.isDead()
                    || (target instanceof Player p && TeamAllies.areAllies(owner, p))) continue;
            Vector delta =
                    target.getEyeLocation()
                            .toVector()
                            .subtract(origin.clone().add(0, .5, 0).toVector());
            if (delta.lengthSquared() > 1e-6
                    && world.rayTraceBlocks(
                                    origin.clone().add(0, .5, 0),
                                    delta,
                                    delta.length(),
                                    FluidCollisionMode.NEVER,
                                    true)
                            != null) continue;
            DamageGuard.apply(target, owner, config.getDouble("mjolnir.damage", 5.25));
        }
    }

    private void throwThunder(Player player, ItemStack item, Session state) {
        long now = ServerCompat.tick();
        if (now - state.lastHit < config.getInt("mjolnir.throw_cooldown_ticks", 160)) return;
        state.lastHit = now;
        Location start = player.getEyeLocation();
        Vector velocity = start.getDirection().multiply(1.5);
        ItemDisplay display =
                start.getWorld()
                        .spawn(
                                start,
                                ItemDisplay.class,
                                d -> {
                                    d.setItemStack(item.clone());
                                    d.setTeleportDuration(1);
                                    d.setPersistent(false);
                                });
        displays.add(display);
        new org.bukkit.scheduler.BukkitRunnable() {
            Location at = start.clone();
            int age;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || !display.isValid() || ++age > 66) {
                    close();
                    return;
                }
                Location next = at.clone().add(velocity);
                if (!next.getWorld().isChunkLoaded(next.getBlockX() >> 4, next.getBlockZ() >> 4)) {
                    close();
                    return;
                }
                var result =
                        at.getWorld()
                                .rayTrace(
                                        at,
                                        velocity,
                                        velocity.length(),
                                        FluidCollisionMode.NEVER,
                                        true,
                                        .35,
                                        e ->
                                                e instanceof LivingEntity
                                                        && !e.equals(player)
                                                        && !(e instanceof Player p
                                                                && TeamAllies.areAllies(
                                                                        player, p)));
                if (result != null) {
                    lightning(player, result.getHitPosition().toLocation(at.getWorld()));
                    close();
                    return;
                }
                at = next;
                display.teleport(at);
                display.setRotation(age * 35, age * 12);
                at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 4, .1, .1, .1, .02);
            }

            void close() {
                display.remove();
                displays.remove(display);
                cancel();
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protect(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Long fallUntil =
                event.getCause() == EntityDamageEvent.DamageCause.FALL
                        ? noFall.remove(player.getUniqueId())
                        : null;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && fallUntil != null
                && fallUntil >= ServerCompat.tick()) {
            event.setCancelled(true);
            return;
        }
        Session state = sessions.get(player.getUniqueId());
        if (state == null || state.end <= ServerCompat.tick()) return;
        if (state.power == GamblePower.SHADOW && event.getFinalDamage() >= 2) {
            sessions.remove(player.getUniqueId());
            finish(state);
            return;
        }
        boolean pvp =
                event instanceof EntityDamageByEntityEvent damage
                        && (damage.getDamager() instanceof Player
                                || (damage.getDamager() instanceof Projectile p
                                        && p.getShooter() instanceof Player));
        if (state.power == GamblePower.JACKPOT && pvp) DamageGuard.protect(event);
        if (state.power == GamblePower.EXCALIBUR
                && event.getFinalDamage() >= config.getDouble("excalibur.min_damage", 1.5)) {
            DamageGuard.protect(event);
            player.getWorld()
                    .spawnParticle(
                            Particle.END_ROD,
                            player.getLocation().add(0, 1, 0),
                            20,
                            .4,
                            .7,
                            .4,
                            .03);
            if (--state.shields <= 0) {
                sessions.remove(player.getUniqueId());
                finish(state);
            }
        }
    }

    private void dragon(Player player) {
        Location start = player.getLocation();
        Vector flat = start.getDirection().setY(0);
        if (flat.lengthSquared() < 1e-6) flat = new Vector(0, 0, 1);
        else flat.normalize();
        Location wanted =
                start.clone()
                        .add(
                                flat.multiply(
                                        Math.max(
                                                1,
                                                Math.min(
                                                        32,
                                                        config.getDouble("dragon.distance", 14)))));
        World world = start.getWorld();
        if (!world.isChunkLoaded(wanted.getBlockX() >> 4, wanted.getBlockZ() >> 4)) return;
        Location destination = world.getHighestBlockAt(wanted).getLocation().add(.5, 1, .5);
        destination.setYaw(start.getYaw());
        destination.setPitch(start.getPitch());
        if (!world.getWorldBorder().isInside(destination)
                || !destination.getBlock().isPassable()
                || !destination.clone().add(0, 1, 0).getBlock().isPassable()
                || destination.clone().subtract(0, 1, 0).getBlock().getType()
                        == Material.MAGMA_BLOCK) return;
        if (player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            player.setFallDistance(0);
            noFall.put(player.getUniqueId(), (long) ServerCompat.tick() + 100);
            world.spawnParticle(Particle.PORTAL, start, 50, .5, 1, .5, .1);
            world.spawnParticle(
                    Particle.DRAGON_BREATH,
                    destination,
                    40,
                    .5,
                    .6,
                    .5,
                    .03,
                    Particle.DRAGON_BREATH.getDataType() == Float.class ? Float.valueOf(1f) : null);
        }
    }

    private void cobwebs(Player player) {
        Location center = player.getLocation();
        long until = (long) ServerCompat.tick() + config.getInt("spider.web_ticks", 300);
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (!(Math.abs(distance - 1) < .5 || Math.abs(distance - 3) < .5)) continue;
                Block block = center.getBlock().getRelative(x, 0, z);
                if (!block.getType().isAir()
                        || new org.bukkit.util.BoundingBox(
                                        block.getX(),
                                        block.getY(),
                                        block.getZ(),
                                        block.getX() + 1,
                                        block.getY() + 1,
                                        block.getZ() + 1)
                                .overlaps(player.getBoundingBox())) continue;
                webs.putIfAbsent(block, new TemporaryWeb(block.getBlockData().clone(), until));
                block.setType(Material.COBWEB, false);
            }
        center.getWorld().playSound(center, Sound.ENTITY_SPIDER_AMBIENT, 1, .7f);
    }

    private void tick() {
        long now = ServerCompat.tick();
        for (var entry : new ArrayList<>(rolling.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null
                    || !player.isOnline()
                    || !isItem(player.getInventory().getItemInMainHand())) {
                rolling.remove(entry.getKey());
                continue;
            }
            if (now >= entry.getValue()) {
                rolling.remove(entry.getKey());
                roll(player);
            } else if (now % 2 == 0) player.playSound(player, Sound.UI_BUTTON_CLICK, .7f, 1.2f);
        }
        for (Session state : new ArrayList<>(sessions.values())) {
            if (state.end <= now || !state.owner.isOnline() || state.owner.isDead()) {
                sessions.remove(state.owner.getUniqueId());
                finish(state);
                continue;
            }
            if (state.power == GamblePower.JACKPOT && now % 20 == 0) {
                String sound =
                        config.getString(
                                "jackpot.loop_sound", "bloodstone:gamble_sword.gamble_sword3");
                state.owner.playSound(state.owner.getLocation(), sound, .6f, 1);
            }
            if (now % 4 == 0)
                state.owner
                        .getWorld()
                        .spawnParticle(
                                state.power == GamblePower.JACKPOT
                                        ? Particle.TOTEM_OF_UNDYING
                                        : Particle.ENCHANT,
                                state.owner.getLocation().add(0, 1, 0),
                                5,
                                .4,
                                .6,
                                .4,
                                .02);
        }
        for (var entry : new ArrayList<>(webs.entrySet()))
            if (entry.getValue().until <= now) {
                restoreWeb(entry.getKey(), entry.getValue());
                webs.remove(entry.getKey());
            }
        frost.tick();
        noFall.entrySet().removeIf(e -> e.getValue() < now);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void freeze(PlayerMoveEvent event) {
        frost.move(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void freezeTeleport(PlayerTeleportEvent event) {
        frost.move(event);
    }

    private void restoreWeb(Block block, TemporaryWeb state) {
        if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                && block.getType() == Material.COBWEB) block.setBlockData(state.previous, false);
    }

    @EventHandler
    public void unload(org.bukkit.event.world.ChunkUnloadEvent event) {
        for (var entry : new ArrayList<>(webs.entrySet()))
            if (entry.getKey().getChunk().equals(event.getChunk())) {
                restoreWeb(entry.getKey(), entry.getValue());
                webs.remove(entry.getKey());
            }
    }

    private void finish(Session state) {
        ItemStack source = null;
        for (ItemStack item : state.owner.getInventory().getContents()) {
            if (state.itemId.equals(identity(item))) {
                source = item;
                break;
            }
        }
        cooldown.setCooldownWithoutIndicator(
                state.owner, source, (int) Math.round(cooldown(state.power) * 20));
        if (source != null) writeBack(state.owner, source);

        if (state.power == GamblePower.SHRINKER) {
            var scale = state.owner.getAttribute(Attributes.SCALE);
            if (scale != null) ServerCompat.removeModifier(scale, scaleKey);
        }
        for (UUID id : state.viewers) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null) viewer.showEntity(plugin, state.owner);
        }
        for (var entry : state.applied.entrySet()) {
            PotionEffect current = state.owner.getPotionEffect(entry.getKey());
            int expected = (int) Math.max(0, state.end - ServerCompat.tick());
            if (current != null
                    && current.getAmplifier() == entry.getValue().getAmplifier()
                    && Math.abs(current.getDuration() - expected) < 5)
                state.owner.removePotionEffect(entry.getKey());
        }
        int elapsed = (int) Math.max(0, duration(state.power) - (state.end - ServerCompat.tick()));
        for (PotionEffect old : state.saved)
            if (!state.owner.hasPotionEffect(old.getType())
                    && (old.isInfinite() || old.getDuration() > elapsed))
                state.owner.addPotionEffect(
                        new PotionEffect(
                                old.getType(),
                                old.isInfinite() ? -1 : old.getDuration() - elapsed,
                                old.getAmplifier(),
                                old.isAmbient(),
                                old.hasParticles(),
                                old.hasIcon()));
        if (state.power == GamblePower.JACKPOT)
            for (ItemStack item : state.owner.getInventory().getContents())
                if (state.itemId.equals(identity(item))) {
                    applyJackpot(item, false);
                    writeBack(state.owner, item);
                }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) {
        Session state = sessions.get(event.getPlayer().getUniqueId());
        if (state != null && state.itemId.equals(identity(event.getItemDrop().getItemStack()))) {
            sessions.remove(state.owner.getUniqueId());
            finish(state);
            if (state.power == GamblePower.JACKPOT) {
                ItemStack dropped = event.getItemDrop().getItemStack();
                applyJackpot(dropped, false);
                event.getItemDrop().setItemStack(dropped);
            }
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        var scale = event.getPlayer().getAttribute(Attributes.SCALE);
        if (scale != null) ServerCompat.removeModifier(scale, scaleKey);
        for (Session state : sessions.values())
            if (state.power == GamblePower.SHADOW) {
                event.getPlayer().hideEntity(plugin, state.owner);
                state.viewers.add(event.getPlayer().getUniqueId());
            }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler
    public void death(PlayerDeathEvent event) {
        clear(event.getEntity());
        for (ItemStack item : event.getDrops()) if (isItem(item)) applyJackpot(item, false);
    }

    private void clear(Player player) {
        Session state = sessions.remove(player.getUniqueId());
        if (state != null) finish(state);
        swings.clear(player);
        rolling.remove(player.getUniqueId());
        noFall.remove(player.getUniqueId());
        frost.remove(player.getUniqueId());
    }

    public void clearAll() {
        for (Session state : sessions.values()) finish(state);
        sessions.clear();
        rolling.clear();
        swings.clear();
        frost.clear();
        noFall.clear();
        webs.forEach(this::restoreWeb);
        webs.clear();
        displays.forEach(Entity::remove);
        displays.clear();
    }
}
