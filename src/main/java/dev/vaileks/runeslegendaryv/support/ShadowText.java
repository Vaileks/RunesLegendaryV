package dev.vaileks.runeslegendaryv.support;
import net.kyori.adventure.text.Component;import net.kyori.adventure.text.format.ShadowColor;
public final class ShadowText {
 public static Component shadow(Component text){return (text==null?Component.empty():text).shadowColor(ShadowColor.shadowColor(0xff000000));}
 public static Component legacy(String text){return shadow(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(text==null?"":text.replace('§','&')));}
 public static Component mini(String text){return shadow(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(text==null?"":text));}
}
