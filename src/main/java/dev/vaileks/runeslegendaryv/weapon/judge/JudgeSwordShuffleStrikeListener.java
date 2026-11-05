package dev.vaileks.runeslegendaryv.weapon.judge;

import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import dev.vaileks.runeslegendaryv.support.DamageBridge;
import dev.vaileks.runeslegendaryv.support.TeamAllies;
import dev.vaileks.runeslegendaryv.support.DamageGuard;
import dev.vaileks.runeslegendaryv.support.Feedback;

public final class JudgeSwordShuffleStrikeListener implements Listener {
    private final JudgeSword sword;private final JudgeSwordShuffleState state;
    public JudgeSwordShuffleStrikeListener(JudgeSword sword,JudgeSwordShuffleState state){this.sword=sword;this.state=state;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onHit(EntityDamageByEntityEvent event){
        if(event.isCancelled()||event.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK
                ||!(event.getDamager() instanceof Player attacker)||!(event.getEntity() instanceof Player target)
                ||attacker==target||target.isDead()||DamageGuard.wasProtected(event)
                ||dev.vaileks.runeslegendaryv.weapon.perchatkastoikosti.Gauntlet.isTotemBurstDamage(event))return;
        double landed=event.getFinalDamage();
        if(event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION))landed-=Math.min(0,event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION));
        ItemStack item=attacker.getInventory().getItemInMainHand();
        if(landed<=0||!sword.isItem(item)||!state.isPrepared(attacker,item)||TeamAllies.areAllies(attacker,target)
                ||DamageBridge.justResurrected(target))return;
        if(Feedback.hasCooldown(sword.getShuffleCooldowner(),attacker,item))return;
        // Shields, domes and totem protection must block control as well as damage.
        if(DamageBridge.intercept(attacker,target,Math.max(1,event.getDamage())))return;
        if(!shuffleInventory(target.getInventory()))return;
        state.consume(attacker);
        int cooldown=JudgeSwordEffects.integer("shuffleCooldownTicks",sword.getShuffleCooldownTicks(),0,72000);
        Feedback.setCooldown(sword.getShuffleCooldowner(),attacker,item,cooldown);
        attacker.getInventory().setItemInMainHand(item);
        JudgeSwordEffects.strike(target);
    }
    /** Permute occupied storage slots only; never touch cursor, armor, offhand or another container. */
    public static boolean shuffleInventory(PlayerInventory inventory){
        ItemStack[] before=inventory.getStorageContents();if(before==null)return false;
        List<Integer> occupied=new ArrayList<>();List<ItemStack> values=new ArrayList<>();
        for(int i=0;i<before.length;i++)if(before[i]!=null&&!before[i].getType().isAir()){
            occupied.add(i);values.add(before[i].clone());
        }
        if(values.size()<2)return false;
        Collections.shuffle(values);
        ItemStack[] after=before.clone();for(int i=0;i<values.size();i++)after[occupied.get(i)]=values.get(i);
        if(Arrays.equals(before,after)){
            int distinct=-1;
            for(int i=1;i<occupied.size();i++)if(!Objects.equals(before[occupied.get(0)],before[occupied.get(i)])){distinct=i;break;}
            if(distinct<0)return false;
            ItemStack tmp=after[occupied.get(0)];after[occupied.get(0)]=after[occupied.get(distinct)];after[occupied.get(distinct)]=tmp;
        }
        inventory.setStorageContents(after);return true;
    }
}
