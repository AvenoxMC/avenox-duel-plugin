package fr.duels.command;

import fr.duels.DuelsPlugin;
import fr.duels.kit.Kit;
import fr.duels.kit.KitManager;
import fr.duels.util.Lang;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration des kits en jeu : on s'équipe comme on veut puis /dkit create <nom>.
 * Les kits restent aussi modifiables à la main dans kits.yml (/duels reload).
 */
public class KitCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "create", "setinv", "load", "seticon", "setname", "set", "delete", "list", "info");

    private final DuelsPlugin plugin;

    public KitCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    private KitManager kits() {
        return plugin.getKitManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("duels.admin")) {
            Lang.send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            for (String line : Lang.list("kit.help")) {
                sender.sendMessage(line);
            }
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("list")) {
            List<String> names = new ArrayList<>();
            for (Kit kit : kits().getKits()) {
                names.add((kit.isEnabled() ? "&a" : "&c") + kit.getName());
            }
            Lang.send(sender, "kit.list", "count", names.size(), "kits", Lang.color(String.join("&7, ", names)));
            return true;
        }
        if (args.length < 2) {
            Lang.send(sender, "kit.usage");
            return true;
        }
        String name = args[1].toLowerCase();

        if (sub.equals("create")) {
            if (!(sender instanceof Player)) {
                Lang.send(sender, "general.player-only");
                return true;
            }
            if (!name.matches("[a-z0-9_-]{1,32}")) {
                Lang.send(sender, "kit.invalid-name");
                return true;
            }
            if (kits().get(name) != null) {
                Lang.send(sender, "kit.already-exists", "kit", name);
                return true;
            }
            Kit kit = kits().create(name);
            kit.copyFrom((Player) sender);
            ItemStack hand = ((Player) sender).getItemInHand();
            if (hand != null && hand.getType() != Material.AIR) {
                kit.setIcon(new ItemStack(hand.getType(), 1, hand.getDurability()));
            }
            kits().save();
            Lang.send(sender, "kit.created", "kit", name);
            return true;
        }

        Kit kit = kits().get(name);
        if (kit == null) {
            Lang.send(sender, "general.kit-not-found", "kit", name);
            return true;
        }
        switch (sub) {
            case "setinv":
                if (!(sender instanceof Player)) {
                    Lang.send(sender, "general.player-only");
                    return true;
                }
                kit.copyFrom((Player) sender);
                kits().save();
                Lang.send(sender, "kit.inventory-saved", "kit", name);
                break;
            case "load":
                if (!(sender instanceof Player)) {
                    Lang.send(sender, "general.player-only");
                    return true;
                }
                if (plugin.getMatchManager().isBusy(((Player) sender).getUniqueId())) {
                    Lang.send(sender, "general.busy");
                    return true;
                }
                kit.apply((Player) sender);
                Lang.send(sender, "kit.loaded", "kit", name);
                break;
            case "seticon": {
                if (!(sender instanceof Player)) {
                    Lang.send(sender, "general.player-only");
                    return true;
                }
                ItemStack hand = ((Player) sender).getItemInHand();
                if (hand == null || hand.getType() == Material.AIR) {
                    Lang.send(sender, "kit.no-item-in-hand");
                    return true;
                }
                ItemStack icon = hand.clone();
                icon.setAmount(1);
                kit.setIcon(icon);
                kits().save();
                Lang.send(sender, "kit.icon-set", "kit", name);
                break;
            }
            case "setname":
                if (args.length < 3) {
                    Lang.send(sender, "kit.usage");
                    return true;
                }
                kit.setDisplayName(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                kits().save();
                Lang.send(sender, "kit.name-set", "kit", name, "display", kit.getDisplayName());
                break;
            case "set":
                if (args.length < 4) {
                    Lang.send(sender, "kit.set-usage", "settings", String.join(", ", KitManager.SETTINGS));
                    return true;
                }
                String setting = args[2].toLowerCase();
                if (!KitManager.SETTINGS.contains(setting) || !kits().applySetting(kit, setting, args[3])) {
                    Lang.send(sender, "kit.set-usage", "settings", String.join(", ", KitManager.SETTINGS));
                    return true;
                }
                kits().save();
                Lang.send(sender, "kit.setting-updated", "kit", name, "setting", setting, "value", args[3]);
                break;
            case "delete":
                kits().delete(name);
                kits().save();
                Lang.send(sender, "kit.deleted", "kit", name);
                break;
            case "info": {
                List<String> settings = new ArrayList<>();
                for (String s : KitManager.SETTINGS) {
                    settings.add("&7" + s + ": &f" + kits().getSetting(kit, s));
                }
                for (String line : Lang.list("kit.info", "kit", kit.getName(), "display", kit.getDisplayName(),
                        "effects", kit.getEffects().size(), "settings", Lang.color(String.join("&8, ", settings)))) {
                    sender.sendMessage(line);
                }
                break;
            }
            default:
                Lang.send(sender, "kit.usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(SUBS);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
            for (Kit kit : kits().getKits()) {
                out.add(kit.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            out.addAll(KitManager.SETTINGS);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            out.addAll(args[2].equalsIgnoreCase("hit-delay") ? Arrays.asList("20", "10", "2") : Arrays.asList("true", "false"));
        }
        return Completions.filter(out, args[args.length - 1]);
    }
}
