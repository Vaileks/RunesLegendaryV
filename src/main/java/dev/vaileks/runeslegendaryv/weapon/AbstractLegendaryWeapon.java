package dev.vaileks.runeslegendaryv.weapon;
import java.util.*;import org.bukkit.*;import org.bukkit.attribute.*;import org.bukkit.inventory.*;import org.bukkit.persistence.PersistentDataType;import dev.vaileks.runeslegendaryv.util.*;import dev.vaileks.runeslegendaryv.support.Presentation;
public abstract class AbstractLegendaryWeapon {
 public static final NamespacedKey TYPE=new NamespacedKey("runeslegendaryv","item"),ID=new NamespacedKey("runeslegendaryv","id");
 protected final ULegendaryContext context;protected final dev.vaileks.runeslegendaryv.config.Settings settings;protected final Keys keys;protected final String key;protected final org.bukkit.configuration.ConfigurationSection config;private ItemStack cachedItem;
 protected AbstractLegendaryWeapon(ULegendaryContext context,String key){this.context=context;this.settings=context.settings();this.keys=context.keys();this.key=key;this.config=settings.section(key);}
 public String getName(){return key;}
 public ItemStack getItem(){if(cachedItem==null)cachedItem=buildItem();ItemStack result=cachedItem.clone();var meta=result.getItemMeta();String id=UUID.randomUUID().toString();meta.getPersistentDataContainer().set(ID,PersistentDataType.STRING,id);var use=meta.getUseCooldown();use.setCooldownSeconds(.05f);use.setCooldownGroup(new NamespacedKey("runeslegendaryv","item_"+id));meta.setUseCooldown(use);result.setItemMeta(meta);return result;}
 public void refreshText(ItemStack item){if(!isItem(item))return;var meta=item.getItemMeta();meta.displayName(ItemUtil.parse(config.getString("name",key)));item.setItemMeta(meta);Presentation.decorate(item,config,key);}
 public ItemStack getProcessedItem(){return getItem();}
 protected List<ItemStack> buildTemplates(){return List.of(getItem());}public void invalidateTemplates(){}
 public boolean isItem(ItemStack item){return item!=null&&item.hasItemMeta()&&key.equals(item.getItemMeta().getPersistentDataContainer().get(TYPE,PersistentDataType.STRING));}
 protected ItemStack buildItem(){
  ItemStack item=ItemUtil.base(ItemUtil.material(config.getString("material","STONE"),Material.STONE),config.getString("name",key),List.of());
  applyItemModel(item,config.getString("item_model"));double damage=config.getDouble("damage"),speed=config.getDouble("attack_speed"),movement=config.getDouble("speed_modifier");
  if(damage!=0)ItemUtil.addAttribute(item,keys.attribute(key+"_damage"),Attribute.ATTACK_DAMAGE,damage-1,EquipmentSlotGroup.MAINHAND);
  if(speed!=0)ItemUtil.addAttribute(item,keys.attribute(key+"_speed"),Attribute.ATTACK_SPEED,speed,EquipmentSlotGroup.MAINHAND);
  if(movement!=0)ItemUtil.addAttribute(item,keys.attribute(key+"_movement"),Attribute.MOVEMENT_SPEED,movement,scalarMovementSpeed()?AttributeModifier.Operation.ADD_SCALAR:AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.MAINHAND);
  var enchants=config.getConfigurationSection("enchants");if(enchants!=null)for(String name:enchants.getKeys(false))ItemUtil.enchant(item,name,enchants.getInt(name));
  var meta=item.getItemMeta();meta.getPersistentDataContainer().set(TYPE,PersistentDataType.STRING,key);meta.setUnbreakable(true);meta.setEnchantmentGlintOverride(!config.getBoolean("hide_glow",true));item.setItemMeta(meta);Presentation.decorate(item,config,key);return item;
 }
 protected void applyItemModel(ItemStack item,String model){ItemUtil.setItemModel(item,model);}protected boolean scalarMovementSpeed(){return false;}
}
