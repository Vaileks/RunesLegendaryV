package dev.vaileks.runeslegendaryv;

import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
import org.bukkit.*;import org.bukkit.command.*;import org.bukkit.configuration.file.*;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.inventory.InventoryClickEvent;import org.bukkit.inventory.*;import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import dev.vaileks.runeslegendaryv.config.Settings;import dev.vaileks.runeslegendaryv.util.*;import dev.vaileks.runeslegendaryv.support.*;import dev.vaileks.runeslegendaryv.weapon.*;import dev.vaileks.runeslegendaryv.weapon.mechzhizni.SwordOfNature;import dev.vaileks.runeslegendaryv.weapon.perchatkastoikosti.Gauntlet;import dev.vaileks.runeslegendaryv.weapon.relsatron.RailGun;import dev.vaileks.runeslegendaryv.weapon.judge.JudgeSword;import dev.vaileks.runeslegendaryv.listener.*;

public final class RunesLegendaryV extends JavaPlugin implements Listener,TabExecutor {
 private static RunesLegendaryV instance;private Settings settings;private ULegendaryContext context;private SwordOfNature nature;private Gauntlet glove;private RailGun rail;private JudgeSword judge;
 private final Map<String,YamlConfiguration> configs=new LinkedHashMap<>();private final Map<String,AbstractLegendaryWeapon> weapons=new LinkedHashMap<>();
 public static RunesLegendaryV instance(){return instance;}public Settings settings(){return settings;}public SwordOfNature swordOfNature(){return nature;}public Gauntlet gauntlet(){return glove;}public RailGun railGun(){return rail;}public JudgeSword judgeSword(){return judge;}
 public FileConfiguration itemConfig(String key){return configs.get(key);}public Map<String,AbstractLegendaryWeapon> weapons(){return Collections.unmodifiableMap(weapons);}
 @Override public void onEnable(){instance=this;saveDefaultConfig();initialize();getCommand("runeslegendaryv").setExecutor(this);getCommand("runeslegendaryv").setTabCompleter(this);getLogger().info("Four legendary items loaded. No external plugin dependencies.");}
 private void initialize(){
  configs.clear();weapons.clear();reloadConfig();var combined=new YamlConfiguration();for(String key:getConfig().getKeys(false))combined.set(key,getConfig().get(key));
  for(String key:List.of("mechzhizni","JudgeSword","relsatron","perchatkastoikosti")){
   String path="items/"+key+".yml";File file=new File(getDataFolder(),path);if(!file.exists())saveResource(path,false);
   var yaml=new YamlConfiguration();try{yaml.load(file);}catch(Exception error){throw new IllegalStateException("Cannot load "+file,error);}
   try(var resource=getResource(path)){if(resource==null)throw new IOException("Missing bundled defaults: "+path);yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(resource,StandardCharsets.UTF_8)));}catch(IOException e){throw new IllegalStateException(e);}
   configs.put(key,yaml);combined.set(key,yaml);
  }
  settings=new Settings(combined);Debug.bind(getLogger(),getConfig().getBoolean("debug"));context=new ULegendaryContext(this,settings,new Keys(this),new Cooldowns(),new AbilityCooldown(),new Tasks(this));
  nature=new SwordOfNature(context);glove=new Gauntlet(context);rail=new RailGun(context);judge=new JudgeSword(context);
  for(var weapon:List.of(nature,judge,rail,glove))weapons.put(weapon.getName(),weapon);
  for(Listener listener:List.of(this,new CombatListener(this,context),new EquipmentListener(this,context),new PlayerSessionListener(this,context),new TotemMenuListener(glove),new DamageGuard()))getServer().getPluginManager().registerEvents(listener,this);
  for(Listener listener:judge.listeners())getServer().getPluginManager().registerEvents(listener,this);
  nature.startPassiveTasks();rail.startFlightTask();judge.start();context.tasks().globalTimer(2,2,glove::tick);for(Player player:Bukkit.getOnlinePlayers())glove.syncStats(player);
 }
 private void stop(){
  for(Player player:Bukkit.getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder() instanceof dev.vaileks.runeslegendaryv.menu.TotemMenu||player.getOpenInventory().getTopInventory().getHolder() instanceof RelicMenu)player.closeInventory();
  if(nature!=null)nature.clear();if(rail!=null)rail.clearAll();if(glove!=null)glove.clearAll();if(judge!=null)judge.clear();Feedback.clearAll();DeferredCooldowns.clear();DamageBridge.clear();
  Bukkit.getScheduler().cancelTasks(this);
 }
 @Override public void onDisable(){stop();}
 private static String canonical(String value){return switch(value.toLowerCase(Locale.ROOT)){case "life","мечжизни"->"mechzhizni";case "judge","judgesword","судныймеч"->"JudgeSword";case "rail","рельсатрон"->"relsatron";case "glove","перчатка"->"perchatkastoikosti";default->value;};}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(!sender.hasPermission("runeslegendaryv.admin")){sender.sendMessage("Нет прав.");return true;}
  if(args.length==0||args[0].equalsIgnoreCase("menu")){if(sender instanceof Player p)p.openInventory(new RelicMenu().getInventory());else sender.sendMessage("/"+label+" give <игрок> <предмет> [количество]");return true;}
  if(args.length==1&&args[0].equalsIgnoreCase("reload")){stop();HandlerList.unregisterAll((org.bukkit.plugin.Plugin)this);initialize();sender.sendMessage("RunesLegendaryV: настройки обновлены.");return true;}
  if(args.length>=3&&args[0].equalsIgnoreCase("give")){
   Player target=Bukkit.getPlayerExact(args[1]);var weapon=weapons.get(canonical(args[2]));if(target==null||weapon==null){sender.sendMessage("Игрок или предмет не найден.");return true;}
   int amount=1;try{if(args.length>3)amount=Integer.parseInt(args[3]);}catch(NumberFormatException e){sender.sendMessage("Укажи количество от 1 до 64.");return true;}if(amount<1||amount>64){sender.sendMessage("Укажи количество от 1 до 64.");return true;}
   for(int i=0;i<amount;i++)give(target,weapon);sender.sendMessage("Предмет выдан игроку "+target.getName()+".");return true;
  }
  if(args.length==2&&args[0].equalsIgnoreCase("resetcd")){
   Player target=Bukkit.getPlayerExact(args[1]);if(target==null){sender.sendMessage("Игрок не найден.");return true;}
   resetCooldowns(target);
   sender.sendMessage("Перезарядки сброшены.");return true;
  }
  sender.sendMessage("/"+label+" [menu|give <игрок> <предмет> [количество]|reload|resetcd <игрок>]");return true;
 }
 public void resetCooldowns(Player target) {
  context.cooldowns().clearAll(target);
  context.abilityCooldown().clearAll(target);
  Feedback.clear(target);
  for (var key : new HashSet<>(target.getPersistentDataContainer().getKeys()))
   if (cooldownKey(key)) target.getPersistentDataContainer().remove(key);
  var inventory = target.getInventory();
  for (int slot = 0; slot < inventory.getSize(); slot++) {
   ItemStack item = inventory.getItem(slot);
   if (item == null || weapons.values().stream().noneMatch(w -> w.isItem(item))) continue;
   var meta = item.getItemMeta();
   for (var key : new HashSet<>(meta.getPersistentDataContainer().getKeys()))
    if (cooldownKey(key)) meta.getPersistentDataContainer().remove(key);
   item.setItemMeta(meta);
   inventory.setItem(slot, item);
   target.setCooldown(item, 0);
  }
 }
 private static boolean cooldownKey(NamespacedKey key) {
  return key.getNamespace().equals("runeslegendaryv") &&
   (key.getKey().startsWith("cd_") || key.getKey().equals("gauntlet_bash_until"));
 }
 private void give(Player player,AbstractLegendaryWeapon weapon){player.getInventory().addItem(weapon.getProcessedItem()).values().forEach(item->player.getWorld().dropItemNaturally(player.getLocation(),item));glove.syncStats(player);}
 private final class RelicMenu implements InventoryHolder {
  private final Inventory inventory;private final Map<Integer,AbstractLegendaryWeapon> slots=new LinkedHashMap<>();
  RelicMenu(){inventory=Bukkit.createInventory(this,9,Component.text("Легендарные предметы"));int slot=1;for(var weapon:weapons.values()){slots.put(slot,weapon);inventory.setItem(slot,weapon.getProcessedItem());slot+=2;}}
  public Inventory getInventory(){return inventory;}
 }
 @EventHandler(priority=EventPriority.HIGHEST)public void menu(InventoryClickEvent event){if(!(event.getView().getTopInventory().getHolder() instanceof RelicMenu menu))return;event.setCancelled(true);if(event.getWhoClicked() instanceof Player p&&p.hasPermission("runeslegendaryv.admin")){var weapon=menu.slots.get(event.getRawSlot());if(weapon!=null)give(p,weapon);}}
 @EventHandler(priority=EventPriority.HIGHEST)public void drag(org.bukkit.event.inventory.InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof RelicMenu)event.setCancelled(true);}
 @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(!sender.hasPermission("runeslegendaryv.admin"))return List.of();List<String> values=args.length==1?List.of("menu","give","reload","resetcd"):args.length==2&&(args[0].equalsIgnoreCase("give")||args[0].equalsIgnoreCase("resetcd"))?Bukkit.getOnlinePlayers().stream().map(Player::getName).toList():args.length==3&&args[0].equalsIgnoreCase("give")?new ArrayList<>(weapons.keySet()):List.of();String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();}
}
