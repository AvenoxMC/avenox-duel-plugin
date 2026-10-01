package fr.duels.command;

import fr.duels.DuelsPlugin;
import fr.duels.arena.Arena;
import fr.duels.gui.ArenaSelectMenu;
import fr.duels.gui.KitSelectMenu;
import fr.duels.kit.Kit;
import fr.duels.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /duel <joueur> [kit] [arène] | /duel accept|deny <joueur> */
public class DuelCommand implements CommandExecutor, TabCompleter {

    private final DuelsPlugin plugin;

    public DuelCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Lang.send(sender, "general.player-only");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            Lang.send(player, "duel.usage");
            return true;
        }
        if (args[0].equalsIgnoreCase("accept") && args.length >= 2) {
            plugin.getRequestManager().accept(player, args[1]);
            return true;
        }
        if ((args[0].equalsIgnoreCase("deny") || args[0].equalsIgnoreCase("refuse")) && args.length >= 2) {
            plugin.getRequestManager().deny(player, args[1]);
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Lang.send(player, "general.player-not-found", "player", args[0]);
            return true;
        }
        if (target.equals(player)) {
            Lang.send(player, "duel.self");
            return true;
        }
        if (args.length >= 2) {
            Kit kit = plugin.getKitManager().get(args[1]);
            if (kit == null || !kit.isEnabled()) {
                Lang.send(player, "general.kit-not-found", "kit", args[1]);
                return true;
            }
            Arena arena = null;
            if (args.length >= 3) {
                arena = plugin.getArenaManager().get(args[2]);
                if (arena == null || !arena.supports(kit)) {
                    Lang.send(player, "general.arena-not-found", "arena", args[2]);
                    return true;
                }
            }
            plugin.getRequestManager().send(player, target, kit, arena);
            return true;
        }
        new KitSelectMenu(plugin, Lang.raw("menu.kit-select.duel-title").replace("{player}", target.getName()), (p, kit) ->
                ArenaSelectMenu.openOrSkip(plugin, p, kit, (p2, arena) -> {
                    if (target.isOnline()) {
                        plugin.getRequestManager().send(p2, target, kit, arena);
                    }
                })).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("accept");
            out.add("deny");
            for (Player online : Bukkit.getOnlinePlayers()) {
                out.add(online.getName());
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                out.add(online.getName());
            }
        } else if (args.length == 2) {
            for (Kit kit : plugin.getKitManager().getEnabledKits()) {
                out.add(kit.getName());
            }
        } else if (args.length == 3) {
            for (Arena arena : plugin.getArenaManager().getArenas()) {
                out.add(arena.getName());
            }
        }
        return Completions.filter(out, args[args.length - 1]);
    }
}
