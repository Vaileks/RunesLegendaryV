package dev.vaileks.runeslegendaryv.support;
import org.bukkit.entity.Player;
public final class TeamAllies {public static boolean areAllies(Player a,Player b){var ta=a.getScoreboard().getEntryTeam(a.getName());var tb=a.getScoreboard().getEntryTeam(b.getName());return ta!=null&&tb!=null&&ta.getName().equals(tb.getName())&&!ta.allowFriendlyFire();}}
