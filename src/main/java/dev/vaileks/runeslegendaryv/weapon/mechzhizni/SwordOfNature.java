package dev.vaileks.runeslegendaryv.weapon.mechzhizni;

import dev.vaileks.runeslegendaryv.support.Scheduler;
import dev.vaileks.runeslegendaryv.support.DeferredCooldowns;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import dev.vaileks.runeslegendaryv.config.Cfg;
import dev.vaileks.runeslegendaryv.util.SoundUtil;
import dev.vaileks.runeslegendaryv.util.ItemUtil;
import dev.vaileks.runeslegendaryv.weapon.AbstractLegendaryWeapon;
import dev.vaileks.runeslegendaryv.weapon.ULegendaryContext;

public class SwordOfNature extends AbstractLegendaryWeapon {
    public static final String KEY = "mechzhizni";
    private final Map<UUID, AttackSample> swings = new HashMap<>();
    private final Map<UUID, Scheduler.Task> visualTasks = new HashMap<>();
    private final Map<UUID, TargetCharge> charges = new HashMap<>();
    private final Map<EntityDamageByEntityEvent, PendingHit> pending = new IdentityHashMap<>();
    private final Map<UUID, Freeze> frozen = new HashMap<>();
    private final Map<UUID, ItemDisplay> roots = new HashMap<>();
    private final Map<UUID, List<ItemDisplay>> rootParts = new HashMap<>();
    private final Set<UUID> armed = new HashSet<>();
    private final Map<UUID, RootSession> rootSessions = new HashMap<>();
    private static final class RootSession {
        long until; LivingEntity victim;
        final List<DeferredCooldowns.Ticket> tickets = new ArrayList<>();
    }
    private final dev.vaileks.runeslegendaryv.support.Cooldowner activeCooldown = new dev.vaileks.runeslegendaryv.support.Cooldowner("nature_active_end");
    private final dev.vaileks.runeslegendaryv.support.Cooldowner passiveCooldown = new dev.vaileks.runeslegendaryv.support.Cooldowner("nature_passive_end");
    private Scheduler.Task auraTask;
    private Scheduler.Task rootTask;

    public SwordOfNature(ULegendaryContext context) { super(context, KEY); }
    private Cfg ability() { return Cfg.child(this.config, "ability"); }
    private Cfg rootEffect() { return Cfg.child(this.config, "root_effect"); }
    private Cfg aura() { return Cfg.child(this.config, "aura"); }
    private Cfg hit() { return Cfg.child(this.config, "hit"); }

    /** Manual cancellation is always allowed, including while an earlier root cools down. */
    public boolean toggleMode(Player player, ItemStack item) {
        if (armed.remove(player.getUniqueId())) {
            Cfg cfg = Cfg.child(config, "disarmed_messages");
            SoundUtil.play(player, cfg.string("sound", "minecraft:block.beacon.deactivate"), cfg.decimal("sound_volume", .6f), cfg.decimal("sound_pitch", 1.2f));
            return true;
        }
        return arm(player, item);
    }

    public boolean arm(Player player, ItemStack item) {
        // Selecting a mode is independent of the root's cooldown.
        armed.add(player.getUniqueId());
        Cfg cfg = Cfg.child(config, "armed_messages");
        SoundUtil.play(player, cfg.string("sound", "block.beacon.power_select"), cfg.decimal("sound_volume", .6f), cfg.decimal("sound_pitch", 1.6f));
        return true;
    }
    public boolean isArmed(Player player) { return armed.contains(player.getUniqueId()); }
    public void disarm(Player player) { armed.remove(player.getUniqueId()); }

    /** Capture before vanilla resets the player's attack-strength ticker. */
    public void captureSwing(Player player, Entity victim) {
        swings.put(player.getUniqueId(), new AttackSample(victim.getUniqueId(), Bukkit.getCurrentTick(), player.getAttackCooldown()));
    }

