package dev.vaileks.runeslegendaryv.weapon.perchatkastoikosti;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import dev.vaileks.runeslegendaryv.config.Cfg;
import dev.vaileks.runeslegendaryv.menu.TotemMenu;
import dev.vaileks.runeslegendaryv.util.Debug;
import dev.vaileks.runeslegendaryv.util.ItemUtil;
import dev.vaileks.runeslegendaryv.util.SoundUtil;
import dev.vaileks.runeslegendaryv.weapon.AbstractLegendaryWeapon;
import dev.vaileks.runeslegendaryv.weapon.ULegendaryContext;

public class Gauntlet
extends AbstractLegendaryWeapon {
    public static final String KEY = "perchatkastoikosti";
    private static volatile Gauntlet active;
    private static final ThreadLocal<TotemBurst> CURRENT_BURST = new ThreadLocal<>();
    private static final class TotemBurst {
        final Player owner; final Location center; final double radius,max,min; final boolean pure;
        final java.util.Set<UUID> seen=new java.util.HashSet<>(),denied=new java.util.HashSet<>();
        boolean explosion=true; UUID direct;
        TotemBurst(Player owner,Location center,double radius,double max,double min,boolean pure){
            this.owner=owner;this.center=center;this.radius=radius;this.max=max;this.min=min;this.pure=pure;
        }
    }
    public static double burstDamage(double distance,double radius,double max,double min) {
        if(!Double.isFinite(distance)||!Double.isFinite(radius)||radius<=0||distance<0||distance>radius)return 0;
        max=Double.isFinite(max)?Math.max(0,max):12;min=Double.isFinite(min)?Math.max(0,Math.min(max,min)):Math.min(max,2);
        return min+(max-min)*(1-distance/radius);
    }
    /** Rewrite the existing native hit; never add another hit to a target already touched by the explosion. */
    public void adjustTotemBurstDamage(EntityDamageEvent event) {
        TotemBurst burst=CURRENT_BURST.get();if(!burstEvent(burst,event))return;
        UUID id=event.getEntity().getUniqueId();burst.seen.add(id);
        if(event.isCancelled()){burst.denied.add(id);return;}
        double damage=burstDamage(event.getEntity().getLocation().distance(burst.center),burst.radius,burst.max,burst.min);
        event.setDamage(damage);
        if(burst.pure && event instanceof EntityDamageByEntityEvent hit
                && (!event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)||event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)>=0))makeTrueDamage(hit);
    }
    public void confirmTotemBurstDamage(EntityDamageByEntityEvent event) {
        TotemBurst burst=CURRENT_BURST.get();if(!burstEvent(burst,event))return;
        if(event.isCancelled()||dev.vaileks.runeslegendaryv.support.DamageGuard.wasProtected(event))burst.denied.add(event.getEntity().getUniqueId());
    }
    public static boolean isTotemBurstDamage(EntityDamageEvent event) {return burstEvent(CURRENT_BURST.get(),event);}
    private static boolean burstEvent(TotemBurst burst,EntityDamageEvent event) {
        return burst!=null && event.getEntity() instanceof LivingEntity && event.getEntity()!=burst.owner
                && (burst.explosion && (event.getCause()==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION||event.getCause()==EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)
                    || !burst.explosion && event.getEntity().getUniqueId().equals(burst.direct));
    }

    private static final int MAX_TOTEMS = 4;
    private static final NamespacedKey BASH_UNTIL = new NamespacedKey("runeslegendaryv", "gauntlet_bash_until");
    private static final NamespacedKey BASH_GROUP = new NamespacedKey("runeslegendaryv", "gauntlet_bash");
    private static final NamespacedKey STORAGE_ID = new NamespacedKey("runeslegendaryv", "gauntlet_storage_id");
    private final Map<EntityResurrectEvent, PendingResurrection> resurrections = new java.util.IdentityHashMap<>();
    private record PendingResurrection(int slot, String storageId, ItemStack totem) {}
    private final Map<UUID, Immunity> immunities = new HashMap<UUID, Immunity>();
    private final Map<UUID, Long> hitProtectionUntil = new HashMap<>();
    private final java.util.Set<UUID> bursting = new java.util.HashSet<>();
    private final Map<UUID, Long> lastShieldSound = new HashMap<>();

    public Gauntlet(ULegendaryContext uLegendaryContext) {
        super(uLegendaryContext, KEY);
        active=this;
    }

    @Override
    protected ItemStack buildItem() {
        ItemStack itemStack = this.buildGauntletItem();
        this.applyStateModel(itemStack, 0);
        return itemStack;
    }

    @Override
    protected List<ItemStack> buildTemplates() {
        ArrayList<ItemStack> arrayList = new ArrayList<ItemStack>(5);
        for (int i = 0; i <= 4; ++i) {
            ItemStack itemStack = this.buildGauntletItem();
            this.applyStateModel(itemStack, i);
            arrayList.add(itemStack);
        }
        return arrayList;
    }

    private ItemStack buildGauntletItem() {
        ItemStack itemStack = super.buildItem();
        ItemMeta meta=itemStack.getItemMeta();meta.setUnbreakable(true);itemStack.setItemMeta(meta);
        this.applyOffhandStats(itemStack);
        return itemStack;
    }

    private void applyOffhandStats(ItemStack itemStack) {
        applyCooldownGroup(itemStack);
        this.setOffhandAttribute(itemStack, Attribute.ARMOR, this.stats().number("armor", 4.0), "perchatkastoikosti_armor");
        this.setOffhandAttribute(itemStack, Attribute.MAX_HEALTH, this.stats().number("max_health", 4.0), "perchatkastoikosti_health");
        this.setOffhandAttribute(itemStack, Attribute.ATTACK_DAMAGE, this.stats().number("attack_damage", 2.0), "perchatkastoikosti_damage");
    }

    private void setOffhandAttribute(ItemStack itemStack, Attribute attribute, double amount, String keyName) {
        ItemMeta meta=itemStack.getItemMeta();
        NamespacedKey legacy=this.keys.attribute(keyName), key=this.keys.attribute(keyName+"_offhand");
        Collection<AttributeModifier> modifiers=meta.getAttributeModifiers(attribute);
        boolean correct=false,changed=false;
        if(modifiers!=null)for(AttributeModifier modifier:List.copyOf(modifiers)) {
            if(modifier.getKey().equals(key) && amount!=0 && modifier.getAmount()==amount
                    && modifier.getOperation()==AttributeModifier.Operation.ADD_NUMBER
                    && modifier.getSlotGroup().equals(EquipmentSlotGroup.OFFHAND))correct=true;
            else if(modifier.getKey().equals(key)||modifier.getKey().equals(legacy)) {
                meta.removeAttributeModifier(attribute,modifier);changed=true;
            }
        }
        if(amount!=0 && !correct) {
            meta.addAttributeModifier(attribute,new AttributeModifier(key,amount,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.OFFHAND));changed=true;
        }
        if(changed)itemStack.setItemMeta(meta);
    }

    /** Also repairs already issued gloves; never resets stored totems or other metadata. */
    public void repairItem(ItemStack item) {
        if(!isItem(item))return;
        ItemMeta meta=item.getItemMeta();
        boolean changed=!meta.isUnbreakable();meta.setUnbreakable(true);
        if(meta instanceof org.bukkit.inventory.meta.Damageable damage && damage.getDamage()!=0) {
            damage.setDamage(0);changed=true;
        }
        if(changed)item.setItemMeta(meta);
        applyOffhandStats(item);
    }

    private Cfg stats() {
        return Cfg.child(this.config, "stats");
    }

    private Cfg ability() {
        return Cfg.child(this.config, "ability");
    }

    private Cfg slam() {
        return Cfg.child(this.config, "bash");
    }

    private Cfg totemMenu() {
        return Cfg.child(this.config, "totem_menu");
    }

    public void applyStateModel(ItemStack itemStack, int n) {
        NamespacedKey namespacedKey;
        String string;
        if (this.config != null && (string = this.config.getString("states." + n + ".item_model")) != null && itemStack.hasItemMeta() && (namespacedKey = NamespacedKey.fromString((String)string)) != null) {
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.setItemModel(namespacedKey);
            itemStack.setItemMeta(itemMeta);
        }
    }

    public int getTotems(ItemStack itemStack) {
        if (itemStack != null && this.isItem(itemStack)) {
            Byte by = (Byte)itemStack.getItemMeta().getPersistentDataContainer().get(this.keys.totemCount(), PersistentDataType.BYTE);
            return by == null ? 0 : Math.max(0, Math.min(4, by.byteValue()));
        }
        return 0;
    }

    private static NamespacedKey storedTotemKey(int slot) { return new NamespacedKey("runeslegendaryv", "gauntlet_totem_" + slot); }

    /** Native item bytes retain every component, including attributes, model and custom PDC. */
    public java.util.List<ItemStack> storedTotems(ItemStack glove) {
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        int count = getTotems(glove);
        if (count == 0) return items;
        var data = glove.getItemMeta().getPersistentDataContainer();
        for (int slot = 0; slot < count; slot++) {
            byte[] bytes = data.get(storedTotemKey(slot), PersistentDataType.BYTE_ARRAY);
            ItemStack item = bytes == null ? new ItemStack(Material.TOTEM_OF_UNDYING) : ItemStack.deserializeBytes(bytes);
            if (item.getType() != Material.TOTEM_OF_UNDYING) throw new IllegalStateException("Invalid stored gauntlet totem");
            item.setAmount(1); items.add(item);
        }
        return items;
    }

    public void setStoredTotems(ItemStack glove, java.util.List<ItemStack> items) {
        if (!isItem(glove) || items.size() > MAX_TOTEMS) throw new IllegalArgumentException("Invalid gauntlet storage");
        java.util.List<byte[]> encoded = new java.util.ArrayList<>();
        for (ItemStack source : items) {
            if (source == null || source.getType() != Material.TOTEM_OF_UNDYING || source.getAmount() <= 0) throw new IllegalArgumentException("Only totems can be stored");
            ItemStack one = source.clone(); one.setAmount(1); encoded.add(one.serializeAsBytes());
        }
        ItemMeta meta = glove.getItemMeta(); var data = meta.getPersistentDataContainer();
        for (int slot = 0; slot < MAX_TOTEMS; slot++) {
            if (slot < encoded.size()) data.set(storedTotemKey(slot), PersistentDataType.BYTE_ARRAY, encoded.get(slot));
            else data.remove(storedTotemKey(slot));
        }
        data.set(this.keys.totemCount(), PersistentDataType.BYTE, (byte)items.size());
        glove.setItemMeta(meta); applyStateModel(glove, items.size());
    }

    public void setTotems(ItemStack glove, int count) {
        if (!isItem(glove)) return;
        int size = Math.max(0, Math.min(MAX_TOTEMS, count));
        java.util.List<ItemStack> items = storedTotems(glove);
        while (items.size() > size) items.remove(items.size() - 1);
        while (items.size() < size) items.add(new ItemStack(Material.TOTEM_OF_UNDYING));
        setStoredTotems(glove, items);
    }

    public void applyStats(Player player) {
        ItemStack itemStack = player.getInventory().getItemInOffHand();
        if (this.isItem(itemStack)) {
            this.applyOffhandStats(itemStack);
            player.getInventory().setItemInOffHand(itemStack);
        }
    }

    public void clearStats(Player player) {
        ItemUtil.removePlayerAttribute((Attributable)player, Attribute.ARMOR, this.keys.attribute("perchatkastoikosti_armor"));
        ItemUtil.removePlayerAttribute((Attributable)player, Attribute.MAX_HEALTH, this.keys.attribute("perchatkastoikosti_health"));
        ItemUtil.removePlayerAttribute((Attributable)player, Attribute.ATTACK_DAMAGE, this.keys.attribute("perchatkastoikosti_damage"));
    }

    public boolean isEquipped(Player player) {
        return this.isItem(player.getInventory().getItemInOffHand());
    }

    public void syncStats(Player player) {
        // Only legacy direct player keys are removed. New equipment keys are never removed here.
        this.clearStats(player);
        for(int slot=0;slot<player.getInventory().getSize();slot++) {
            ItemStack item=player.getInventory().getItem(slot);
            if(!isItem(item))continue;
            ItemMeta before=item.getItemMeta();repairItem(item);
            if(!before.equals(item.getItemMeta()))player.getInventory().setItem(slot,item);
        }
    }

    public void onSwap(Player player) {
        this.context.tasks().entityLater((Entity)player, 1L, () -> this.syncStats(player));
    }

    public void onAttack(Player player, LivingEntity livingEntity, EntityDamageByEntityEvent entityDamageByEntityEvent) {
        if(CURRENT_BURST.get()!=null)return;
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        Cfg cfg = this.slam();
        if (entityDamageByEntityEvent.getDamager() != player || entityDamageByEntityEvent.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !this.isItem(itemStack) || !cfg.flag("enabled", true) || player.getAttackCooldown() < cfg.number("min_attack_strength", 0.9) || !bashReady(player, itemStack)) {
            return;
        }
        startBashCooldown(player, itemStack, cfg.number("cooldown", 15.0));
        entityDamageByEntityEvent.setDamage(Math.max(0,cfg.number("damage",8.0)));
        if(cfg.flag("true_damage",true))makeTrueDamage(entityDamageByEntityEvent);
        Vector vector = livingEntity.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
        if (vector.lengthSquared() < 1.0E-4) {
            vector = player.getLocation().getDirection().setY(0.0);
        }
        if (vector.lengthSquared() < 1.0E-4) {
            vector = new Vector(0.0, 0.0, 1.0);
        }
        boolean inWater = player.isInWater() || livingEntity.isInWater();
        double knockback = inWater ? cfg.number("water_knockback", 0.2) : cfg.number("knockback", 3.5);
        double knockbackY = inWater ? cfg.number("water_knockback_y", 0.02) : cfg.number("knockback_y", 0.55);
        vector.normalize().multiply(knockback).setY(knockbackY);
        livingEntity.setVelocity(vector);
        SoundUtil.play(player.getLocation(), cfg.string("sound", "minecraft:entity.player.attack.strong"), cfg.decimal("sound_volume", 1.0f), cfg.decimal("sound_pitch", 1.0f));
        player.getWorld().spawnParticle(cfg.particle("particle", Particle.CLOUD), livingEntity.getLocation().add(0.0, cfg.number("particle_y_offset", 1.0), 0.0), cfg.integer("particle_count", 50), cfg.number("particle_offset_x", 0.1), cfg.number("particle_offset_y", 0.1), cfg.number("particle_offset_z", 0.1), cfg.number("particle_speed", 0.5));
    }

    /** Keep the native damage event, absorption, cancellation and resurrection pipeline. */
    private static void makeTrueDamage(EntityDamageByEntityEvent event) {
        for(EntityDamageEvent.DamageModifier modifier:EntityDamageEvent.DamageModifier.values()) {
            if(modifier!=EntityDamageEvent.DamageModifier.BASE && modifier!=EntityDamageEvent.DamageModifier.ABSORPTION
                    && event.isApplicable(modifier))event.setDamage(modifier,0);
        }
        // Bukkit's original absorption modifier was calculated after armor/resistance.
        // Recalculate it for the full true hit, leaving native absorption consumption intact.
        if(event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)
                && event.getEntity() instanceof LivingEntity target) {
            double absorbed=Math.min(Math.max(0,event.getDamage()),Math.max(0,target.getAbsorptionAmount()));
            event.setDamage(EntityDamageEvent.DamageModifier.ABSORPTION,-absorbed);
        }
    }

    private void applyCooldownGroup(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        var cooldown = meta.getUseCooldown();
        cooldown.setCooldownSeconds(.05f);
        cooldown.setCooldownGroup(BASH_GROUP);
        meta.setUseCooldown(cooldown);
        item.setItemMeta(meta);
    }

    public boolean bashReady(Player player, ItemStack item) {
        Long until = item.getItemMeta().getPersistentDataContainer().get(BASH_UNTIL, PersistentDataType.LONG);
        if(until!=null&&until>System.currentTimeMillis())dev.vaileks.runeslegendaryv.support.Feedback.waiting(player,until-System.currentTimeMillis());
        return (until == null || until <= System.currentTimeMillis())
            && context.cooldowns().ready(player, "perchatkastoikosti_slam");
    }

    private void startBashCooldown(Player player, ItemStack item, double seconds) {
        context.cooldowns().set(player, "perchatkastoikosti_slam", seconds);
        applyCooldownGroup(item);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(BASH_UNTIL, PersistentDataType.LONG,
            System.currentTimeMillis() + (long)(Math.max(0, seconds) * 1000));
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
        player.clearActiveItem();
        player.setCooldown(BASH_GROUP, Math.max(0, (int)Math.ceil(seconds * 20)));
    }

    /** Shield and bash share a cooldown; resurrection intentionally never consults it. */
    public boolean canUseShield(Player player, ItemStack item, org.bukkit.inventory.EquipmentSlot hand) {
        if (bashReady(player, item)) return true;
        applyCooldownGroup(item);
        if (hand == org.bukkit.inventory.EquipmentSlot.HAND) player.getInventory().setItemInMainHand(item);
        else player.getInventory().setItemInOffHand(item);
        Long until = item.getItemMeta().getPersistentDataContainer().get(BASH_UNTIL, PersistentDataType.LONG);
        long remaining = Math.max(context.cooldowns().remaining(player, "perchatkastoikosti_slam"),
            until == null ? 0 : until - System.currentTimeMillis());
        player.setCooldown(BASH_GROUP, Math.max(1, (int)Math.ceil(remaining / 50.0)));
        player.clearActiveItem();
        return false;
    }

    public void onRightClick(Player player) {
        Cfg cfg = this.ability();
        // Raising a shield is silent in vanilla; play a custom cue only if configured.
        long now = System.currentTimeMillis();
        long interval = Math.max(250L, (long)(cfg.number("shield_sound_cooldown", 1.0) * 1000.0));
        if (now - this.lastShieldSound.getOrDefault(player.getUniqueId(), 0L) < interval) return;
        String sound = cfg.string("shield_sound", "");
        if (sound.isBlank()) return;
        this.lastShieldSound.put(player.getUniqueId(), now);
        SoundUtil.play(player.getLocation(), sound, cfg.decimal("shield_sound_volume", 0.6f), cfg.decimal("shield_sound_pitch", 1.0f));
    }

    public void onDamage(Player player, EntityDamageEvent event) {
        if(event.isCancelled() || !blocksDamage(player,event.getDamage()))return;
        event.setCancelled(true);
        // Legacy compensation must not restore damage after the gauntlet cancelled it.
        dev.vaileks.runeslegendaryv.support.DamageGuard.protect(event);
    }

    private boolean blocksDamage(Player player,double damage) {
        UUID id=player.getUniqueId();
        if(bursting.contains(id))return true;
        long now=System.currentTimeMillis();
        Long protectedUntil=hitProtectionUntil.get(id);
        if(protectedUntil!=null) {
            if(now<protectedUntil)return true;
            hitProtectionUntil.remove(id);
        }
        Immunity immunity=immunities.get(id);
        if(immunity==null)return false;
        if(now>=immunity.until() || immunity.hitsLeft()<=0) {immunities.remove(id);return false;}
        Cfg cfg=ability();
        long interval=Math.max(0,cfg.integer("hit_cooldown_ticks",10))*50L;
        if(damage<cfg.number("min_damage_threshold",1.5) || now-immunity.lastHit()<interval)return true;
        immunity.lastHit(now);
        immunity.hitsLeft(immunity.hitsLeft()-1);
        if(interval>0)hitProtectionUntil.put(id,Math.min(immunity.until(),now+interval));
        SoundUtil.play(player.getLocation(),cfg.string("immunity_hit_sound","minecraft:entity.zombie.attack_iron_door"),cfg.decimal("immunity_hit_sound_volume",1),cfg.decimal("immunity_hit_sound_pitch",1));
        if(immunity.hitsLeft()<=0)immunities.remove(id);
        return true;
    }

    /** Direct-health legacy helpers have no damage event: check the same gauntlet protection first. */
    public static boolean protectPureDamage(org.bukkit.entity.Damageable target,double damage) {
        Gauntlet glove=active;
        if(glove==null || !(target instanceof Player player) || damage<=0)return false;
        if(player.getGameMode()==GameMode.CREATIVE || player.getGameMode()==GameMode.SPECTATOR)return true;
        if(glove.blocksDamage(player,damage))return true;
        // Stored stock must use EntityResurrectEvent just like a totem held in a hand.
        // A denied native damage/resurrection event never falls back to direct setHealth.
        if(damage>=player.getHealth()+player.getAbsorptionAmount() && glove.findTotemSlot(player)>=0) {
            player.damage(10000.0);
            return true;
        }
        return false;
    }

    public boolean tryResurrect(Player player, EntityResurrectEvent event) {
        // An actual totem in either hand follows vanilla and must not also consume stored stock.
        if (event.getHand() != null || resurrections.containsKey(event)) return false;
        int slot = findTotemSlot(player);
        if (slot < 0) return false;
        ItemStack item = player.getInventory().getItem(slot);
        String id = ensureStorageId(item);
        var stock = storedTotems(item);
        ItemStack consumed = stock.remove(0);
        setStoredTotems(item, stock);
        player.getInventory().setItem(slot, item);
        resurrections.put(event, new PendingResurrection(slot, id, consumed));
        event.setCancelled(false);
        return true;
    }

    /** Commit the custom effect only after cancellable resurrection handlers. */
    public void finishResurrect(Player player, EntityResurrectEvent event) {
        PendingResurrection pending = resurrections.remove(event);
        if (pending == null && (event.isCancelled() || !isItem(player.getInventory().getItemInMainHand())
                && !isItem(player.getInventory().getItemInOffHand()))) return;
        if (event.isCancelled()) {
            ItemStack item = player.getInventory().getItem(pending.slot());
            if (sameStorage(item, pending.storageId())) {
                var stock = storedTotems(item);
                stock.add(0, pending.totem().clone());
                setStoredTotems(item, stock);
                player.getInventory().setItem(pending.slot(), item);
            }
            return;
        }
        Cfg cfg = ability();
        double duration = cfg.number("immortality_duration", 15);
        int hits = cfg.integer("immortality_hits", 3);
        if (duration > 0 && hits > 0) immunities.put(player.getUniqueId(),
            new Immunity(System.currentTimeMillis() + (long)(duration * 1000), hits, 0));
        // Vanilla finishes its resurrection after the event; its hit velocity must not overwrite the burst.
        context.tasks().entityLater(player, 1, () -> {
            if (!event.isCancelled() && player.isOnline() && !player.isDead()) activateTotem(player);
        });
    }

    private void activateTotem(Player player) {
        double d;
        Location location = player.getLocation();
        // The successful native resurrection already restores health, effects and totem animation.
        // This delayed task only adds the gauntlet burst; do not reset the revived player again.
        Cfg cfg = this.ability();
        TotemBurst previous=CURRENT_BURST.get();
        TotemBurst effect=new TotemBurst(player,location.clone(),Math.max(0,cfg.number("velocity_radius",8)),
                cfg.number("burst_max_damage",12),cfg.number("burst_min_damage",2),cfg.flag("burst_true_damage",true));
        CURRENT_BURST.set(effect);
        try {
            boolean burst=true;
            if((d=cfg.number("explosion_power",3))>0) {
                bursting.add(player.getUniqueId());
                try {burst=player.getWorld().createExplosion(location,(float)d,false,false,player);}
                finally {bursting.remove(player.getUniqueId());}
            }
            effect.explosion=false;
            if(burst) this.knockback(player,effect.radius,cfg.number("velocity_strength",4),cfg.number("velocity_y",1.2));
        } finally {if(previous==null)CURRENT_BURST.remove();else CURRENT_BURST.set(previous);}
        Cfg cfg2 = Cfg.child(this.config, "totem_break");
        SoundUtil.play(location, cfg2.string("sound", "block.respawn_anchor.deplete"), cfg2.decimal("sound_volume", 1.0f), cfg2.decimal("sound_pitch", 1.0f));
    }

    private void knockback(Player player, double radius, double strength, double vertical) {
        if (radius <= 0) return;
        Location center = player.getLocation();
        for (Entity entity : player.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity.equals(player) || !entity.isValid() || entity.isDead()
                    || entity.getLocation().distanceSquared(center) > radius * radius
                    || entity instanceof org.bukkit.entity.Display
                    || entity instanceof Player target && (target.getGameMode() == GameMode.SPECTATOR || target.getGameMode() == GameMode.CREATIVE)) continue;
            TotemBurst burst=CURRENT_BURST.get();
            if(burst!=null && entity instanceof LivingEntity living && entity.getUniqueId()!=null) {
                UUID id=entity.getUniqueId();
                if(burst.denied.contains(id)||dev.vaileks.runeslegendaryv.support.DamageBridge.justResurrected(living))continue;
                if(!burst.seen.contains(id)) {
                    Location from=center.clone().add(0,.5,0),to=living.getLocation().add(0,Math.min(1,living.getHeight()*.5),0);
                    Vector ray=to.toVector().subtract(from.toVector());double length=ray.length();
                    if(length>.01 && player.getWorld().rayTraceBlocks(from,ray.multiply(1/length),length,org.bukkit.FluidCollisionMode.NEVER,true)!=null)continue;
                    burst.direct=id;
                    try {
                        double damage=burstDamage(entity.getLocation().distance(center),burst.radius,burst.max,burst.min);
                        if(damage>0 && !dev.vaileks.runeslegendaryv.support.DamageGuard.apply(living,player,damage))burst.denied.add(id);
                    }finally{burst.direct=null;}
                }
                if(burst.denied.contains(id)||living.isDead()||dev.vaileks.runeslegendaryv.support.DamageBridge.justResurrected(living))continue;
            }
            Vector direction = entity.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (direction.lengthSquared() < 1.0E-4) direction = new Vector(0, 0, 1);
            entity.setVelocity(direction.normalize().multiply(strength).setY(vertical));
        }
    }

    private int findTotemSlot(Player player) {
        int main = player.getInventory().getHeldItemSlot();
        if (getTotems(player.getInventory().getItem(main)) > 0) return main;
        return getTotems(player.getInventory().getItem(40)) > 0 ? 40 : -1;
    }

    private String ensureStorageId(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(STORAGE_ID, PersistentDataType.STRING);
        if (id == null) {
            id = UUID.randomUUID().toString();
            meta.getPersistentDataContainer().set(STORAGE_ID, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return id;
    }

    private boolean sameStorage(ItemStack item, String id) {
        return isItem(item) && item.getAmount() == 1 && id.equals(item.getItemMeta().getPersistentDataContainer().get(STORAGE_ID, PersistentDataType.STRING));
    }

    public ItemStack menuItem(Player player, TotemMenu menu) {
        if (!player.getUniqueId().equals(menu.owner()) || menu.sourceSlot() < 0) return null;
        ItemStack item = player.getInventory().getItem(menu.sourceSlot());
        return sameStorage(item, menu.storageId()) ? item : null;
    }

    /** Explicitly replace the actual inventory slot: Bukkit may return a detached stack. */
    public boolean storeMenuCount(Player player, TotemMenu menu, int expected, int count) {
        ItemStack item = menuItem(player, menu);
        if (item == null || getTotems(item) != expected || count < 0 || count > MAX_TOTEMS) return false;
        setTotems(item, count);
        player.getInventory().setItem(menu.sourceSlot(), item);
        menu.refresh(storedTotems(item));
        return true;
    }

    public boolean storeMenuTotems(Player player, TotemMenu menu, int expected, java.util.List<ItemStack> items) {
        ItemStack glove = menuItem(player, menu);
        if (glove == null || getTotems(glove) != expected || items.size() > MAX_TOTEMS) return false;
        setStoredTotems(glove, items);
        player.getInventory().setItem(menu.sourceSlot(), glove);
        menu.refresh(items); return true;
    }

    public void playTotemMenuSound(Player player, boolean insert) {
        String section = insert ? "totem_insert" : "totem_remove";
        String legacyKey = insert ? "insert_sound" : "remove_sound";
        String fallback = insert ? "minecraft:block.beacon.power_select" : "minecraft:block.respawn_anchor.deplete";
        Cfg cfg = Cfg.child(this.config, section);
        SoundUtil.play(player, cfg.string("sound", this.totemMenu().string(legacyKey, fallback)),
                cfg.decimal("sound_volume", 1.0f), cfg.decimal("sound_pitch", 1.0f));
    }

    public void openTotemMenu(Player player, ItemStack itemStack) {
        if (!this.totemMenu().flag("enabled", true)) {
            Debug.log("totem menu disabled by config");
        } else if (!this.isItem(itemStack)) {
            Debug.log("totem menu: item is not gauntlet");
        } else {
            int slot = player.getInventory().getHeldItemSlot();
            if (!isItem(player.getInventory().getItem(slot))) slot = 40;
            ItemStack actual = player.getInventory().getItem(slot);
            if (!isItem(actual) || actual.getAmount() != 1) return;
            String id = ensureStorageId(actual);
            player.getInventory().setItem(slot, actual);
            TotemMenu menu = new TotemMenu(this.totemMenu(), actual, getTotems(actual), MAX_TOTEMS, player.getUniqueId(), slot, id);
            menu.refresh(storedTotems(actual));
            player.openInventory(menu.getInventory());
        }
    }

    public void clear(Player player) {
        this.lastShieldSound.remove(player.getUniqueId());
        this.immunities.remove(player.getUniqueId());
        this.hitProtectionUntil.remove(player.getUniqueId());
        this.clearStats(player);
    }

    public void clearAll() {
        this.lastShieldSound.clear();
        this.immunities.clear();
        this.hitProtectionUntil.clear();
        if(active==this)active=null;
        this.resurrections.clear();
        this.bursting.clear();
    }

    public void tick() {
        long l = System.currentTimeMillis();
        this.immunities.entrySet().removeIf(entry -> ((Immunity)entry.getValue()).until() < l);
        this.hitProtectionUntil.entrySet().removeIf(entry -> entry.getValue()<=l);
        for (UUID uUID : this.immunities.keySet()) {
            Player player = Bukkit.getPlayer((UUID)uUID);
            if (player == null || !player.isOnline() || player.isDead()) continue;
            Immunity immunity = this.immunities.get(uUID);
            if (l - immunity.lastAmbient >= 1000) {
                immunity.lastAmbient = l;
                Cfg cfg = ability();
                SoundUtil.play(player.getLocation(), cfg.string("immunity_ambient_sound", "minecraft:block.beacon.ambient"),
                    cfg.decimal("immunity_ambient_sound_volume", 5), cfg.decimal("immunity_ambient_sound_pitch", 1));
            }
            Location location = player.getLocation().add(0.0, 1.0, 0.0);
            double d = (double)l / 300.0 % (Math.PI * 2);
            for (int i = 0; i < 16; ++i) {
                double d2 = d + Math.PI * 2 * (double)i / 16.0;
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location.clone().add(Math.cos(d2) * 0.6, 0.1 * Math.sin(d2 * 2.0), Math.sin(d2) * 0.6), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static final class Immunity {
        private final long until;
        private int hitsLeft;
        private long lastHit;
        private long lastAmbient = System.currentTimeMillis();

        private Immunity(long l, int n, long l2) {
            this.until = l;
            this.hitsLeft = n;
            this.lastHit = l2;
        }

        long until() {
            return this.until;
        }

        int hitsLeft() {
            return this.hitsLeft;
        }

        void hitsLeft(int n) {
            this.hitsLeft = n;
        }

        long lastHit() {
            return this.lastHit;
        }

        void lastHit(long l) {
            this.lastHit = l;
        }
    }
}
