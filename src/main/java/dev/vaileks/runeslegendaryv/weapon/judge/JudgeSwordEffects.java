package dev.vaileks.runeslegendaryv.weapon.judge;

import dev.vaileks.runeslegendaryv.support.Presets;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import dev.vaileks.runeslegendaryv.util.SoundUtil;

/** Cosmetic-only verdict glyphs; display entities are removed by lifetime or plugin shutdown. */
public final class JudgeSwordEffects {
    private static final java.util.Set<BlockDisplay> active=new java.util.HashSet<>();
    private JudgeSwordEffects(){}
    public static void clear(){active.forEach(Entity::remove);active.clear();}
    private static FileConfiguration config(){return Presets.config("CustomSpeedItems","JudgeSword");}
    public static int integer(String key,int fallback,int min,int max){return Math.max(min,Math.min(max,config().getInt(key,fallback)));}
    public static void prepared(Player player){
        SoundUtil.play(player,config().getString("sounds.prepare","minecraft:block.enchantment_table.use"),.7f,.7f);
        ring(player.getLocation(),.65,Particle.ENCHANT,20);
    }
    private static void ring(Location center,double radius,Particle particle,int amount){
        for(int i=0;i<amount;i++){
            double angle=i*Math.PI*2/amount;
            center.getWorld().spawnParticle(particle,center.clone().add(Math.cos(angle)*radius,.1,Math.sin(angle)*radius),1,0,0,0,0);
        }
    }
    public static void strike(Player target){
        Location center=target.getLocation().clone();
        SoundUtil.play(center,config().getString("sounds.shuffle","minecraft:block.anvil.land"),.85f,1.3f);
        ring(center,1.2,Particle.SOUL_FIRE_FLAME,32);
        center.getWorld().spawnParticle(Particle.ENCHANT,center.clone().add(0,1,0),30,.5,.8,.5,.25);
        if(!config().getBoolean("visuals.block-displays",true))return;
        Plugin plugin=Bukkit.getPluginManager().getPlugin("RunesLegendaryV");if(plugin==null||!plugin.isEnabled())return;
        int duration=integer("visuals.duration-ticks",16,4,40);
        java.util.List<BlockDisplay> parts=new java.util.ArrayList<>();
        try{
            for(int i=0;i<6;i++){
                double angle=i*Math.PI/3;
                parts.add(center.getWorld().spawn(center,BlockDisplay.class,d->{
                    d.setBlock(Material.CRYING_OBSIDIAN.createBlockData());d.setPersistent(false);d.setInvulnerable(true);d.setGravity(false);
                    d.setBrightness(new Display.Brightness(15,15));d.setInterpolationDuration(duration);d.setDisplayWidth(4);d.setDisplayHeight(4);
                    d.setTransformation(new Transformation(new Vector3f((float)Math.cos(angle)*.9f,1.4f,(float)Math.sin(angle)*.9f),
                            new Quaternionf().rotateY((float)-angle),new Vector3f(.14f,.55f,.14f),new Quaternionf()));
                }));
            }
            active.addAll(parts);
            Bukkit.getScheduler().runTaskLater(plugin,()->{parts.forEach(Entity::remove);active.removeAll(parts);},duration);
        }catch(RuntimeException error){parts.forEach(Entity::remove);active.removeAll(parts);plugin.getLogger().warning("Cannot render Judge Sword verdict: "+error.getMessage());}
    }
}
