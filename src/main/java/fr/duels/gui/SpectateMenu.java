package fr.duels.gui;

import fr.duels.DuelsPlugin;
import fr.duels.match.Match;
import fr.duels.match.MatchState;
import fr.duels.match.MatchTeam;
import fr.duels.util.ItemBuilder;
import fr.duels.util.Lang;
import fr.duels.util.PlayerUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Liste des matchs en cours ; un clic permet de les regarder. */
public class SpectateMenu extends Menu {

    public SpectateMenu(DuelsPlugin plugin) {
        super(plugin, Lang.raw("menu.spectate.title"), 3);
    }

    @Override
    protected void build(Player viewer) {
        List<Match> matches = new ArrayList<>();
        for (Match match : plugin.getMatchManager().getMatches()) {
            if (match.getState() == MatchState.STARTING || match.getState() == MatchState.FIGHTING) {
                matches.add(match);
            }
        }
        setRows(rowsFor(Math.max(1, matches.size())));
        if (matches.isEmpty()) {
            set(13, new ItemBuilder(Material.BARRIER).name(Lang.raw("menu.spectate.empty")).build());
            return;
        }
        int[] slots = slotsFor(matches.size());
        for (int i = 0; i < slots.length; i++) {
            final Match match = matches.get(i);
            List<String> names = new ArrayList<>();
            for (MatchTeam team : match.getTeams()) {
                names.add(team.getDisplayName());
            }
            String title = match.getTeams().size() == 2
                    ? names.get(0) + " &7vs " + names.get(1)
                    : Lang.raw("menu.spectate.ffa-name").replace("{count}", String.valueOf(match.getAllMembers().size()));
            String head = MatchTeam.nameOf(match.getTeams().get(0).getMembers().get(0));
            set(slots[i], ItemBuilder.skull(head)
                    .name(title)
                    .lore(Lang.list("menu.spectate.lore",
                            "type", match.getType().getDisplayName(),
                            "kit", match.getKit().getDisplayName(),
                            "arena", match.getArena().getDisplayName(),
                            "time", PlayerUtil.formatTime(match.getElapsedSeconds()),
                            "alive", match.getAlivePlayers().size(),
                            "spectators", match.getSpectators().size()))
                    .build(), (player, click) -> {
                player.closeInventory();
                List<Player> alive = match.getAlivePlayers();
                plugin.getMatchManager().spectate(player, match, alive.isEmpty() ? null : alive.get(0));
            });
        }
    }
}
