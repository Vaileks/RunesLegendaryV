package dev.vaileks.runeslegendaryv.support;
import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import org.bukkit.entity.Player;
public final class DeferredCooldowns {
 private static final Map<String,Ticket> busy=new HashMap<>();
 public static boolean busy(Player player,String key){return busy.containsKey(key+":"+player.getUniqueId());}
 public static Ticket begin(Player player,String key,Runnable completion){String id=key+":"+player.getUniqueId();if(busy.containsKey(id))return null;Ticket t=new Ticket(id,completion);busy.put(id,t);return t;}
 public static void clear(){busy.clear();}
 public static final class Ticket {private final String id;private final Runnable completion;private final AtomicBoolean ended=new AtomicBoolean();private Ticket(String id,Runnable completion){this.id=id;this.completion=completion;}public void finish(){if(ended.compareAndSet(false,true)){busy.remove(id,this);completion.run();}}public void abort(){if(ended.compareAndSet(false,true))busy.remove(id,this);}}
}
