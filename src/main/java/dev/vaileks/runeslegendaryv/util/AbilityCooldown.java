package dev.vaileks.runeslegendaryv.util;
import java.util.*;import org.bukkit.entity.Player;import org.bukkit.inventory.ItemStack;import dev.vaileks.runeslegendaryv.support.*;
public final class AbilityCooldown {
 private final Map<String,Cooldowner> timers=new HashMap<>();private Cooldowner timer(String key){return timers.computeIfAbsent(key,Cooldowner::new);}
 public boolean ready(Player player,String key,ItemStack item){return !Feedback.hasCooldown(timer(key),player,item);}
 private boolean wait(Player player,String key,ItemStack item){Feedback.waiting(player,timer(key).remaining(null,item));return false;}
 public int remainingTicks(Player player,String key,ItemStack item){return timer(key).ticksUntilFinish(item);}
 public void start(Player player,String key,ItemStack item,double seconds){if(item==null||seconds<=0)return;Cooldowner cd=timer(key);cd.setCooldownWithoutIndicator(null,item,(int)Math.round(seconds*20));
  String id=item.getItemMeta().getPersistentDataContainer().get(dev.vaileks.runeslegendaryv.weapon.AbstractLegendaryWeapon.ID,org.bukkit.persistence.PersistentDataType.STRING);
  if(id!=null)for(int slot=0;slot<player.getInventory().getSize();slot++){ItemStack current=player.getInventory().getItem(slot);if(current!=null&&current.hasItemMeta()&&id.equals(current.getItemMeta().getPersistentDataContainer().get(dev.vaileks.runeslegendaryv.weapon.AbstractLegendaryWeapon.ID,org.bukkit.persistence.PersistentDataType.STRING)))player.getInventory().setItem(slot,item);}
  Feedback.watch(player,"ability:"+key,(long)(seconds*1000),item,()->cd.remaining(null,item));player.setCooldown(item,(int)Math.round(seconds*20));}
 public void clear(Player player,String key,ItemStack item){timer(key).clear(null,item);Feedback.cancel(player,"ability:"+key);if(item!=null)player.setCooldown(item,0);}
 public void clearAll(Player player){for(var cd:timers.values()){cd.clear(player,null);for(var item:player.getInventory().getContents())cd.clear(null,item);}Feedback.clear(player);}
}
