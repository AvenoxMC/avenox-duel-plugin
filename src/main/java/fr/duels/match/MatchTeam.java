package fr.duels.match;

import fr.duels.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MatchTeam {

    private static final ChatColor[] COLORS = {
            ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW, ChatColor.AQUA,
            ChatColor.LIGHT_PURPLE, ChatColor.GOLD, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
            ChatColor.DARK_PURPLE, ChatColor.WHITE, ChatColor.GRAY, ChatColor.DARK_RED, ChatColor.DARK_BLUE
    };

    private final int index;
    private final ChatColor color;
    private final List<UUID> members;
    private final Set<UUID> alive;

    public MatchTeam(int index, List<UUID> members) {
        this.index = index;
        this.color = COLORS[index % COLORS.length];
        this.members = new ArrayList<>(members);
        this.alive = new LinkedHashSet<>(members);
    }

    public int getIndex() {
        return index;
    }

    public ChatColor getColor() {
        return color;
    }

    public List<UUID> getMembers() {
        return members;
    }

    public Set<UUID> getAlive() {
        return alive;
    }

    public boolean isEliminated() {
        return alive.isEmpty();
    }

    public boolean isSolo() {
        return members.size() == 1;
    }

    /** Nom du joueur pour une équipe solo, sinon "Équipe Rouge", etc. */
    public String getDisplayName() {
        if (isSolo()) {
            return color + nameOf(members.get(0));
        }
        List<String> names = Lang.list("team-names");
        String name = index < names.size() ? names.get(index) : String.valueOf(index + 1);
        return color + Lang.get("team-format", "name", name);
    }

    public List<Player> getOnlineMembers() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                list.add(player);
            }
        }
        return list;
    }

    public static String nameOf(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? "?" : player.getName();
    }
}
