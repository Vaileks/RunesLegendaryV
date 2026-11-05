package dev.vaileks.runeslegendaryv.weapon.judge;

import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** A prepared strike belongs to one item and expires without starting an ability cooldown. */
public final class JudgeSwordShuffleState {
    private static final NamespacedKey ID=new NamespacedKey("runeslegendaryv","judge_sword_id");
    private record Prepared(String item,long until){}
    private final Map<UUID,Prepared> prepared=new HashMap<>();
    public void prepare(Player player){prepare(player,player.getInventory().getItemInMainHand(),200);}
    public void prepare(Player player,ItemStack item,int ticks){
        var meta=item.getItemMeta();String id=meta.getPersistentDataContainer().get(ID,PersistentDataType.STRING);
        if(id==null){id=UUID.randomUUID().toString();meta.getPersistentDataContainer().set(ID,PersistentDataType.STRING,id);item.setItemMeta(meta);}
        prepared.put(player.getUniqueId(),new Prepared(id,System.currentTimeMillis()+Math.max(1,ticks)*50L));
    }
    public boolean isPrepared(Player player,ItemStack item){
        Prepared state=prepared.get(player.getUniqueId());if(state==null)return false;
        if(state.until<=System.currentTimeMillis()){prepared.remove(player.getUniqueId());return false;}
        return item!=null&&item.hasItemMeta()&&state.item.equals(item.getItemMeta().getPersistentDataContainer().get(ID,PersistentDataType.STRING));
    }
    public boolean consume(Player player){
        if(!isPrepared(player,player.getInventory().getItemInMainHand()))return false;
        prepared.remove(player.getUniqueId());return true;
    }
    public void clear(Player player){prepared.remove(player.getUniqueId());}
}