    /** Only direct, fully recovered swings can apply or earn a charge. */
    public void prepareHit(Player player, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (event.getDamager() != player || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.isCancelled() || !landedDamage(event) && !blockedByShield(event)) return;
        TargetCharge charge = charges.computeIfAbsent(player.getUniqueId(), ignored -> new TargetCharge());
        charge.select(victim.getUniqueId());
        long now = System.currentTimeMillis();
        long interval = Math.max(1, ability().integer("min_hit_interval_ticks", 10)) * 50L;
        AttackSample swing = swings.remove(player.getUniqueId());
        if (swing == null || !swing.target.equals(victim.getUniqueId()) || Bukkit.getCurrentTick() - swing.tick < 0 || Bukkit.getCurrentTick() - swing.tick > 1
                || swing.strength < ability().number("min_attack_strength", .9) || !charge.ready(now, interval)) return;
        double accumulated = charge.bonus(now);
        double bonus = Math.min(Math.max(0, ability().number("max_bonus_damage", 6)), accumulated);
        // A fully blocked swing earns charge, but never pushes bonus damage through a shield.
        if (landedDamage(event)) event.setDamage(event.getDamage() + bonus);
        pending.put(event, new PendingHit(player, victim, charge));
    }

    private boolean blockedByShield(EntityDamageEvent event) {
        return ability().flag("charge_on_shield", true)
            && event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)
            && event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) < 0;
    }

    private boolean landedDamage(EntityDamageEvent event) {
        // Absorption is a successful hit too, even when no red hearts are lost.
        double damage = event.getFinalDamage();
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION))
            damage -= Math.min(0, event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION));
        return damage > 0;
    }

    /** Charge qualification must not suppress a valid armed root hit. */
    public void finishHit(EntityDamageByEntityEvent event) {
        PendingHit hit = pending.remove(event);
        if (event.isCancelled()) return;
        boolean damaging = landedDamage(event);
        if (hit != null && (damaging || blockedByShield(event))) {
            long now = System.currentTimeMillis();
            hit.charge.record(now, (long)(Math.max(0, ability().number("point_duration", 40)) * 1000), ability().number("damage_per_point", .075));
        }
        // This is the confirmed direct hit, even if the attack sample was absent
        // or insufficient for the separate stacking-damage mechanic.
        if (damaging && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getDamager() instanceof Player attacker
                && event.getEntity() instanceof LivingEntity victim
                && isItem(attacker.getInventory().getItemInMainHand())) {
            onHit(attacker, victim);
        }
    }

    public void onHit(Player player, LivingEntity victim) {
        Cfg sound = hit();
        SoundUtil.play(victim.getLocation(), sound.string("sound", "minecraft:block.sweet_berry_bush.break"), sound.decimal("sound_volume", .8f), sound.decimal("sound_pitch", 1));
        spawnHitParticles(victim);
        if (!(victim instanceof Player target)) return;
        boolean active = isArmed(player);
        if (!active) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (dev.vaileks.runeslegendaryv.support.Feedback.hasCooldown(activeCooldown,player,null) || !context.cooldowns().ready(player, "mechzhizni_active")
                || !context.abilityCooldown().ready(player, KEY, held)) return;
        String reservation = KEY + (active ? ":active" : ":passive");
        if (DeferredCooldowns.busy(player, reservation)) return;
        if (!active && (dev.vaileks.runeslegendaryv.support.Feedback.hasCooldown(passiveCooldown,player,null) || !context.cooldowns().ready(player, "mechzhizni_root"))) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        var ticket = DeferredCooldowns.begin(player, reservation, () -> {
            if (!active) {
                context.cooldowns().set(player, "mechzhizni_root", ability().number("cooldown", 40));
                passiveCooldown.setCooldownWithoutIndicator(player, null, Math.max(0, (int)Math.round(ability().number("cooldown", 40) * 20)));
            }
            if (active) {
                context.cooldowns().set(player, "mechzhizni_active", ability().number("cooldown", 40));
                activeCooldown.setCooldownWithoutIndicator(player, null, Math.max(0, (int)Math.round(ability().number("cooldown", 40) * 20)));
                // Resolve the stack after inventory moves; Bukkit copies stack metadata.
                boolean found = false;
                for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                    ItemStack current = player.getInventory().getItem(slot);
                    if (!isItem(current)) continue;
                    context.abilityCooldown().start(player, KEY, current, ability().number("cooldown", 40));
                    player.getInventory().setItem(slot, current); found = true;
                }
                if (!found) context.abilityCooldown().start(player, KEY, item, ability().number("cooldown", 40));
            }
        });
        if (ticket == null) return;
        double seconds = active ? Math.max(rootEffect().number("duration", 4.79), rootEffect().flag("freeze", true) ? rootEffect().number("freeze_time", 4.79) : 0) : rootEffect().number("duration", 4.2);
        RootSession session = rootSessions.computeIfAbsent(target.getUniqueId(), ignored -> new RootSession());
        session.victim = target; session.until = Math.max(session.until, System.currentTimeMillis() + (long)(Math.max(0, seconds) * 1000));
        session.tickets.add(ticket);
        try {
            if (active) { bindVictim(target); }
            else showRoot(target, seconds);
        } catch (RuntimeException failure) {
            session.tickets.remove(ticket); ticket.abort();
            if (session.tickets.isEmpty()) rootSessions.remove(target.getUniqueId());
            throw failure;
        }
    }
    private void finishRoots(UUID id) {
        RootSession session = rootSessions.remove(id);
        if (session != null) session.tickets.forEach(DeferredCooldowns.Ticket::finish);
    }

    private void bindVictim(Player victim) {
        Cfg cfg = rootEffect();
        if (cfg.flag("freeze", true)) frozen.put(victim.getUniqueId(), new Freeze(victim.getLocation().clone(), System.currentTimeMillis() + (long)(cfg.number("freeze_time", 4.79) * 1000)));
        try {
            showRoot(victim, Math.max(cfg.number("duration", 4.79), cfg.flag("freeze", true) ? cfg.number("freeze_time", 4.79) : 0));
        } catch (RuntimeException visualFailure) {
            // Rendering cannot roll back the gameplay root or its cooldown ticket.
            Bukkit.getLogger().log(java.util.logging.Level.WARNING, "Cannot render Sword of Nature vine; player root remains active", visualFailure);
        }
        SoundUtil.play(victim.getLocation(), cfg.string("bind_sound", "minecraft:block.sweet_berry_bush.break"), cfg.decimal("bind_sound_volume", 1), cfg.decimal("bind_sound_pitch", .7f));
    }

    public static String rootModelKey(String configured) {
        final String canonical="runes:effects/life_vines/vine";
        if(configured==null||configured.isBlank())return canonical;
        String value=configured.trim();
        // Item-model components resolve assets/<namespace>/items, NOT models/item paths.
        for(String namespace:List.of("bloodstone","runes","minecraft")) {
            for(String path:List.of("effects/sword_of_nature_roots","item/effects/sword_of_nature_roots"))
                if(value.equals(namespace+":"+path))return canonical;
        }
        if(value.equals("sword_of_nature_roots")||value.equals("effects/sword_of_nature_roots"))return canonical;
        if(value.equals("runes:effects/life_vines")||value.equals("runes:item/effects/life_vines/vine"))return canonical;
        return value; // Preserve an explicitly configured third-party resource.
    }

    private static Transformation rootTransform(double progress, float scale) {
        // Native model is centred around the feet; grow upwards out of the ground.
        float growth = (float)(.25 + .75 * progress);
        return new Transformation(new Vector3f(0, (float)(-.7 * (1 - progress) * scale), 0),
            new Quaternionf(), new Vector3f(scale, scale * growth, scale), new Quaternionf());
    }
    private static Transformation plantTransform(int part,double progress,float scale) {
        return PlantVineRig.transform(part,progress,scale);
    }
    private void showRoot(LivingEntity victim, double seconds) {
        UUID id = victim.getUniqueId();
        removeRoot(id);
        Cfg cfg = rootEffect();
        if (!cfg.flag("model_enabled", true)) return;
        RootSession session = rootSessions.get(id);
        double visualSeconds = session == null ? seconds : Math.max(seconds, (session.until - System.currentTimeMillis()) / 1000.0);
        final int duration = Math.max(1, (int)Math.ceil(Math.max(0, visualSeconds) * 20));
        final double lifetime = duration / 20.0;
        double configuredScale = cfg.number("scale", 1.35);
        final float scale = (float)(Double.isFinite(configuredScale) ? Math.max(.1, Math.min(4, configuredScale)) : 1.35);
        Location location = victim.getLocation().clone().add(cfg.number("offset_x", 0), cfg.number("offset_y", 0), cfg.number("offset_z", 0));
        location.setYaw(0); location.setPitch(0);
        List<ItemDisplay> parts = new ArrayList<>();
        final String configuredModel=rootModelKey(cfg.string("item_model", "runes:effects/life_vines/vine"));
        final boolean plantRig=configuredModel.equals("runes:effects/life_vines/vine");
        try {
            for(int bone=0;bone<(plantRig?PlantVineRig.PARTS.length:1);bone++) {
                final int part=bone;
                NamespacedKey model = NamespacedKey.fromString(plantRig?"runes:effects/life_vines/"+PlantVineRig.PARTS[part]:configuredModel);
                if (model == null) throw new IllegalArgumentException("Invalid root_effect.item_model");
                ItemStack item = new ItemStack(Material.PAPER);
                var meta = item.getItemMeta(); meta.setItemModel(model); item.setItemMeta(meta);
                ItemDisplay display = victim.getWorld().spawn(location, ItemDisplay.class, entity -> {
                    entity.setItemStack(item);
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    entity.setBillboard(Display.Billboard.FIXED);
                    entity.setPersistent(false); entity.setInvulnerable(true); entity.setGravity(false);
                    entity.setViewRange((float)Math.max(16, cfg.number("view_distance", 48)) / 64f);
                    entity.setDisplayWidth(3f); entity.setDisplayHeight(4f);
                    int light = Math.max(0, Math.min(15, cfg.integer("brightness", 15)));
                    entity.setBrightness(new Display.Brightness(light, light));
                    entity.setTeleportDuration(1); entity.setInterpolationDuration(2);
                    entity.setTransformation(plantRig?plantTransform(part,0,scale):rootTransform(0, scale));
                });
                parts.add(display);
            }
        } catch (RuntimeException failure) { parts.forEach(Entity::remove); throw failure; }
        roots.put(id, parts.get(0)); rootParts.put(id, parts);
        final int[] age = {0};
        final double[] previousPose = {-1};
        Scheduler.Task[] ownTask = {null};
        try {
            Scheduler.Task task = context.tasks().entityTimer(parts.get(0), 1, 1, () -> {
                if (roots.get(id) != parts.get(0)) { if (ownTask[0] != null) ownTask[0].cancel(); return; }
                if (parts.stream().anyMatch(part -> !part.isValid()) || !victim.isValid() || victim.isDead()
                        || (victim instanceof Player player && !player.isOnline())
                        || !victim.getWorld().equals(parts.get(0).getWorld()) || age[0]++ >= duration) {
                    removeRoot(id); return;
                }
                Location feet = victim.getLocation().clone().add(cfg.number("offset_x", 0), cfg.number("offset_y", 0), cfg.number("offset_z", 0));
                feet.setYaw(0); feet.setPitch(0);
                double progress = PlantVineRig.progress(age[0] / 20.0, lifetime, cfg.number("grow_time", 1), cfg.number("sink_time", 1));
                boolean animate = age[0] % 2 == 0 && Math.abs(progress - previousPose[0]) > .00001;
                for (int part = 0; part < parts.size(); part++) {
                    ItemDisplay display = parts.get(part);
                    if (display.getLocation().distanceSquared(feet) > .000001) display.teleport(feet);
                    if (animate) {
                        display.setInterpolationDelay(0); display.setInterpolationDuration(2);
                        display.setTransformation(plantRig?plantTransform(part,progress,scale):rootTransform(progress, scale));
                    }
                }
                if (animate) previousPose[0] = progress;
            });
            ownTask[0] = task;
            if (task == null) throw new IllegalStateException("Could not schedule vine root renderer");
            visualTasks.put(id, task);
        } catch (RuntimeException failure) { removeRoot(id); throw failure; }
    }

    private void removeRoot(UUID id) {
        Scheduler.Task task = visualTasks.remove(id);
        if (task != null) task.cancel();
        ItemDisplay primary = roots.remove(id);
        List<ItemDisplay> parts = rootParts.remove(id);
        if (parts != null) parts.forEach(Entity::remove);
        else if (primary != null) primary.remove();
    }

    private void spawnHitParticles(LivingEntity victim) {
        Cfg cfg = hit();
        Particle particle = cfg.particle("particle", Particle.BLOCK);
        Location point = victim.getLocation().clone().add(0, cfg.number("y_offset", 1), 0);
        int count = cfg.integer("count", 8); double offset = cfg.number("offset", .3), speed = cfg.number("speed", .05);
        if (particle == Particle.BLOCK) victim.getWorld().spawnParticle(particle, point, count, offset, offset, offset, speed, cfg.material("block", Material.MOSS_BLOCK).createBlockData());
        else victim.getWorld().spawnParticle(particle, point, count, offset, offset, offset, speed);
    }
    public boolean isFrozen(Player player) {
        Freeze state = frozen.get(player.getUniqueId());
        if (state != null && state.until <= System.currentTimeMillis()) { frozen.remove(player.getUniqueId()); return false; }
        return state != null;
    }
    public void startPassiveTasks() {
        int interval = Math.max(1, aura().integer("interval_ticks", 20));
        auraTask = context.tasks().globalTimer(interval, interval, this::tickAura);
        rootTask = context.tasks().globalTimer(1, 1, this::tickRoots);
    }
    private void tickAura() {
        Cfg cfg = aura();
        for (Player player : Bukkit.getOnlinePlayers()) if (isHeld(player)) {
            double radius = cfg.number("radius", .6);
            player.getWorld().spawnParticle(cfg.particle("particle", Particle.CHERRY_LEAVES), player.getLocation().add(0, cfg.number("y_offset", 1), 0), cfg.integer("count", 3), radius, radius, radius, 0);
        }
    }
    private int rootTicks;
    private void tickRoots() {
        long now = System.currentTimeMillis();
        for (var entry : new ArrayList<>(rootSessions.entrySet())) {
            RootSession session = entry.getValue();
            if (session.until <= now || !session.victim.isValid() || session.victim.isDead()
                    || (session.victim instanceof Player player && !player.isOnline())) {
                // Gameplay completion must not wait for an entity renderer to tick.
                removeRoot(entry.getKey()); frozen.remove(entry.getKey()); finishRoots(entry.getKey());
            }
        }
        if (++rootTicks % 20 == 0) tick();
    }
    private void tick() {
        long now = System.currentTimeMillis();
        for (var entry : new ArrayList<>(frozen.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey()); Freeze freeze = entry.getValue();
            if (player == null || !player.isOnline() || player.isDead() || freeze.until <= now || !player.getWorld().equals(freeze.anchor.getWorld())) { frozen.remove(entry.getKey()); continue; }
            player.setVelocity(new Vector()); player.setFallDistance(0); player.setSprinting(false);
            if (player.getVehicle() != null) player.leaveVehicle();
            if (player.getLocation().distanceSquared(freeze.anchor) > .0001) {
                Location anchor = freeze.anchor.clone(); anchor.setYaw(player.getLocation().getYaw()); anchor.setPitch(player.getLocation().getPitch());
                player.teleport(anchor, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }
        charges.entrySet().removeIf(entry -> {
            Player owner = Bukkit.getPlayer(entry.getKey()); TargetCharge charge = entry.getValue();
            Entity target = charge.target() == null ? null : Bukkit.getEntity(charge.target());
            return owner == null || !owner.isOnline() || owner.isDead() || target == null || target.isDead() || charge.bonus(now) <= 0;
        });
        for (var entry : new ArrayList<>(roots.entrySet())) {
            Entity victim = Bukkit.getEntity(entry.getKey());
            if (victim == null || victim.isDead() || !entry.getValue().isValid()) removeRoot(entry.getKey());
        }
    }
    public boolean isHeld(Player player) { return isItem(player.getInventory().getItemInMainHand()); }
    public void clear(Player player) {
        UUID id = player.getUniqueId(); swings.remove(id); armed.remove(id); frozen.remove(id); charges.remove(id);
        charges.entrySet().removeIf(entry -> id.equals(entry.getValue().target()));
        pending.entrySet().removeIf(entry -> entry.getValue().attacker == player || entry.getValue().victim == player);
        removeRoot(id); finishRoots(id);
    }
    public void clear() {
        if (auraTask != null) { auraTask.cancel(); auraTask = null; }
        if (rootTask != null) { rootTask.cancel(); rootTask = null; }
        for (UUID id : new ArrayList<>(rootSessions.keySet())) finishRoots(id);
        for (UUID id : new ArrayList<>(roots.keySet())) removeRoot(id);
        visualTasks.values().forEach(Scheduler.Task::cancel); visualTasks.clear();
        roots.values().forEach(Entity::remove); roots.clear(); rootParts.clear(); swings.clear(); charges.clear(); pending.clear(); frozen.clear(); armed.clear();
    }
    private record AttackSample(UUID target, int tick, float strength) {}
    private record PendingHit(Player attacker, LivingEntity victim, TargetCharge charge) {}
    private record Freeze(Location anchor, long until) {}
}
