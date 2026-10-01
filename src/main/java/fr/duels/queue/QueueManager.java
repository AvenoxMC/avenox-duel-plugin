package fr.duels.queue;

import fr.duels.DuelsPlugin;
import fr.duels.arena.Arena;
import fr.duels.kit.Kit;
import fr.duels.match.MatchType;
import fr.duels.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** File d'attente 1v1 par kit : dès que 2 joueurs attendent le même kit et qu'une arène est libre, le match démarre. */
public class QueueManager {

    private final DuelsPlugin plugin;
    private final Map<String, LinkedList<UUID>> queues = new HashMap<>();
    private final Map<UUID, String> playerQueue = new HashMap<>();
    private final Set<UUID> warnedNoArena = new HashSet<>();

    public QueueManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long interval = Math.max(20L, plugin.getConfig().getLong("queue.check-interval", 40L));
        Bukkit.getScheduler().runTaskTimer(plugin, this::tryAll, interval, interval);
    }

    public void join(Player player, Kit kit) {
        if (plugin.getMatchManager().isBusy(player.getUniqueId())) {
            Lang.send(player, "general.busy");
            return;
        }
        if (plugin.getPartyManager().getParty(player.getUniqueId()) != null) {
            Lang.send(player, "queue.in-party");
            return;
        }
        if (!kit.isEnabled() || !kit.isQueue()) {
            Lang.send(player, "queue.kit-unavailable");
            return;
        }
        if (plugin.getArenaManager().getArenasFor(kit).isEmpty()) {
            Lang.send(player, "queue.no-arena-for-kit", "kit", kit.getDisplayName());
            return;
        }
        if (kit.getName().equals(playerQueue.get(player.getUniqueId()))) {
            Lang.send(player, "queue.already", "kit", kit.getDisplayName());
            return;
        }
        leave(player, false);
        LinkedList<UUID> queue = queues.computeIfAbsent(kit.getName(), k -> new LinkedList<>());
        queue.add(player.getUniqueId());
        playerQueue.put(player.getUniqueId(), kit.getName());
        Lang.send(player, "queue.joined", "kit", kit.getDisplayName(), "count", queue.size());
        plugin.getLobbyManager().giveItems(player);
        tryMatch(kit);
    }

    public boolean leave(Player player, boolean message) {
        String kitName = playerQueue.remove(player.getUniqueId());
        warnedNoArena.remove(player.getUniqueId());
        if (kitName == null) {
            return false;
        }
        LinkedList<UUID> queue = queues.get(kitName);
        if (queue != null) {
            queue.remove(player.getUniqueId());
        }
        if (message) {
            Kit kit = plugin.getKitManager().get(kitName);
            Lang.send(player, "queue.left", "kit", kit == null ? kitName : kit.getDisplayName());
            if (!plugin.getMatchManager().isBusy(player.getUniqueId())) {
                plugin.getLobbyManager().giveItems(player);
            }
        }
        return true;
    }

    public boolean isQueued(UUID uuid) {
        return playerQueue.containsKey(uuid);
    }

    public Kit getQueuedKit(UUID uuid) {
        return plugin.getKitManager().get(playerQueue.get(uuid));
    }

    public int getQueued(Kit kit) {
        LinkedList<UUID> queue = queues.get(kit.getName());
        return queue == null ? 0 : queue.size();
    }

    public void tryAll() {
        for (Kit kit : plugin.getKitManager().getKits()) {
            tryMatch(kit);
        }
    }

    private void tryMatch(Kit kit) {
        LinkedList<UUID> queue = queues.get(kit.getName());
        if (queue == null) {
            return;
        }
        // nettoyage des joueurs déconnectés
        for (Iterator<UUID> it = queue.iterator(); it.hasNext(); ) {
            UUID uuid = it.next();
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                it.remove();
                playerQueue.remove(uuid);
            }
        }
        while (queue.size() >= 2) {
            Arena arena = plugin.getArenaManager().findAvailable(kit);
            if (arena == null) {
                for (UUID uuid : queue) {
                    if (warnedNoArena.add(uuid)) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            Lang.send(player, "queue.waiting-arena");
                        }
                    }
                }
                return;
            }
            Player first = Bukkit.getPlayer(queue.poll());
            Player second = Bukkit.getPlayer(queue.poll());
            playerQueue.remove(first.getUniqueId());
            playerQueue.remove(second.getUniqueId());
            warnedNoArena.remove(first.getUniqueId());
            warnedNoArena.remove(second.getUniqueId());
            List<List<Player>> teams = Arrays.asList(
                    Collections.singletonList(first), Collections.singletonList(second));
            plugin.getMatchManager().startMatch(MatchType.DUEL, kit, arena, teams);
        }
    }
}
