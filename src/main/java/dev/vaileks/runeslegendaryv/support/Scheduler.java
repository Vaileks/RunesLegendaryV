package dev.vaileks.runeslegendaryv.support;
import org.bukkit.*;import org.bukkit.entity.*;import org.bukkit.plugin.Plugin;import org.bukkit.scheduler.BukkitTask;
public final class Scheduler {
 private final Plugin plugin;public Scheduler(Plugin plugin){this.plugin=plugin;}
 public interface Task{void cancel();}
 public boolean isFolia(){return false;}
 public Task runGlobalLater(long delay,Runnable body){BukkitTask t=Bukkit.getScheduler().runTaskLater(plugin,body,Math.max(1,delay));return t::cancel;}
 public Task runGlobalAtFixedRate(long delay,long period,Runnable body){BukkitTask t=Bukkit.getScheduler().runTaskTimer(plugin,body,Math.max(1,delay),Math.max(1,period));return t::cancel;}
 public Task runInRegionLater(Location loc,long delay,Runnable body){return runGlobalLater(delay,body);}
 public Task runInRegionAtFixedRate(Location loc,long delay,long period,Runnable body){return runGlobalAtFixedRate(delay,period,body);}
 public Task runAtEntityLater(Entity entity,long delay,Runnable retired,Runnable body){return runGlobalLater(delay,()->{if(valid(entity))body.run();else retired.run();});}
 public Task runAtEntityAtFixedRate(Entity entity,long delay,long period,Runnable retired,Runnable body){Task[] task={null};task[0]=runGlobalAtFixedRate(delay,period,()->{if(valid(entity))body.run();else{task[0].cancel();retired.run();}});return task[0];}
 private boolean valid(Entity e){return e instanceof Player player?player.isOnline():e.isValid()&&!e.isDead();}
}
