package test;
import java.lang.reflect.*;import java.util.*;import org.bukkit.*;import org.bukkit.entity.*;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.*;import org.bukkit.persistence.*;import org.bukkit.attribute.*;import org.bukkit.event.entity.*;import org.bukkit.event.player.*;import org.bukkit.plugin.java.JavaPlugin;
import dev.vaileks.runeslegendaryv.RunesLegendaryV;import dev.vaileks.runeslegendaryv.weapon.*;import dev.vaileks.runeslegendaryv.weapon.mechzhizni.*;import dev.vaileks.runeslegendaryv.weapon.judge.*;import dev.vaileks.runeslegendaryv.support.*;

public final class StandaloneSmoke extends JavaPlugin {
 int checks;void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();getLogger().info("STANDALONE_PASS checks="+checks);}catch(Throwable e){getLogger().severe("STANDALONE_FAIL "+e);e.printStackTrace();}finally{Bukkit.shutdown();}},30);}
 static Object zero(Class<?> t){if(!t.isPrimitive())return null;if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;if(t==float.class)return 0f;if(t==double.class)return 0d;return null;}
 @SuppressWarnings("unchecked")static <T>T proxy(Class<T> type,InvocationHandler h){return(T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},h);}
 static final class Avatar {
  final UUID id=UUID.randomUUID();final ItemStack[] slots=new ItemStack[41];final ItemStack data=new ItemStack(Material.PAPER);final PersistentDataContainer pdc=data.getItemMeta().getPersistentDataContainer();final PlayerInventory inv;final Player player;final Location location;boolean online=true;final Map<Object,Integer> cooldowns=new HashMap<>();
  Avatar(Location at){location=at;inv=proxy(PlayerInventory.class,(o,m,a)->switch(m.getName()){
   case "getItemInMainHand"->copy(slots[0]);case "getItemInOffHand"->copy(slots[40]);case "getItem"->copy(slots[(int)a[0]]);
   case "setItem"->{slots[(int)a[0]]=copy((ItemStack)a[1]);yield null;}case "setItemInMainHand"->{slots[0]=copy((ItemStack)a[0]);yield null;}case "setItemInOffHand"->{slots[40]=copy((ItemStack)a[0]);yield null;}
   case "getContents"->Arrays.stream(slots).map(Avatar::copy).toArray(ItemStack[]::new);case "getStorageContents"->Arrays.stream(Arrays.copyOf(slots,36)).map(Avatar::copy).toArray(ItemStack[]::new);case "setStorageContents"->{ItemStack[] input=(ItemStack[])a[0];for(int i=0;i<36;i++)slots[i]=copy(input[i]);yield null;}
   case "getArmorContents"->new ItemStack[4];case "getHeldItemSlot"->0;case "getSize"->41;case "getMaxStackSize"->64;default->zero(m.getReturnType());});
   player=proxy(Player.class,(o,m,a)->switch(m.getName()){
    case "getUniqueId"->id;case "getName"->"Fixture";case "isOnline","isValid"->online;case "isDead"->false;case "getGameMode"->GameMode.SURVIVAL;
    case "getInventory"->inv;case "getLocation"->location.clone();case "getEyeLocation"->location.clone().add(0,1.62,0);case "getWorld"->location.getWorld();case "getPersistentDataContainer"->pdc;
    case "getAttackCooldown"->1f;case "getHeight"->1.8;case "getHealth"->20d;case "getAbsorptionAmount"->0d;case "getVelocity"->new org.bukkit.util.Vector();
    case "getScoreboard"->Bukkit.getScoreboardManager().getMainScoreboard();
    case "setCooldown"->{cooldowns.put(a[0],(int)a[1]);yield null;}case "getCooldown"->cooldowns.getOrDefault(a[0],0);case "hasCooldown"->cooldowns.getOrDefault(a[0],0)>0;
    case "hashCode"->id.hashCode();case "equals"->o==a[0];default->zero(m.getReturnType());});
  }
  static ItemStack copy(ItemStack item){return item==null?new ItemStack(Material.AIR):item.clone();}
 }
 void run()throws Exception{
  var plugin=(RunesLegendaryV)Bukkit.getPluginManager().getPlugin("RunesLegendaryV");check(plugin!=null&&plugin.isEnabled(),"plugin enabled without any other gameplay plugin");
  check(plugin.getDescription().getDepend().isEmpty()&&plugin.getDescription().getSoftDepend().isEmpty(),"zero plugin dependencies");check(plugin.getDescription().getAuthors().equals(List.of("Vaileks")),"author");check(Bukkit.getPluginManager().getPlugins().length==2,"only standalone and test helper installed");
  check(plugin.weapons().size()==4,"exactly four legendaries");Set<String> ids=new HashSet<>();
  for(var entry:plugin.weapons().entrySet()){
   ItemStack item=entry.getValue().getProcessedItem(),other=entry.getValue().getProcessedItem();var meta=item.getItemMeta();
   check(entry.getValue().isItem(item),"own identity "+entry.getKey());check(meta.isUnbreakable(),"unbreakable "+entry.getKey());check(meta.hasItemModel(),"model "+entry.getKey());check(meta.getTooltipStyle()!=null,"tooltip "+entry.getKey());check(meta.hasLore()&&meta.lore().size()>5,"lore "+entry.getKey());
   String id=meta.getPersistentDataContainer().get(AbstractLegendaryWeapon.ID,PersistentDataType.STRING);check(ids.add(id)&&!id.equals(other.getItemMeta().getPersistentDataContainer().get(AbstractLegendaryWeapon.ID,PersistentDataType.STRING)),"per-issued-item identity");
   for(var different:plugin.weapons().values())if(different!=entry.getValue())check(!different.isItem(item),"cross-weapon isolation");
   check(!meta.getPersistentDataContainer().getKeys().stream().anyMatch(k->k.getNamespace().equals("fullleg")||k.getNamespace().equals("commonbloodlib")),"no old plugin data dependency");
  }
  ItemStack glove=plugin.gauntlet().getItem();var health=glove.getItemMeta().getAttributeModifiers(Attribute.MAX_HEALTH);check(health!=null&&health.stream().anyMatch(a->a.getAmount()==4&&a.getSlotGroup()==EquipmentSlotGroup.OFFHAND),"two hearts only in offhand");
  ItemStack totem=new ItemStack(Material.TOTEM_OF_UNDYING);var tm=totem.getItemMeta();tm.setItemModel(NamespacedKey.fromString("fixture:custom_totem"));tm.setCustomModelData(431);tm.addAttributeModifier(Attribute.MAX_HEALTH,new AttributeModifier(new NamespacedKey("fixture","health"),2,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.OFFHAND));totem.setItemMeta(tm);
  plugin.gauntlet().setStoredTotems(glove,List.of(totem));ItemStack saved=ItemStack.deserializeBytes(glove.serializeAsBytes());check(plugin.gauntlet().storedTotems(saved).getFirst().isSimilar(totem),"stored totem retains model, CMD and attributes across native serialization");check(plugin.gauntlet().getTotems(saved)==1,"totem count retained");
  plugin.gauntlet().setTotems(saved,0);check(plugin.gauntlet().getTotems(saved)==0,"withdraw storage count");
  var charges=new TargetCharge();UUID first=UUID.randomUUID(),second=UUID.randomUUID();charges.select(first);charges.record(1000,40000,.075);check(charges.bonus(1001)==.075&&!charges.ready(1001,500),"full-hit charge and click interval");charges.select(second);check(charges.bonus(1001)==0&&charges.ready(1001,500),"damage charge cannot transfer victims");
  check(plugin.itemConfig("relsatron").getInt("charge.segments")==2,"rail two display segments");check(plugin.itemConfig("perchatkastoikosti").getDouble("bash.damage")==8,"gauntlet four hearts true damage");
  var world=Bukkit.getWorlds().getFirst();Location at=world.getSpawnLocation().clone().add(0,5,0);at.getChunk().load();var a=new Avatar(at);var b=new Avatar(at.clone().add(2,0,0));a.slots[0]=plugin.swordOfNature().getItem();
  plugin.swordOfNature().arm(a.player,a.inv.getItemInMainHand());check(plugin.swordOfNature().isArmed(a.player),"life sword armed by RMB");plugin.swordOfNature().onHit(a.player,b.player);check(plugin.swordOfNature().isFrozen(b.player),"first valid hit binds victim");
  long displays=world.getEntitiesByClass(ItemDisplay.class).size();check(displays>=3,"animated multipart vine spawned without model plugins");plugin.swordOfNature().clear(b.player);check(!plugin.swordOfNature().isFrozen(b.player),"root cleanup");
  var cd=new Cooldowner("fixture");ItemStack item=plugin.judgeSword().getItem();cd.setCooldownWithoutIndicator(a.player,item,40);check(cd.remaining(a.player,item)>1500,"persistent player/item cooldown");ItemStack roundtrip=ItemStack.deserializeBytes(item.serializeAsBytes());check(cd.ticksUntilFinish(roundtrip)>0,"cooldown persists across item serialization");cd.clear(a.player,item);check(cd.remaining(a.player,item)==0,"cooldown reset");
  a.slots[0]=plugin.judgeSword().getItem();cd.setCooldownWithoutIndicator(a.player,a.slots[0],100);plugin.resetCooldowns(a.player);check(cd.remaining(a.player,a.inv.getItemInMainHand())==0,"admin reset writes cooldown cleanup back to inventory copies");
  for(int i=0;i<36;i++)a.slots[i]=null;a.slots[0]=new ItemStack(Material.DIAMOND,3);a.slots[4]=new ItemStack(Material.EMERALD,2);var before=Map.of(Material.DIAMOND,3,Material.EMERALD,2);
  for(int i=0;i<40;i++){check(JudgeSwordShuffleStrikeListener.shuffleInventory(a.inv),"judge shuffle");var counted=new EnumMap<Material,Integer>(Material.class);for(var stack:a.inv.getStorageContents())if(stack!=null&&!stack.getType().isAir())counted.merge(stack.getType(),stack.getAmount(),Integer::sum);check(counted.equals(before),"shuffle conserves items");}
  var zombie=world.spawn(at.clone().add(5,0,0),Zombie.class,z->{z.setAI(false);z.setSilent(true);});zombie.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(4);zombie.setAbsorptionAmount(4);DamageGuard.apply(zombie,null,6);check(zombie.getAbsorptionAmount()==0&&Math.abs(zombie.getHealth()-18)<.001,"pure damage consumes golden hearts first");zombie.remove();
  var savedMob=world.spawn(at.clone().add(6,0,0),Zombie.class,z->{z.setAI(false);z.setSilent(true);z.getEquipment().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));});DamageGuard.apply(savedMob,null,1000);check(!savedMob.isDead()&&savedMob.getHealth()>0,"native totem survives lethal pure damage");savedMob.remove();
  a.online=false;b.online=false;plugin.swordOfNature().clear(a.player);
 }
}
