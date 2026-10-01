package fr.duels.match;

import fr.duels.DuelsPlugin;
import fr.duels.arena.Arena;
import fr.duels.kit.Kit;
import fr.duels.stats.PlayerStats;
import fr.duels.util.Lang;
import fr.duels.util.PlayerUtil;
import fr.duels.util.SpawnPoint;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("deprecation") // sendTitle est marqué "API susceptible de changer" en 1.8
public class Match {

    public enum Elimination { DEATH, QUIT, FORFEIT }

    private static final long KILL_CREDIT_MS = 15000L;

    private final DuelsPlugin plugin;
    private final int id;
    private final MatchType type;
    private final Kit kit;
    private final Arena arena;
    private final List<MatchTeam> teams;
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Set<Location> placedBlocks = new HashSet<>();
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();

    private World world;
    private MatchState state = MatchState.LOADING;
    private BukkitTask task;
    private int countdown;
    private long fightStart;
    private MatchBoard board;

    public Match(DuelsPlugin plugin, int id, MatchType type, Kit kit, Arena arena, List<MatchTeam> teams) {
        this.plugin = plugin;
        this.id = id;
        this.type = type;
        this.kit = kit;
        this.arena = arena;
        this.teams = teams;
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    /** Appelé quand la copie de la map est chargée : téléporte, équipe et lance le compte à rebours. */
    public void begin(World world) {
        this.world = world;
        for (MatchTeam team : teams) {
            for (UUID uuid : new ArrayList<>(team.getAlive())) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    team.getAlive().remove(uuid);
                }
            }
        }
        if (getAliveTeams().size() < 2) {
            abort("match.cancelled-not-enough");
            return;
        }

