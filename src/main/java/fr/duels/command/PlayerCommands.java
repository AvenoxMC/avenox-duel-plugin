package fr.duels.command;

import fr.duels.DuelsPlugin;
import fr.duels.gui.QueueMenu;
import fr.duels.gui.SpectateMenu;
import fr.duels.kit.Kit;
import fr.duels.match.Match;
import fr.duels.stats.PlayerStats;
import fr.duels.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /queue [kit], /leave, /spectate [joueur], /stats [joueur] */
public class PlayerCommands implements CommandExecutor, TabCompleter {

    private final DuelsPlugin plugin;

    public PlayerCommands(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Lang.send(sender, "general.player-only");
            return true;
        }
        Player player = (Player) sender;
        switch (command.getName().toLowerCase()) {
            case "queue":
                queue(player, args);
                break;
            case "leave":
                leave(player);
                break;
            case "spectate":
                spectate(player, args);
                break;
            case "stats":
                stats(player, args);
                break;
            default:
                break;
        }
        return true;
    }

    private void queue(Player player, String[] args) {
        if (args.length == 0) {
            new QueueMenu(plugin).open(player);
            return;
        }
        Kit kit = plugin.getKitManager().get(args[0]);
        if (kit == null) {
            Lang.send(player, "general.kit-not-found", "kit", args[0]);
            return;
        }
        plugin.getQueueManager().join(player, kit);
    }

    private void leave(Player player) {
        if (plugin.getMatchManager().leave(player)) {
            return;
        }
        if (plugin.getQueueManager().leave(player, true)) {
            return;
        }
        Lang.send(player, "general.nothing-to-leave");
    }

    private void spectate(Player player, String[] args) {
        if (args.length == 0) {
            new SpectateMenu(plugin).open(player);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Lang.send(player, "general.player-not-found", "player", args[0]);
            return;
        }
        Match match = plugin.getMatchManager().getMatch(target.getUniqueId());
        if (match == null) {
            Lang.send(player, "spectate.not-in-match", "player", target.getName());
            return;
        }
        plugin.getMatchManager().spectate(player, match, target);
    }

    private void stats(Player player, String[] args) {
        PlayerStats stats;
        if (args.length == 0) {
            stats = plugin.getStatsManager().get(player);
        } else {
            Player target = Bukkit.getPlayerExact(args[0]);
            stats = target != null ? plugin.getStatsManager().get(target) : plugin.getStatsManager().find(args[0]);
        }
        if (stats == null) {
            Lang.send(player, "stats.not-found", "player", args[0]);
            return;
        }
        for (String line : Lang.list("stats.lines",
                "player", stats.getName(),
                "wins", stats.getWins(),
                "losses", stats.getLosses(),
                "kills", stats.getKills(),
                "deaths", stats.getDeaths(),
                "ratio", stats.getRatio())) {
            player.sendMessage(line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (command.getName().equalsIgnoreCase("queue")) {
                for (Kit kit : plugin.getKitManager().getQueueKits()) {
                    out.add(kit.getName());
                }
            } else {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    out.add(online.getName());
                }
            }
        }
        return Completions.filter(out, args.length == 0 ? "" : args[args.length - 1]);
    }
}
