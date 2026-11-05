package dev.vaileks.runeslegendaryv.support;
import java.util.*;import org.bukkit.*;import org.bukkit.entity.*;import dev.vaileks.runeslegendaryv.weapon.perchatkastoikosti.Gauntlet;
public final class DamageBridge {
 private static final Map<UUID,Long> resurrected=new HashMap<>();
 public static boolean justResurrected(LivingEntity target){Long tick=resurrected.get(target.getUniqueId());if(tick==null)return false;if(Bukkit.getCurrentTick()-tick<=1)return true;resurrected.remove(target.getUniqueId());return false;}
 public static void resurrected(LivingEntity target){resurrected.put(target.getUniqueId(),(long)Bukkit.getCurrentTick());}
 public static boolean intercept(Player owner,Player target,double amount){return justResurrected(target)||Gauntlet.protectPureDamage(target,amount);}
 public static void clear(){resurrected.clear();}
}
