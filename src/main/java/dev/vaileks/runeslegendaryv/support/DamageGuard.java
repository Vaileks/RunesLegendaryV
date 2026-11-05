package dev.vaileks.runeslegendaryv.support;
import java.util.*;import org.bukkit.entity.*;import org.bukkit.event.*;import org.bukkit.event.entity.EntityDamageEvent;import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;import dev.vaileks.runeslegendaryv.weapon.perchatkastoikosti.Gauntlet;
public final class DamageGuard implements Listener {
 private static final Set<EntityDamageEvent> protectedEvents=Collections.newSetFromMap(new WeakHashMap<>());private static final Map<UUID,Double> pending=new HashMap<>();
 public static void protect(EntityDamageEvent event){protectedEvents.add(event);event.setCancelled(true);}
 public static boolean wasProtected(EntityDamageEvent event){return protectedEvents.contains(event);}
 public static boolean apply(LivingEntity target,Player owner,double amount){if(target.isDead()||!target.isValid()||amount<=0||DamageBridge.justResurrected(target)||Gauntlet.protectPureDamage(target,amount))return false;pending.put(target.getUniqueId(),amount);target.setNoDamageTicks(0);try{target.damage(amount,owner);}finally{pending.remove(target.getUniqueId());}return !target.isDead();}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void onPure(EntityDamageEvent event){Double amount=pending.get(event.getEntity().getUniqueId());if(amount==null||wasProtected(event))return;event.setDamage(amount);for(var m:new DamageModifier[]{DamageModifier.ARMOR,DamageModifier.RESISTANCE,DamageModifier.MAGIC,DamageModifier.HARD_HAT})if(event.isApplicable(m))event.setDamage(m,0);if(event.isApplicable(DamageModifier.ABSORPTION)&&event.getEntity() instanceof LivingEntity target)event.setDamage(DamageModifier.ABSORPTION,-Math.min(amount,target.getAbsorptionAmount()));}
}
