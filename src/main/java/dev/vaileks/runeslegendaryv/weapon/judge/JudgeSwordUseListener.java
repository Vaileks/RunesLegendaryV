package dev.vaileks.runeslegendaryv.weapon.judge;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.*;
import dev.vaileks.runeslegendaryv.support.Feedback;

public final class JudgeSwordUseListener implements Listener {
    private final JudgeSword sword;private final JudgeSwordShuffleState state;
    public JudgeSwordUseListener(JudgeSword sword,JudgeSwordShuffleState state){this.sword=sword;this.state=state;}
    private boolean arm(Player player){
        ItemStack item=player.getInventory().getItemInMainHand();
        if(!player.isSneaking()||!sword.isItem(item))return false;
        if(Feedback.hasCooldown(sword.getShuffleCooldowner(),player,item))return true;
        if(state.isPrepared(player,item))return true;
        state.prepare(player,item,JudgeSwordEffects.integer("preparedWindowTicks",200,1,1200));
        player.getInventory().setItemInMainHand(item);
        JudgeSwordEffects.prepared(player);return true;
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event){
        if(event.getHand()==EquipmentSlot.HAND&&event.getAction().isRightClick()&&arm(event.getPlayer()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onEntityUse(PlayerInteractEntityEvent event){if(event.getHand()==EquipmentSlot.HAND&&arm(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onEntityUseAt(PlayerInteractAtEntityEvent event){onEntityUse(event);}
    @EventHandler public void onQuit(PlayerQuitEvent event){state.clear(event.getPlayer());}
    @EventHandler public void onDeath(PlayerDeathEvent event){state.clear(event.getEntity());}
    @EventHandler public void onWorld(PlayerChangedWorldEvent event){state.clear(event.getPlayer());}
}