        board = new MatchBoard(this);
        List<SpawnPoint> spawns = arena.getSpawns();
        int global = 0;
        for (MatchTeam team : teams) {
            for (UUID uuid : team.getMembers()) {
                SpawnPoint point = teams.size() == 2
                        ? spawns.get(team.getIndex() % spawns.size())
                        : spawns.get(global % spawns.size());
                global++;
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !team.getAlive().contains(uuid)) {
                    continue;
                }
                Location location = point.toLocation(world);
                location.getChunk().load();
                player.teleport(location);
                kit.apply(player);
                board.show(player);
            }
        }

        state = MatchState.STARTING;
        countdown = Math.max(0, plugin.getConfig().getInt("match.countdown", 5));
        board.update(this);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
    }

    private void tick() {
        if (state == MatchState.STARTING) {
            if (countdown > 0) {
                for (Player player : getAlivePlayers()) {
                    player.sendTitle(Lang.get("match.countdown-title", "seconds", countdown), Lang.get("match.countdown-subtitle"));
                    player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1f);
                }
                broadcast(Lang.get("match.countdown-chat", "seconds", countdown));
                countdown--;
            } else {
                state = MatchState.FIGHTING;
                fightStart = System.currentTimeMillis();
                for (Player player : getAlivePlayers()) {
                    player.sendTitle(Lang.get("match.start-title"), Lang.get("match.start-subtitle"));
                    player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 2f);
                }
                broadcast(Lang.get("match.started"));
            }
        } else if (state == MatchState.FIGHTING) {
            int max = plugin.getConfig().getInt("match.max-duration", 900);
            if (max > 0 && getElapsedSeconds() >= max) {
                broadcast(Lang.get("match.time-limit"));
                end(null);
                return;
            }
        }
        if (board != null && state != MatchState.ENDED) {
            board.update(this);
        }
    }

    /** Élimine un joueur (mort, déconnexion ou abandon) puis vérifie la fin du match. */
    public void eliminate(Player victim, Elimination reason) {
        UUID uuid = victim.getUniqueId();
        MatchTeam team = getTeam(uuid);
        if (team == null || !team.getAlive().contains(uuid)) {
            return;
        }
        if (state == MatchState.ENDING || state == MatchState.ENDED) {
            return;
        }
        team.getAlive().remove(uuid);
        if (state == MatchState.LOADING) {
            return;
        }

        Player killer = resolveKiller(victim);
        plugin.getStatsManager().get(victim).addDeath();
        if (killer != null) {
            plugin.getStatsManager().get(killer).addKill();
            killer.playSound(killer.getLocation(), Sound.ORB_PICKUP, 1f, 1f);
        }

        String victimName = team.getColor() + victim.getName();
        switch (reason) {
            case QUIT:
                broadcast(Lang.get("match.death-quit", "victim", victimName));
                break;
            case FORFEIT:
                broadcast(Lang.get("match.death-forfeit", "victim", victimName));
                break;
            default:
                if (killer != null) {
                    MatchTeam killerTeam = getTeam(killer.getUniqueId());
                    String killerName = (killerTeam != null ? killerTeam.getColor() : "") + killer.getName();
                    broadcast(Lang.get("match.death-killed", "victim", victimName, "killer", killerName,
                            "hearts", PlayerUtil.hearts(killer)));
                } else {
                    broadcast(Lang.get("match.death", "victim", victimName));
                }
        }

        if (reason == Elimination.DEATH) {
            if (plugin.getConfig().getBoolean("match.lightning-on-death", true)) {
                world.strikeLightningEffect(victim.getLocation());
            }
            if (!victim.isDead()) {
                makeDeadSpectator(victim);
            }
            victim.sendTitle(Lang.get("match.dead-title"), Lang.get("match.dead-subtitle"));
        }
        checkEnd();
    }

    /** Un participant éliminé reste dans la map en mode spectateur jusqu'à la fin. */
    public void makeDeadSpectator(Player player) {
        Location location = player.getLocation();
        PlayerUtil.reset(player, GameMode.SPECTATOR);
        if (location.getWorld() != world || location.getY() < arena.getVoidY() + 2) {
            location = getSpectatorLocation();
        }
        player.teleport(location);
    }

    private void checkEnd() {
        List<MatchTeam> remaining = getAliveTeams();
        if (remaining.size() <= 1) {
            end(remaining.isEmpty() ? null : remaining.get(0));
        }
    }

    /** Termine le match (winner null = égalité) et programme le retour au lobby. */
    public void end(MatchTeam winner) {
        if (state == MatchState.ENDING || state == MatchState.ENDED) {
            return;
        }
        state = MatchState.ENDING;

        if (winner != null) {
            if (winner.isSolo()) {
                Player player = Bukkit.getPlayer(winner.getMembers().get(0));
                broadcast(Lang.get("match.winner-solo", "winner", winner.getDisplayName(),
                        "hearts", player == null ? "0" : PlayerUtil.hearts(player)));
            } else {
                broadcast(Lang.get("match.winner-team", "winner", winner.getDisplayName()));
            }
            for (MatchTeam team : teams) {
                boolean won = team == winner;
                for (UUID uuid : team.getMembers()) {
                    PlayerStats stats = plugin.getStatsManager().get(uuid, MatchTeam.nameOf(uuid));
                    if (won) {
                        stats.addWin();
                    } else {
                        stats.addLoss();
                    }
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && isParticipantHere(player)) {
                        if (won) {
                            player.sendTitle(Lang.get("match.win-title"), Lang.get("match.win-subtitle"));
                            player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
                        } else {
                            player.sendTitle(Lang.get("match.lose-title"), Lang.get("match.lose-subtitle", "winner", winner.getDisplayName()));
                        }
                    }
                }
            }
            for (Player player : getAlivePlayers()) {
                player.setFireTicks(0);
                player.setHealth(player.getMaxHealth());
            }
        } else {
            broadcast(Lang.get("match.draw"));
            for (Player player : getOnlineParticipants()) {
                player.sendTitle(Lang.get("match.draw-title"), "");
            }
        }

        if (board != null) {
            board.update(this);
        }
        if (task != null) {
            task.cancel();
        }
        int delay = Math.max(1, plugin.getConfig().getInt("match.end-delay", 4));
        task = Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, delay * 20L);
    }

    /** Renvoie tout le monde au lobby, décharge et supprime la copie de la map. */
    private void cleanup() {
        if (state == MatchState.ENDED) {
            return;
        }
        state = MatchState.ENDED;
        releasePlayers();
        plugin.getWorldService().unloadAndDelete(world);
        arena.decrementInstances();
        plugin.getMatchManager().removeMatch(this);
    }

    /** Annule un match (chargement raté, joueurs partis...). */
    public void abort(String messageKey) {
        if (state == MatchState.ENDED) {
            return;
        }
        state = MatchState.ENDED;
        if (task != null) {
            task.cancel();
        }
        for (Player player : getOnlineParticipants()) {
            Lang.send(player, messageKey);
        }
        releasePlayers();
        if (world != null) {
            plugin.getWorldService().unloadAndDelete(world);
        }
        arena.decrementInstances();
        plugin.getMatchManager().removeMatch(this);
    }

    /** Arrêt du serveur : nettoyage immédiat et synchrone. */
    public void forceEnd() {
        if (state == MatchState.ENDED) {
            return;
        }
        state = MatchState.ENDED;
        if (task != null) {
            task.cancel();
        }
        releasePlayers();
        if (world != null) {
            plugin.getWorldService().unloadAndDeleteNow(world);
        }
        arena.decrementInstances();
    }

    private void releasePlayers() {
        MatchManager manager = plugin.getMatchManager();
        for (MatchTeam team : teams) {
            for (UUID uuid : team.getMembers()) {
                if (manager.getMatch(uuid) == this) {
                    manager.unregister(uuid);
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        plugin.getLobbyManager().sendToLobby(player);
                    }
                }
            }
        }
        for (UUID uuid : new ArrayList<>(spectators)) {
            manager.unregisterSpectator(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                plugin.getLobbyManager().sendToLobby(player);
            }
        }
        spectators.clear();
    }

    // ------------------------------------------------------------------
    // Joueurs qui partent / spectateurs
    // ------------------------------------------------------------------

    /** /leave d'un participant : abandon s'il est encore en vie, puis retour au lobby. */
    public void leave(Player player) {
        if (isAlive(player.getUniqueId()) && (state == MatchState.STARTING || state == MatchState.FIGHTING)) {
            eliminate(player, Elimination.FORFEIT);
        } else if (state == MatchState.LOADING) {
            MatchTeam team = getTeam(player.getUniqueId());
            if (team != null) {
                team.getAlive().remove(player.getUniqueId());
            }
        }
        plugin.getMatchManager().unregister(player.getUniqueId());
        plugin.getLobbyManager().sendToLobby(player);
    }

    /** Déconnexion d'un participant. */
    public void quit(Player player) {
        if (state == MatchState.LOADING) {
            MatchTeam team = getTeam(player.getUniqueId());
            if (team != null) {
                team.getAlive().remove(player.getUniqueId());
            }
        } else {
            eliminate(player, Elimination.QUIT);
        }
        plugin.getMatchManager().unregister(player.getUniqueId());
    }

    public void addSpectator(Player player, Player target) {
        spectators.add(player.getUniqueId());
        plugin.getMatchManager().registerSpectator(player.getUniqueId(), this);
        PlayerUtil.reset(player, GameMode.SPECTATOR);
        player.teleport(target != null && target.getWorld() == world ? target.getLocation() : getSpectatorLocation());
        if (board != null) {
            player.setScoreboard(board.getScoreboard());
        }
        if (plugin.getConfig().getBoolean("match.announce-spectators", true)) {
            broadcast(Lang.get("spectate.joined-broadcast", "player", player.getName()));
        }
    }

    public void removeSpectator(Player player, boolean toLobby) {
        spectators.remove(player.getUniqueId());
        plugin.getMatchManager().unregisterSpectator(player.getUniqueId());
        if (toLobby) {
            plugin.getLobbyManager().sendToLobby(player);
        }
    }

    // ------------------------------------------------------------------
    // Combat
    // ------------------------------------------------------------------

    public void recordDamage(Player victim, Player damager) {
        lastDamager.put(victim.getUniqueId(), damager.getUniqueId());
        lastDamageTime.put(victim.getUniqueId(), System.currentTimeMillis());
    }

    private Player resolveKiller(Player victim) {
        UUID damager = lastDamager.get(victim.getUniqueId());
        Long time = lastDamageTime.get(victim.getUniqueId());
        if (damager == null || time == null || System.currentTimeMillis() - time > KILL_CREDIT_MS) {
            return null;
        }
        Player killer = Bukkit.getPlayer(damager);
        return killer != null && killer.isOnline() && !killer.equals(victim) ? killer : null;
    }

    public void addPlacedBlock(Block block) {
        placedBlocks.add(block.getLocation());
    }

    public boolean removePlacedBlock(Block block) {
        return placedBlocks.remove(block.getLocation());
    }

    public boolean isPlacedBlock(Block block) {
        return placedBlocks.contains(block.getLocation());
    }

    // ------------------------------------------------------------------
    // Accesseurs
    // ------------------------------------------------------------------

    public Location getSpectatorLocation() {
        SpawnPoint point = arena.getSpectatorSpawn();
        if (point == null) {
            point = arena.getSpawns().get(0);
            Location location = point.toLocation(world);
            return location.add(0, 5, 0);
        }
        return point.toLocation(world);
    }

    public Location getSpawnFor(Player player) {
        MatchTeam team = getTeam(player.getUniqueId());
        int index = team == null ? 0 : team.getIndex();
        if (teams.size() != 2 && team != null) {
            int global = 0;
            for (MatchTeam t : teams) {
                for (UUID uuid : t.getMembers()) {
                    if (uuid.equals(player.getUniqueId())) {
                        index = global;
                    }
                    global++;
                }
            }
        }
        return arena.getSpawns().get(index % arena.getSpawns().size()).toLocation(world);
    }

    public void broadcast(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        for (Player player : getOnlineParticipants()) {
            player.sendMessage(message);
        }
        for (UUID uuid : spectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    public void announceFound() {
        for (MatchTeam team : teams) {
            List<String> opponents = new ArrayList<>();
            for (MatchTeam other : teams) {
                if (other != team) {
                    opponents.add(other.getDisplayName());
                }
            }
            for (Player player : team.getOnlineMembers()) {
                Lang.send(player, "match.found",
                        "kit", kit.getDisplayName(),
                        "arena", arena.getDisplayName(),
                        "type", type.getDisplayName(),
                        "opponents", String.join("&7, ", opponents));
            }
        }
    }

    private boolean isParticipantHere(Player player) {
        return plugin.getMatchManager().getMatch(player.getUniqueId()) == this;
    }

    public List<MatchTeam> getAliveTeams() {
        List<MatchTeam> list = new ArrayList<>();
        for (MatchTeam team : teams) {
            if (!team.isEliminated()) {
                list.add(team);
            }
        }
        return list;
    }

    public List<Player> getAlivePlayers() {
        List<Player> list = new ArrayList<>();
        for (MatchTeam team : teams) {
            for (UUID uuid : team.getAlive()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    list.add(player);
                }
            }
        }
        return list;
    }

    /** Participants en ligne encore rattachés à ce match (vivants ou morts-spectateurs). */
    public List<Player> getOnlineParticipants() {
        List<Player> list = new ArrayList<>();
        for (MatchTeam team : teams) {
            for (UUID uuid : team.getMembers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline() && isParticipantHere(player)) {
                    list.add(player);
                }
            }
        }
        return list;
    }

    public List<UUID> getAllMembers() {
        List<UUID> list = new ArrayList<>();
        for (MatchTeam team : teams) {
            list.addAll(team.getMembers());
        }
        return list;
    }

    public MatchTeam getTeam(UUID uuid) {
        for (MatchTeam team : teams) {
            if (team.getMembers().contains(uuid)) {
                return team;
            }
        }
        return null;
    }

    public boolean isAlive(UUID uuid) {
        MatchTeam team = getTeam(uuid);
        return team != null && team.getAlive().contains(uuid);
    }

    public long getElapsedSeconds() {
        return fightStart == 0 ? 0 : (System.currentTimeMillis() - fightStart) / 1000L;
    }

    public int getId() {
        return id;
    }

    public MatchType getType() {
        return type;
    }

    public Kit getKit() {
        return kit;
    }

    public Arena getArena() {
        return arena;
    }

    public List<MatchTeam> getTeams() {
        return teams;
    }

    public Set<UUID> getSpectators() {
        return spectators;
    }

    public World getWorld() {
        return world;
    }

    public MatchState getState() {
        return state;
    }
}
