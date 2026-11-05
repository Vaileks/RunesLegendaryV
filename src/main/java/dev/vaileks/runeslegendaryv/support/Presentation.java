package dev.vaileks.runeslegendaryv.support;
import java.util.*;import org.bukkit.*;import org.bukkit.attribute.*;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.ItemMeta;import net.kyori.adventure.text.*;import net.kyori.adventure.text.format.*;
public final class Presentation {
 private static final int WIDTH=38;private static final String SECTION="Когда в ведущей руке:",DAMAGE="Урон при атаке ",SPEED="Скорость атаки ";
 public static void decorate(ItemStack item,org.bukkit.configuration.ConfigurationSection config,String key){var meta=item.getItemMeta();meta.displayName(meta.displayName().font(NamespacedKey.minecraft("default")).decoration(TextDecoration.ITALIC,false));meta.lore(render(config.getStringList("lore-description"),config.getStringList("lore-abilities"),value(item,meta,Attribute.ATTACK_DAMAGE,1),value(item,meta,Attribute.ATTACK_SPEED,4)));String palette=key.equals("mechzhizni")?"earth":key.equals("JudgeSword")?"gold":"shard";meta.setTooltipStyle(NamespacedKey.fromString(config.getString("tooltip-style","minecraft:"+palette)));meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES,ItemFlag.HIDE_ENCHANTS,ItemFlag.HIDE_UNBREAKABLE);item.setItemMeta(meta);}
    public static double value(ItemStack item,ItemMeta meta,Attribute attribute,double base) {
        var attributes=meta.getAttributeModifiers();
        Collection<AttributeModifier> modifiers=attributes==null?item.getType().getDefaultAttributeModifiers(EquipmentSlot.HAND).get(attribute):attributes.get(attribute);
        double add=0,scalar=0,multiplier=1;
        for(AttributeModifier modifier:modifiers) {
            if(!modifier.getSlotGroup().test(EquipmentSlot.HAND))continue;
            switch(modifier.getOperation()) {
                case ADD_NUMBER -> add+=modifier.getAmount();
                case ADD_SCALAR -> scalar+=modifier.getAmount();
                case MULTIPLY_SCALAR_1 -> multiplier*=1+modifier.getAmount();
            }
        }
        double result=(base+add)*(1+scalar)*multiplier;
        int sharpness=0;
        if(attribute==Attribute.ATTACK_DAMAGE && meta.getEnchants()!=null)for(var enchantment:meta.getEnchants().entrySet())
            if(enchantment.getKey().getKey().toString().equals("minecraft:sharpness"))sharpness=enchantment.getValue();
        if(sharpness>0)result+=.5*sharpness+.5;
        return Math.max(0,result);
    }
    private static Component line(String value,NamedTextColor color){return Component.text(value,color).decoration(TextDecoration.ITALIC,false);}
    private static String number(double value){return java.math.BigDecimal.valueOf(value).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    public static List<Component> render(List<String> description,List<String> abilities,double damage,double speed) {
        List<Component> result=new ArrayList<>();
        for(String paragraph:description)for(String row:wrap(paragraph,WIDTH))result.add(line(row,NamedTextColor.GRAY));
        result.add(Component.empty());
        for(String ability:abilities) {
            String[] pieces=ability.split(" \\u2014 ",2);
            String trigger=pieces.length==2?pieces[0]:"",body=pieces.length==2?pieces[1]:pieces[0];
            String prefix=trigger.isEmpty()?"":trigger+" - ";List<String> rows=wrap(body,Math.max(8,WIDTH-prefix.length()));
            for(int i=0;i<rows.size();i++) {
                if(i==0 && !prefix.isEmpty())
                    result.add(line(trigger,NamedTextColor.WHITE).append(line(" - "+rows.get(i),NamedTextColor.GRAY)));
                else result.add(line(rows.get(i),NamedTextColor.GRAY));
            }
        }
        result.add(Component.empty());result.add(line(SECTION,NamedTextColor.GRAY));
        result.add(line(" "+DAMAGE+number(damage),NamedTextColor.DARK_GREEN));
        result.add(line(" "+SPEED+number(speed),NamedTextColor.DARK_GREEN));
        return result;
    }
    public static List<String> wrap(String paragraph,int limit) {
        List<String> rows=new ArrayList<>();StringBuilder row=new StringBuilder();
        for(String word:paragraph.trim().split("\\s+")) {
            if(row.length()>0 && row.length()+1+word.length()>limit){rows.add(row.toString());row.setLength(0);}
            if(row.length()>0)row.append(' ');row.append(word);
        }
        if(row.length()>0)rows.add(row.toString());return rows;
    }
}
