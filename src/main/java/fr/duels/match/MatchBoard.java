package fr.duels.match;

import fr.duels.util.Lang;
import fr.duels.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Scoreboard d'un match : couleurs d'équipe, vie sous le pseudo et barre latérale. */
public class MatchBoard {

    private static final int MAX_LINES = 15;

    private final Scoreboard scoreboard;
    private final Objective sidebar;
    private final Objective health;
    private int shownLines;

    public MatchBoard(Match match) {
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        this.health = scoreboard.registerNewObjective("duels_hp", "health");
        health.setDisplaySlot(DisplaySlot.BELOW_NAME);
        health.setDisplayName(ChatColor.RED + "❤");

        this.sidebar = scoreboard.registerNewObjective("duels_side", "dummy");
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        sidebar.setDisplayName(trim(Lang.get("scoreboard.title"), 32));

        for (MatchTeam matchTeam : match.getTeams()) {
            Team team = scoreboard.registerNewTeam("duels_t" + matchTeam.getIndex());
            team.setPrefix(matchTeam.getColor().toString());
            team.setCanSeeFriendlyInvisibles(true);
            team.setAllowFriendlyFire(false);
            for (UUID uuid : matchTeam.getMembers()) {
                team.addEntry(MatchTeam.nameOf(uuid));
            }
        }
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    public void show(Player player) {
        player.setScoreboard(scoreboard);
        health.getScore(player.getName()).setScore((int) Math.ceil(player.getHealth()));
    }

    public void update(Match match) {
        long seconds = match.getElapsedSeconds();
        List<String> lines = new ArrayList<>();
        for (String raw : Lang.list("scoreboard.lines",
                "kit", match.getKit().getDisplayName(),
                "arena", match.getArena().getDisplayName(),
                "time", PlayerUtil.formatTime(seconds),
                "alive", match.getAlivePlayers().size(),
                "spectators", match.getSpectators().size(),
                "type", match.getType().getDisplayName())) {
            if (ChatColor.stripColor(raw).trim().equals("{teams}")) {
                for (MatchTeam team : match.getTeams()) {
                    if (lines.size() >= MAX_LINES) {
                        break;
                    }
                    lines.add(teamLine(team));
                }
            } else {
                lines.add(Lang.color(raw));
            }
        }
        setLines(lines);
    }

    private String teamLine(MatchTeam team) {
        if (team.isSolo()) {
            Player player = Bukkit.getPlayer(team.getMembers().get(0));
            String hearts = (player != null && team.getAlive().contains(player.getUniqueId())) ? PlayerUtil.hearts(player) : "0";
            return Lang.get("scoreboard.team-solo", "name", team.getDisplayName(), "hearts", hearts);
        }
        return Lang.get("scoreboard.team", "name", team.getDisplayName(),
                "alive", team.getAlive().size(), "total", team.getMembers().size());
    }

    private void setLines(List<String> lines) {
        int count = Math.min(MAX_LINES, lines.size());
        for (int i = 0; i < count; i++) {
            String entry = ChatColor.values()[i].toString() + ChatColor.RESET;
            Team team = scoreboard.getTeam("duels_l" + i);
            if (team == null) {
                team = scoreboard.registerNewTeam("duels_l" + i);
                team.addEntry(entry);
            }
            String text = lines.get(i);
            String prefix = text.length() <= 16 ? text : text.substring(0, 16);
            if (prefix.endsWith(String.valueOf(ChatColor.COLOR_CHAR))) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
            String suffix = "";
            if (text.length() > prefix.length()) {
                suffix = trim(ChatColor.getLastColors(prefix) + text.substring(prefix.length()), 16);
            }
            team.setPrefix(prefix);
            team.setSuffix(suffix);
            sidebar.getScore(entry).setScore(count - i);
        }
        for (int i = count; i < shownLines; i++) {
            scoreboard.resetScores(ChatColor.values()[i].toString() + ChatColor.RESET);
        }
        shownLines = count;
    }

    private static String trim(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        String cut = text.substring(0, max);
        return cut.endsWith(String.valueOf(ChatColor.COLOR_CHAR)) ? cut.substring(0, max - 1) : cut;
    }
}
