package dev.vaileks.runeslegendaryv.support;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.inventory.ItemStack;import org.bukkit.persistence.*;
public final class Cooldowner {
 private final NamespacedKey key;public Cooldowner(String name){key=new NamespacedKey("runeslegendaryv","cd_"+name.toLowerCase(java.util.Locale.ROOT));}
 public String key(){return key.toString();}
 public long remaining(Player player,ItemStack item){long end=player==null?0:player.getPersistentDataContainer().getOrDefault(key,PersistentDataType.LONG,0L);if(item!=null&&item.hasItemMeta())end=Math.max(end,item.getItemMeta().getPersistentDataContainer().getOrDefault(key,PersistentDataType.LONG,0L));return Math.max(0,end-System.currentTimeMillis());}
 public int ticksUntilFinish(ItemStack item){return (int)((remaining(null,item)+49)/50);}
 public void setCooldownWithoutIndicator(Player player,ItemStack item,int ticks){long end=System.currentTimeMillis()+Math.max(0,ticks)*50L;if(player!=null)player.getPersistentDataContainer().set(key,PersistentDataType.LONG,end);if(item!=null&&item.hasItemMeta()){var meta=item.getItemMeta();meta.getPersistentDataContainer().set(key,PersistentDataType.LONG,end);item.setItemMeta(meta);}if(player!=null)Feedback.watch(player,key.toString(),ticks*50L,item==null?player.getInventory().getItemInMainHand():item,()->remaining(player,item));}
 public void clear(Player player,ItemStack item){if(player!=null)player.getPersistentDataContainer().remove(key);if(item!=null&&item.hasItemMeta()){var meta=item.getItemMeta();meta.getPersistentDataContainer().remove(key);item.setItemMeta(meta);}}
}
