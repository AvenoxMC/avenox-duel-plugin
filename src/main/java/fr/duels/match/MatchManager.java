package fr.duels.match;

import fr.duels.DuelsPlugin;
import fr.duels.arena.Arena;
import fr.duels.arena.WorldService;
import fr.duels.kit.Kit;
import fr.duels.util.Lang;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MatchManager {

    private final DuelsPlugin plugin;
    private final Map<Integer, Match> matches = new LinkedHashMap<>();
    private final Map<UUID, Match> players = new HashMap<>();
    private final Map<UUID, Match> spectators = new HashMap<>();
    private int nextId;

    public MatchManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Crée un match : réserve une arène disponible, puis copie et charge sa map en arrière-plan.
     *
     * @param preferred arène imposée (null = choix automatique)
     * @return le match, ou null si aucune arène n'est disponible
     */
    public Match startMatch(MatchType type, Kit kit, Arena preferred, List<List<Player>> teamPlayers) {
        Arena arena;
        if (preferred != null) {
            arena = plugin.getArenaManager().isAvailable(preferred, kit) ? preferred : null;
        } else {
            arena = plugin.getArenaManager().findAvailable(kit);
        }
        if (arena == null) {
            return null;
        }

        List<MatchTeam> teams = new ArrayList<>();
        for (int i = 0; i < teamPlayers.size(); i++) {
            List<UUID> uuids = new ArrayList<>();
            for (Player player : teamPlayers.get(i)) {
                uuids.add(player.getUniqueId());
            }
            teams.add(new MatchTeam(i, uuids));
        }

        final Match match = new Match(plugin, ++nextId, type, kit, arena, teams);
        matches.put(match.getId(), match);
        arena.incrementInstances();

        for (List<Player> list : teamPlayers) {
            for (Player player : list) {
                plugin.getQueueManager().leave(player, false);
                Match spectating = spectators.get(player.getUniqueId());
                if (spectating != null) {
                    spectating.removeSpectator(player, false);
                }
                players.put(player.getUniqueId(), match);
                player.closeInventory();
            }
        }
        match.announceFound();

        final WorldService worlds = plugin.getWorldService();
        final String worldName = worlds.nextInstanceName(arena);
        final File folder = worlds.getWorldFolder(worldName);
        worlds.copyWorldAsync(worlds.getWorldFolder(arena.getTemplateWorld()), worldName, ok -> {
            if (match.getState() == MatchState.ENDED) {
                worlds.deleteAsync(folder);
                return;
            }
            if (!ok) {
                match.abort("match.load-error");
                worlds.deleteAsync(folder);
                return;
            }
            World world = null;
            try {
                world = worlds.loadWorld(worldName, true);
            } catch (Exception e) {
                plugin.getLogger().severe("Chargement de " + worldName + " impossible : " + e);
            }
            if (world == null) {
                match.abort("match.load-error");
                worlds.deleteAsync(folder);
                return;
            }
            match.begin(world);
        });
        return match;
    }

    public Match getMatch(UUID uuid) {
        return players.get(uuid);
    }

    public Match getMatch(Player player) {
        return players.get(player.getUniqueId());
    }

    public Match getSpectating(UUID uuid) {
        return spectators.get(uuid);
    }

    /** Joueur occupé = participant (vivant ou mort) ou spectateur d'un match. */
    public boolean isBusy(UUID uuid) {
        return players.containsKey(uuid) || spectators.containsKey(uuid);
    }

    public Match getMatchByWorld(World world) {
        for (Match match : matches.values()) {
            if (world.equals(match.getWorld())) {
                return match;
            }
        }
        return null;
    }

    public void unregister(UUID uuid) {
        players.remove(uuid);
    }

    public void registerSpectator(UUID uuid, Match match) {
        spectators.put(uuid, match);
    }

    public void unregisterSpectator(UUID uuid) {
        spectators.remove(uuid);
    }

    void removeMatch(Match match) {
        matches.remove(match.getId());
        // une arène vient peut-être de se libérer pour les joueurs en file
        plugin.getQueueManager().tryAll();
    }

    public Collection<Match> getMatches() {
        return matches.values();
    }

    public int countPlaying(Kit kit) {
        int count = 0;
        for (Match match : matches.values()) {
            if (match.getKit().getName().equals(kit.getName()) && match.getState() != MatchState.ENDED) {
                count += match.getAllMembers().size();
            }
        }
        return count;
    }

    public void spectate(Player player, Match match, Player target) {
        if (isBusy(player.getUniqueId())) {
            Lang.send(player, "general.busy");
            return;
        }
        if (match.getState() != MatchState.STARTING && match.getState() != MatchState.FIGHTING) {
            Lang.send(player, "spectate.not-available");
            return;
        }
        plugin.getQueueManager().leave(player, false);
        match.addSpectator(player, target);
        Lang.send(player, "spectate.joined", "arena", match.getArena().getDisplayName(), "kit", match.getKit().getDisplayName());
    }

    /** /leave : quitte le match (abandon) ou arrête de regarder. */
    public boolean leave(Player player) {
        Match match = players.get(player.getUniqueId());
        if (match != null) {
            match.leave(player);
            return true;
        }
        Match spectating = spectators.get(player.getUniqueId());
        if (spectating != null) {
            spectating.removeSpectator(player, true);
            return true;
        }
        return false;
    }

    public void handleQuit(Player player) {
        Match match = players.get(player.getUniqueId());
        if (match != null) {
            match.quit(player);
        }
        Match spectating = spectators.get(player.getUniqueId());
        if (spectating != null) {
            spectating.removeSpectator(player, false);
        }
    }

    public void shutdown() {
        for (Match match : new ArrayList<>(matches.values())) {
            match.forceEnd();
        }
        matches.clear();
        players.clear();
        spectators.clear();
    }
}
