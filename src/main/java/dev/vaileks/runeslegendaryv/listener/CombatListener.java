package dev.vaileks.runeslegendaryv.listener;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.util.Cooldowns;
import dev.vaileks.runeslegendaryv.util.Debug;
import dev.vaileks.runeslegendaryv.weapon.ULegendaryContext;

public final class CombatListener
implements Listener {
    private final RunesLegendaryV plugin;
    private final ULegendaryContext context;
    private final Cooldowns cooldowns;
    private final Map<UUID, Long> lastInteract = new ConcurrentHashMap<UUID, Long>();

    public CombatListener(RunesLegendaryV runesLegendaryV, ULegendaryContext uLegendaryContext) {
        this.plugin = runesLegendaryV;
        this.context = uLegendaryContext;
        this.cooldowns = uLegendaryContext.cooldowns();
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent playerInteractEvent) {
        ItemStack itemStack;
        Action action = playerInteractEvent.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = playerInteractEvent.getPlayer();
        ItemStack used = playerInteractEvent.getItem();
        if (used != null && this.plugin.gauntlet().isItem(used) && !player.isSneaking()
                && !this.plugin.swordOfNature().isItem(player.getInventory().getItemInMainHand())
                && !this.plugin.gauntlet().canUseShield(player, used, playerInteractEvent.getHand())) {
            playerInteractEvent.setCancelled(true);
            return;
        }
        ItemStack itemStack2 = player.getInventory().getItemInMainHand();
        if (!this.isAnyLegendary(itemStack2) && this.isAnyLegendary(itemStack = player.getInventory().getItemInOffHand())) {
            itemStack2 = itemStack;
        }
        if (itemStack2 == null) {
            return;
        }
        if (Debug.enabled()) {
            Debug.item("interact " + player.getName() + " " + String.valueOf(action) + " hand=" + String.valueOf(playerInteractEvent.getHand()), itemStack2);
            boolean bl = this.plugin.swordOfNature().isItem(itemStack2);
            Debug.log("match nature=" + bl + " gauntlet=" + this.plugin.gauntlet().isItem(itemStack2) + " railgun=" + this.plugin.railGun().isItem(itemStack2));
        }
        if (this.plugin.swordOfNature().isItem(itemStack2)) {
            // The offhand packet can be the only use event with a sword and shield.
            // tryInteract deduplicates events from both hands.
            this.toggleNature(player, itemStack2);
            playerInteractEvent.setCancelled(true);
        } else if (this.plugin.gauntlet().isItem(itemStack2)) {
            if (this.tryInteract(player)) {
                if (player.isSneaking()) {
                    Debug.log("gauntlet menu open " + player.getName());
                    this.plugin.gauntlet().openTotemMenu(player, itemStack2);
                    playerInteractEvent.setCancelled(true);
                } else {
                    this.plugin.gauntlet().onRightClick(player);
                }
            }
        } else if (this.plugin.railGun().isItem(itemStack2)) {
            if (this.tryInteract(player)) {
                Debug.log("railgun charge " + player.getName() + " action=" + String.valueOf(action));
                this.plugin.railGun().beginCharge(player);
            }
        }
    }

    private boolean isAnyLegendary(ItemStack itemStack) {
        return itemStack != null && (this.plugin.swordOfNature().isItem(itemStack) || this.plugin.gauntlet().isItem(itemStack) || this.plugin.railGun().isItem(itemStack));
    }

    private void toggleNature(Player player, ItemStack item) {
        if (this.tryInteract(player) && this.plugin.swordOfNature().toggleMode(player, item)) player.swingMainHand();
    }

    private void interactNatureEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!this.plugin.swordOfNature().isItem(item)) return;
        // Entity, precise-entity and both-hand packets share the same debounce.
        this.toggleNature(event.getPlayer(), item);
        event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onNatureEntityInteract(PlayerInteractEntityEvent event) {
        this.interactNatureEntity(event);
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onNatureEntityInteractAt(PlayerInteractAtEntityEvent event) {
        this.interactNatureEntity(event);
    }

    private boolean tryInteract(Player player) {
        long l = System.currentTimeMillis();
        long l2 = (long)(this.plugin.settings().interactCooldown() * 1000.0f);
        Long l3 = this.lastInteract.get(player.getUniqueId());
        if (l3 != null && l3 + l2 > l) {
            return false;
        }
        this.lastInteract.put(player.getUniqueId(), l);
        return true;
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onRelease(PlayerInteractEvent playerInteractEvent) {
        ItemStack itemStack;
        if ((playerInteractEvent.getAction() == Action.LEFT_CLICK_AIR || playerInteractEvent.getAction() == Action.LEFT_CLICK_BLOCK) && playerInteractEvent.getHand() == EquipmentSlot.HAND && (itemStack = playerInteractEvent.getItem()) != null && this.plugin.railGun().isItem(itemStack)) {
            this.plugin.railGun().cancelCharge(playerInteractEvent.getPlayer());
            playerInteractEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onMove(PlayerMoveEvent playerMoveEvent) {
        if (this.plugin.swordOfNature().isFrozen(playerMoveEvent.getPlayer())) {
            Location location = playerMoveEvent.getFrom();
            Location location2 = playerMoveEvent.getTo();
            if (location2 != null && (location.getX() != location2.getX() || location.getY() != location2.getY() || location.getZ() != location2.getZ())) {
                Location location3 = location.clone();
                location3.setYaw(location2.getYaw());
                location3.setPitch(location2.getPitch());
                playerMoveEvent.setTo(location3);
                Player player = playerMoveEvent.getPlayer();
                player.setFallDistance(0.0f);
                if (player.isSprinting()) {
                    player.setSprinting(false);
                }
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onTeleport(PlayerTeleportEvent playerTeleportEvent) {
        if (this.plugin.swordOfNature().isFrozen(playerTeleportEvent.getPlayer()) && playerTeleportEvent.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            playerTeleportEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onSwordAttackStart(PrePlayerAttackEntityEvent event) {
        if (event.willAttack() && this.plugin.swordOfNature().isItem(event.getPlayer().getInventory().getItemInMainHand())) {
            this.plugin.swordOfNature().captureSwing(event.getPlayer(), event.getAttacked());
        }
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onSwordHit(EntityDamageByEntityEvent entityDamageByEntityEvent) {
        if(dev.vaileks.runeslegendaryv.weapon.perchatkastoikosti.Gauntlet.isTotemBurstDamage(entityDamageByEntityEvent))return;
        Entity entity;
        Player player = this.attacker(entityDamageByEntityEvent);
        if (player != null && (entity = entityDamageByEntityEvent.getEntity()) instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity)entity;
            this.plugin.gauntlet().onAttack(player, livingEntity, entityDamageByEntityEvent);
            if (this.plugin.swordOfNature().isItem(player.getInventory().getItemInMainHand())) {
                this.plugin.swordOfNature().prepareHit(player, livingEntity, entityDamageByEntityEvent);
            }
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onSwordHitConfirmed(EntityDamageByEntityEvent event) {
        this.plugin.gauntlet().confirmTotemBurstDamage(event);
        if(dev.vaileks.runeslegendaryv.weapon.perchatkastoikosti.Gauntlet.isTotemBurstDamage(event))return;
        this.plugin.swordOfNature().finishHit(event);
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onHandSwap(PlayerSwapHandItemsEvent playerSwapHandItemsEvent) {
        this.plugin.gauntlet().onSwap(playerSwapHandItemsEvent.getPlayer());
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent entityDamageEvent) {
        this.plugin.gauntlet().adjustTotemBurstDamage(entityDamageEvent);
        Entity entity = entityDamageEvent.getEntity();
        if (entity instanceof Player) {
            Player player = (Player)entity;
            this.plugin.gauntlet().onDamage(player, entityDamageEvent);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent playerDeathEvent) {
        Player player = playerDeathEvent.getEntity();
        if (this.plugin.railGun().recentlyHit((LivingEntity)player)) {
            World world = player.getWorld();
            Location location = player.getLocation().add(0.0, 1.0, 0.0);
            BlockData blockData = Material.REDSTONE_BLOCK.createBlockData();
            world.spawnParticle(Particle.BLOCK, location, 40, 0.4, 0.5, 0.4, 0.1, (Object)blockData);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onStopUsing(PlayerStopUsingItemEvent playerStopUsingItemEvent) {
        Player player = playerStopUsingItemEvent.getPlayer();
        if (this.plugin.railGun().isItem(playerStopUsingItemEvent.getItem())) {
            this.plugin.railGun().release(player);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onResurrect(EntityResurrectEvent entityResurrectEvent) {
        LivingEntity livingEntity = entityResurrectEvent.getEntity();
        if (livingEntity instanceof Player) {
            Player player = (Player)livingEntity;
            this.plugin.gauntlet().tryResurrect(player, entityResurrectEvent);
            if (!entityResurrectEvent.isCancelled() && this.plugin.railGun().recentlyHit((LivingEntity)player)) {
                player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().add(0.0, 1.0, 0.0), 40, 0.4, 0.5, 0.4, 0.1, (Object)Material.REDSTONE_BLOCK.createBlockData());
            }
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onResurrectConfirmed(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) this.plugin.gauntlet().finishResurrect(player, event);
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onRegain(EntityRegainHealthEvent entityRegainHealthEvent) {
        Entity entity = entityRegainHealthEvent.getEntity();
        if (entity instanceof Player) {
            Player player = (Player)entity;
            if (entityRegainHealthEvent.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED && this.plugin.railGun().isNaturalRegenBlocked(player)) {
                entityRegainHealthEvent.setCancelled(true);
            }
        }
    }

    private Player attacker(EntityDamageByEntityEvent entityDamageByEntityEvent) {
        Projectile projectile;
        ProjectileSource projectileSource;
        Entity entity = entityDamageByEntityEvent.getDamager();
        if (entity instanceof Player) {
            Player player = (Player)entity;
            return player;
        }
        entity = entityDamageByEntityEvent.getDamager();
        if (entity instanceof Projectile && (projectileSource = (projectile = (Projectile)entity).getShooter()) instanceof Player) {
            Player player = (Player)projectileSource;
            return player;
        }
        return null;
    }
}
