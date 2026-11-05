package test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import dev.vaileks.runeslegendaryv.RunesLegendaryV;
import dev.vaileks.runeslegendaryv.config.Language;
import dev.vaileks.runeslegendaryv.support.*;
import dev.vaileks.runeslegendaryv.weapon.AbstractLegendaryWeapon;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class LocalizationAudit {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 private static String plain(net.kyori.adventure.text.Component value){return PlainTextComponentSerializer.plainText().serialize(value);}
 private static Set<String> placeholders(String value){Set<String> result=new HashSet<>();var matcher=Pattern.compile("\\{[a-z]+}|%[a-z]+%").matcher(value);while(matcher.find())result.add(matcher.group());return result;}
 private static YamlConfiguration bundle(RunesLegendaryV plugin,String language){return YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource("lang/"+language+".yml"),StandardCharsets.UTF_8));}
 public static void run(RunesLegendaryV plugin)throws Exception{
  var ru=bundle(plugin,"ru");var en=bundle(plugin,"en");check(ru.getKeys(true).equals(en.getKeys(true)),"identical language key sets");
  for(String key:ru.getKeys(true))if(!ru.isConfigurationSection(key)){
   String translated=en.get(key).toString();check(!Pattern.compile("[А-Яа-яЁё]").matcher(translated).find(),"no untranslated Russian in English: "+key);check(placeholders(ru.get(key).toString()).equals(placeholders(translated)),"matching placeholders: "+key);
  }
  check(plugin.language().code().equals("ru"),"Russian default preserved");check(Language.text("tooltip.mainhand").contains("руке"),"Russian tooltip labels");
  var world=Bukkit.getWorlds().getFirst();var avatar=new StandaloneSmoke.Avatar(world.getSpawnLocation().clone().add(0,5,0));
  var glove=plugin.gauntlet().getItem();var totem=new ItemStack(Material.TOTEM_OF_UNDYING);var meta=totem.getItemMeta();meta.setItemModel(NamespacedKey.fromString("test:custom_totem"));totem.setItemMeta(meta);plugin.gauntlet().setStoredTotems(glove,List.of(totem));var cd=new Cooldowner("locale_test");cd.setCooldownWithoutIndicator(null,glove,500);avatar.slots[0]=glove;
  String id=glove.getItemMeta().getPersistentDataContainer().get(AbstractLegendaryWeapon.ID,PersistentDataType.STRING);byte[] totemData=totem.serializeAsBytes();
  double bash=plugin.itemConfig("perchatkastoikosti").getDouble("bash.damage");double root=plugin.itemConfig("mechzhizni").getDouble("ability.root_duration");
  // Mimic an existing installation from before language support.
  File oldItem=new File(plugin.getDataFolder(),"items/mechzhizni.yml");var old=YamlConfiguration.loadConfiguration(oldItem);old.set("name",ru.getString("items.mechzhizni.name"));old.set("lore-description",ru.getList("items.mechzhizni.lore-description"));old.save(oldItem);
  // A removed key in a custom language file must fall back to bundled English.
  File english=new File(plugin.getDataFolder(),"lang/en.yml");var incomplete=YamlConfiguration.loadConfiguration(english);incomplete.set("messages.ready",null);incomplete.save(english);
  plugin.getConfig().set("language","en");plugin.saveConfig();plugin.onCommand(Bukkit.getConsoleSender(),plugin.getCommand("rlv"),"rlv",new String[]{"reload"});
  check(plugin.language().code().equals("en"),"English selected on actual reload");check(Language.text("messages.ready").equals("&aAbility ready: "),"missing key uses bundled English");
  check(plugin.itemConfig("mechzhizni").getString("name").equals("&6Sword of Life"),"legacy default Russian name migrated");check(!YamlConfiguration.loadConfiguration(oldItem).isSet("name"),"old default removed from mechanics file");
  for(var weapon:plugin.weapons().values()){
   ItemStack item=weapon.getProcessedItem();String name=plain(item.getItemMeta().displayName());check(!Pattern.compile("[А-Яа-яЁё]").matcher(name).find(),"English name: "+weapon.getName());String lore=item.getItemMeta().lore().stream().map(LocalizationAudit::plain).reduce("",(a,b)->a+" "+b);check(lore.contains("Attack Damage")&&lore.contains("Attack Speed")&&!Pattern.compile("[А-Яа-яЁё]").matcher(lore).find(),"fully English lore: "+weapon.getName());
  }
  plugin.localizeInventory(avatar.inv);ItemStack translated=avatar.inv.getItemInMainHand();check(plain(translated.getItemMeta().displayName()).equals("Gauntlet of Fortitude"),"already issued item translated");check(id.equals(translated.getItemMeta().getPersistentDataContainer().get(AbstractLegendaryWeapon.ID,PersistentDataType.STRING)),"identity survives translation");check(Arrays.equals(totemData,plugin.gauntlet().storedTotems(translated).getFirst().serializeAsBytes()),"stored custom totem unchanged");check(cd.ticksUntilFinish(translated)>400,"cooldown retained");check(glove.getItemMeta().getAttributeModifiers().equals(translated.getItemMeta().getAttributeModifiers()),"all attributes unchanged");check(glove.getItemMeta().getItemModel().equals(translated.getItemMeta().getItemModel()),"model unchanged");
  check(plugin.itemConfig("perchatkastoikosti").getDouble("bash.damage")==bash&&plugin.itemConfig("mechzhizni").getDouble("ability.root_duration")==root,"mechanics unchanged");
  Feedback.waiting(avatar.player,65000);check(plain(avatar.messages.getLast()).equals("Ability on cooldown (1:05)"),"English cooldown message with time");
  avatar.slots[0]=plugin.railGun().getItem();plugin.railGun().beginCharge(avatar.player);check(plain(avatar.actionBars.getLast()).equals("No ammunition: ANVIL"),"English railgun action bar");
  File judgeFile=new File(plugin.getDataFolder(),"items/JudgeSword.yml");var custom=YamlConfiguration.loadConfiguration(judgeFile);custom.set("name","&6Custom Judge");custom.save(judgeFile);
  plugin.getConfig().set("language","ru");plugin.saveConfig();plugin.onCommand(Bukkit.getConsoleSender(),plugin.getCommand("rlv"),"rlv",new String[]{"reload"});check(plain(plugin.swordOfNature().getItem().getItemMeta().displayName()).equals("Меч жизни"),"switching back to Russian works");check(plugin.itemConfig("JudgeSword").getString("name").equals("&6Custom Judge"),"custom item override preserved");
  custom.set("name",null);custom.save(judgeFile);avatar.online=false;
  System.out.println("LOCALIZATION_PASS checks="+checks);
 }
}
