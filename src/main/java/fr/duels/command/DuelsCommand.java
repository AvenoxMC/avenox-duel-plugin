package fr.duels.command;

import fr.duels.DuelsPlugin;
import fr.duels.util.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /duels setlobby | lobby | reload | help */
public class DuelsCommand implements CommandExecutor, TabCompleter {

    private final DuelsPlugin plugin;

    public DuelsCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();
        switch (sub) {
            case "lobby":
            case "spawn":
                if (!(sender instanceof Player)) {
                    Lang.send(sender, "general.player-only");
                    return true;
                }
                Player player = (Player) sender;
                if (plugin.getMatchManager().isBusy(player.getUniqueId())) {
                    Lang.send(player, "general.busy");
                    return true;
                }
                plugin.getLobbyManager().sendToLobby(player);
                return true;
            case "setlobby":
                if (!sender.hasPermission("duels.admin")) {
                    Lang.send(sender, "general.no-permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    Lang.send(sender, "general.player-only");
                    return true;
                }
                plugin.getLobbyManager().setLobby(((Player) sender).getLocation());
                Lang.send(sender, "admin.lobby-set");
                return true;
            case "reload":
                if (!sender.hasPermission("duels.admin")) {
                    Lang.send(sender, "general.no-permission");
                    return true;
                }
                plugin.reload();
                Lang.send(sender, "admin.reloaded");
                return true;
            default:
                for (String line : Lang.list(sender.hasPermission("duels.admin") ? "admin.help" : "general.help")) {
                    sender.sendMessage(line);
                }
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>(Arrays.asList("lobby", "help"));
        if (sender.hasPermission("duels.admin")) {
            out.add("setlobby");
            out.add("reload");
        }
        return args.length == 1 ? Completions.filter(out, args[0]) : new ArrayList<String>();
    }
}
