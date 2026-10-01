package fr.duels.command;

import fr.duels.DuelsPlugin;
import fr.duels.arena.Arena;
import fr.duels.gui.ArenaSelectMenu;
import fr.duels.gui.KitSelectMenu;
import fr.duels.gui.PartyListMenu;
import fr.duels.gui.PartyMenu;
import fr.duels.kit.Kit;
import fr.duels.match.MatchType;
import fr.duels.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PartyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "create", "invite", "accept", "leave", "kick", "promote", "disband", "open", "info", "chat",
            "ffa", "split", "duel", "list", "help");

    private final DuelsPlugin plugin;

    public PartyCommand(DuelsPlugin plugin) {
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
            new PartyMenu(plugin).open(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create":
                plugin.getPartyManager().create(player);
                break;
            case "invite":
            case "inv": {
                if (args.length < 2) {
                    Lang.send(player, "party.usage");
                    break;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    Lang.send(player, "general.player-not-found", "player", args[1]);
                    break;
                }
                plugin.getPartyManager().invite(player, target);
                break;
            }
            case "accept":
            case "join":
                if (args.length < 2) {
                    Lang.send(player, "party.usage");
                    break;
                }
                plugin.getPartyManager().join(player, args[1]);
                break;
            case "leave":
                plugin.getPartyManager().leave(player);
                break;
            case "kick":
                if (args.length < 2) {
                    Lang.send(player, "party.usage");
                    break;
                }
                plugin.getPartyManager().kick(player, args[1]);
                break;
            case "promote":
            case "leader":
                if (args.length < 2) {
                    Lang.send(player, "party.usage");
                    break;
                }
                plugin.getPartyManager().promote(player, args[1]);
                break;
            case "disband":
                plugin.getPartyManager().disband(player);
                break;
            case "open":
            case "close":
            case "public":
                plugin.getPartyManager().toggleOpen(player);
                break;
            case "info":
                plugin.getPartyManager().info(player);
                break;
            case "list":
                new PartyListMenu(plugin).open(player);
                break;
            case "chat":
            case "c":
                if (args.length < 2) {
                    Lang.send(player, "party.usage");
                    break;
                }
                plugin.getPartyManager().chat(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                break;
            case "ffa":
                fight(player, MatchType.PARTY_FFA, args);
                break;
            case "split":
            case "teams":
                fight(player, MatchType.PARTY_SPLIT, args);
                break;
            case "duel":
            case "fight":
                if (args.length < 2) {
                    new PartyListMenu(plugin).open(player);
                    break;
                }
                player.performCommand("duel " + String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                break;
            default:
                for (String line : Lang.list("party.help")) {
                    player.sendMessage(line);
                }
        }
        return true;
    }

    private void fight(Player player, final MatchType type, String[] args) {
        if (args.length >= 2) {
            Kit kit = plugin.getKitManager().get(args[1]);
            if (kit == null || !kit.isEnabled()) {
                Lang.send(player, "general.kit-not-found", "kit", args[1]);
                return;
            }
            Arena arena = args.length >= 3 ? plugin.getArenaManager().get(args[2]) : null;
            if (args.length >= 3 && arena == null) {
                Lang.send(player, "general.arena-not-found", "arena", args[2]);
                return;
            }
            plugin.getPartyManager().startPartyFight(player, type, kit, arena);
            return;
        }
        new KitSelectMenu(plugin, Lang.raw("menu.kit-select.title"), (p, kit) ->
                ArenaSelectMenu.openOrSkip(plugin, p, kit, (p2, arena) ->
                        plugin.getPartyManager().startPartyFight(p2, type, kit, arena))).open(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(SUBS);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("ffa") || sub.equals("split")) {
                for (Kit kit : plugin.getKitManager().getEnabledKits()) {
                    out.add(kit.getName());
                }
            } else {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    out.add(online.getName());
                }
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("ffa") || args[0].equalsIgnoreCase("split"))) {
            for (Arena arena : plugin.getArenaManager().getArenas()) {
                out.add(arena.getName());
            }
        }
        return Completions.filter(out, args[args.length - 1]);
    }
}
