package fr.duels.command;

import fr.duels.DuelsPlugin;
import fr.duels.arena.Arena;
import fr.duels.arena.ArenaManager;
import fr.duels.arena.WorldService;
import fr.duels.kit.Kit;
import fr.duels.schematic.Schematic;
import fr.duels.schematic.SchematicManager;
import fr.duels.util.Lang;
import fr.duels.util.SpawnPoint;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Création et configuration des arènes.
 * Workflow : /arena create <nom> [monde] -> construire -> /arena addspawn (x2+) -> /arena setspec -> /arena save
 */
public class ArenaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "create", "edit", "save", "addspawn", "setspawn", "delspawn", "clearspawns", "setspec",
            "kits", "set", "tp", "list", "info", "delete", "fromschem", "paste", "schematics");
    private static final List<String> SETTINGS = Arrays.asList(
            "displayname", "icon", "maxinstances", "voidy", "buildlimit", "enabled");

    private final DuelsPlugin plugin;

    public ArenaCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    private ArenaManager arenas() {
        return plugin.getArenaManager();
    }

    private WorldService worlds() {
        return plugin.getWorldService();
    }

    private SchematicManager schematics() {
        return plugin.getSchematicManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("duels.admin")) {
            Lang.send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            for (String line : Lang.list("arena.help")) {
                sender.sendMessage(line);
            }
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("list")) {
            list(sender);
            return true;
        }
        if (sub.equals("schematics") || sub.equals("schems")) {
            listSchematics(sender);
            return true;
        }
        if (args.length < 2) {
            Lang.send(sender, "arena.usage");
            return true;
        }
        if (sub.equals("create") || sub.equals("fromschem")) {
            if (!(sender instanceof Player)) {
                Lang.send(sender, "general.player-only");
                return true;
            }
            if (sub.equals("fromschem")) {
                if (args.length < 3) {
                    Lang.send(sender, "arena.schem-usage");
                    return true;
                }
                createFromSchematic((Player) sender, args[1].toLowerCase(), args[2]);
                return true;
            }
            create((Player) sender, args);
            return true;
        }

        Arena arena = arenas().get(args[1]);
        if (arena == null) {
            Lang.send(sender, "general.arena-not-found", "arena", args[1]);
            return true;
        }
        switch (sub) {
            case "info":
                info(sender, arena);
                return true;
            case "delete":
                delete(sender, arena, args);
                return true;
            case "kits":
                kits(sender, arena, args);
                return true;
            case "set":
                set(sender, arena, args);
                return true;
            case "save":
                save(sender, arena);
                return true;
            default:
                break;
        }

        if (!(sender instanceof Player)) {
            Lang.send(sender, "general.player-only");
            return true;
        }
        Player player = (Player) sender;
        switch (sub) {
            case "edit":
            case "tp":
                startEdit(player, arena);
                break;
            case "addspawn":
                if (checkWorld(player, arena)) {
                    arena.getSpawns().add(SpawnPoint.of(player.getLocation()));
                    arenas().save();
                    Lang.send(player, "arena.spawn-added", "index", arena.getSpawns().size(), "arena", arena.getName());
                }
                break;
            case "setspawn": {
                int index = parseIndex(player, args, arena.getSpawns().size() + 1);
                if (index < 0 || !checkWorld(player, arena)) {
                    break;
                }
                SpawnPoint point = SpawnPoint.of(player.getLocation());
                if (index == arena.getSpawns().size()) {
                    arena.getSpawns().add(point);
                } else {
                    arena.getSpawns().set(index, point);
                }
                arenas().save();
                Lang.send(player, "arena.spawn-set", "index", index + 1, "arena", arena.getName());
                break;
            }
            case "delspawn": {
                int index = parseIndex(player, args, arena.getSpawns().size());
                if (index < 0) {
                    break;
                }
                arena.getSpawns().remove(index);
                arenas().save();
                Lang.send(player, "arena.spawn-removed", "index", index + 1, "arena", arena.getName());
                break;
            }
            case "clearspawns":
                arena.getSpawns().clear();
                arenas().save();
                Lang.send(player, "arena.spawns-cleared", "arena", arena.getName());
                break;
            case "setspec":
                if (checkWorld(player, arena)) {
                    arena.setSpectatorSpawn(SpawnPoint.of(player.getLocation()));
                    arenas().save();
                    Lang.send(player, "arena.spec-set", "arena", arena.getName());
                }
                break;
            case "paste":
                if (args.length < 3) {
                    Lang.send(player, "arena.paste-usage");
                } else if (checkWorld(player, arena)) {
                    pasteHere(player, args[2]);
                }
                break;
            default:
                Lang.send(player, "arena.usage");
        }
        return true;
    }

    // ------------------------------------------------------------------

    private boolean canCreate(Player player, String name) {
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            Lang.send(player, "arena.invalid-name");
            return false;
        }
        if (arenas().get(name) != null) {
            Lang.send(player, "arena.already-exists", "arena", name);
            return false;
        }
        if (plugin.getMatchManager().isBusy(player.getUniqueId())) {
            Lang.send(player, "general.busy");
            return false;
        }
        return true;
    }

    private void create(final Player player, String[] args) {
        final String name = args[1].toLowerCase();
        if (!canCreate(player, name)) {
            return;
        }
        // "/arena create <nom> <fichier.schem>" : création depuis une schématique
        if (args.length >= 3 && (SchematicManager.looksLikeSchematic(args[2])
                || (!worlds().getWorldFolder(args[2]).isDirectory() && schematics().find(args[2]) != null))) {
            createFromSchematic(player, name, args[2]);
            return;
        }
        final Arena arena = arenas().create(name);
        File templateFolder = worlds().getWorldFolder(arena.getTemplateWorld());

        if (args.length >= 3) {
            // copie d'un monde existant comme modèle
            String sourceName = args[2];
            File source = worlds().getWorldFolder(sourceName);
            if (!source.isDirectory()) {
                arenas().remove(arena);
                Lang.send(player, "arena.source-not-found", "world", sourceName);
                return;
            }
            World loaded = Bukkit.getWorld(sourceName);
            if (loaded != null) {
                loaded.save();
            }
            Lang.send(player, "arena.copying", "world", sourceName);
            worlds().copyWorldAsync(source, arena.getTemplateWorld(), ok -> {
                if (!ok) {
                    arenas().remove(arena);
                    Lang.send(player, "arena.copy-failed");
                    return;
                }
                arenas().save();
                Lang.send(player, "arena.created", "arena", name);
                if (player.isOnline()) {
                    startEdit(player, arena);
                }
            });
            return;
        }

        if (templateFolder.isDirectory()) {
            Lang.send(player, "arena.template-reused", "world", arena.getTemplateWorld());
        } else {
            worlds().createVoidWorld(arena.getTemplateWorld());
        }
        arenas().save();
        Lang.send(player, "arena.created", "arena", name);
        startEdit(player, arena);
    }

    private void startEdit(Player player, Arena arena) {
        if (plugin.getMatchManager().isBusy(player.getUniqueId())) {
            Lang.send(player, "general.busy");
            return;
        }
        if (!arenas().templateExists(arena)) {
            Lang.send(player, "arena.template-missing", "world", arena.getTemplateWorld());
            return;
        }
        World world = worlds().loadWorld(arena.getTemplateWorld(), false);
        if (world == null) {
            Lang.send(player, "arena.template-missing", "world", arena.getTemplateWorld());
            return;
        }
        arena.setEditing(true);
        plugin.getQueueManager().leave(player, false);
        Location target;
        if (arena.getSpectatorSpawn() != null) {
            target = arena.getSpectatorSpawn().toLocation(world);
        } else if (!arena.getSpawns().isEmpty()) {
            target = arena.getSpawns().get(0).toLocation(world);
        } else {
            target = world.getSpawnLocation().add(0.5, 0, 0.5);
        }
        player.teleport(target);
        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().clear();
        for (String line : Lang.list("arena.edit-help", "arena", arena.getName())) {
            player.sendMessage(line);
        }
    }

    // ------------------------------------------------------------------
    // Schématiques
    // ------------------------------------------------------------------

    private void listSchematics(CommandSender sender) {
        List<String> names = schematics().list();
        List<String> folders = new ArrayList<>();
        for (File folder : schematics().getFolders()) {
            folders.add(folder.getPath());
        }
        Lang.send(sender, "arena.schem-list-header", "count", names.size(),
                "folders", folders.isEmpty() ? "-" : String.join(", ", folders));
        for (String name : names) {
            sender.sendMessage(Lang.get("arena.schem-list-line", "file", name));
        }
        if (names.isEmpty()) {
            Lang.send(sender, "arena.schem-none");
        }
    }

    /**
     * Crée un monde vide, y colle la schématique centrée en 0 / paste-y / 0,
     * puis passe l'arène en édition pour placer les spawns.
     */
    private void createFromSchematic(final Player player, final String name, String fileName) {
        if (!canCreate(player, name)) {
            return;
        }
        final File file = schematics().find(fileName);
        if (file == null) {
            Lang.send(player, "arena.schem-not-found", "file", fileName);
            return;
        }
        if (worlds().getWorldFolder(worlds().getTemplatePrefix() + name).exists()) {
            Lang.send(player, "arena.template-exists", "world", worlds().getTemplatePrefix() + name);
            return;
        }
        Lang.send(player, "arena.schem-loading", "file", file.getName());
        schematics().loadAsync(file, schematic -> {
            if (arenas().get(name) != null) {
                Lang.send(player, "arena.already-exists", "arena", name);
                return;
            }
            final Arena arena = arenas().create(name);
            final World world = worlds().createVoidWorld(arena.getTemplateWorld(), false);
            arena.setEditing(true);

            final int height = schematic.getHeight();
            final int pasteY = Math.max(1, Math.min(plugin.getConfig().getInt("schematics.paste-y", 64), 256 - height));
            final int minX = -schematic.getWidth() / 2;
            final int minZ = -schematic.getLength() / 2;
            arena.setVoidY(Math.max(0, pasteY - plugin.getConfig().getInt("schematics.void-below", 5)));
            arenas().save();

            reportApproximations(player, schematic);
            Lang.send(player, "arena.schem-pasting", "size",
                    schematic.getWidth() + "x" + height + "x" + schematic.getLength());
            schematics().paste(schematic, world, minX, pasteY, minZ,
                    percent -> {
                        if (player.isOnline()) {
                            Lang.send(player, "arena.schem-progress", "percent", percent);
                        }
                    },
                    placed -> {
                        world.setSpawnLocation(0, Math.min(255, pasteY + height + 1), 0);
                        world.save();
                        Lang.send(player, "arena.schem-done", "arena", name, "blocks", placed);
                        if (player.isOnline()) {
                            startEdit(player, arena);
                            player.setFlying(true);
                        }
                    });
        }, error -> Lang.send(player, "arena.schem-error", "file", file.getName(), "error", error));
    }

    /** Colle une schématique à la position du joueur (comme //paste) dans le monde de l'arène. */
    private void pasteHere(final Player player, String fileName) {
        final File file = schematics().find(fileName);
        if (file == null) {
            Lang.send(player, "arena.schem-not-found", "file", fileName);
            return;
        }
        final World world = player.getWorld();
        if (schematics().isPasting(world)) {
            Lang.send(player, "arena.schem-busy");
            return;
        }
        final Location origin = player.getLocation().getBlock().getLocation();
        Lang.send(player, "arena.schem-loading", "file", file.getName());
        schematics().loadAsync(file, schematic -> {
            if (Bukkit.getWorld(world.getName()) == null) {
                return;
            }
            int[] offset = schematic.getOffset();
            reportApproximations(player, schematic);
            schematics().paste(schematic, world, origin.getBlockX() + offset[0], origin.getBlockY() + offset[1],
                    origin.getBlockZ() + offset[2],
                    percent -> {
                        if (player.isOnline()) {
                            Lang.send(player, "arena.schem-progress", "percent", percent);
                        }
                    },
                    placed -> Lang.send(player, "arena.schem-pasted", "blocks", placed));
        }, error -> Lang.send(player, "arena.schem-error", "file", file.getName(), "error", error));
    }

    private void reportApproximations(Player player, Schematic schematic) {
        if (!schematic.getApproximated().isEmpty()) {
            Lang.send(player, "arena.schem-approximated", "count", schematic.getApproximated().size(),
                    "blocks", String.join(", ", schematic.describeApproximated(8)));
        }
    }

    private void save(CommandSender sender, Arena arena) {
        World world = Bukkit.getWorld(arena.getTemplateWorld());
        if (world != null && schematics().isPasting(world)) {
            Lang.send(sender, "arena.schem-busy");
            return;
        }
        if (world != null) {
            if (!worlds().unloadTemplate(world, true)) {
                Lang.send(sender, "arena.unload-failed");
                return;
            }
        }
        arena.setEditing(false);
        arenas().save();
        Lang.send(sender, "arena.saved", "arena", arena.getName());
        if (!arena.isSetup()) {
            Lang.send(sender, "arena.not-ready", "spawns", arena.getSpawns().size());
        }
    }

    private void delete(CommandSender sender, Arena arena, String[] args) {
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            Lang.send(sender, "arena.delete-confirm", "arena", arena.getName());
            return;
        }
        if (arena.getActiveInstances() > 0) {
            Lang.send(sender, "arena.in-use");
            return;
        }
        World world = Bukkit.getWorld(arena.getTemplateWorld());
        if (world != null) {
            worlds().unloadTemplate(world, false);
        }
        arenas().remove(arena);
        arenas().save();
        worlds().deleteAsync(worlds().getWorldFolder(arena.getTemplateWorld()));
        Lang.send(sender, "arena.deleted", "arena", arena.getName());
    }

    private void kits(CommandSender sender, Arena arena, String[] args) {
        if (args.length < 3) {
            Lang.send(sender, "arena.kits-usage");
            return;
        }
        String action = args[2].toLowerCase();
        if (action.equals("clear")) {
            arena.getKits().clear();
            arenas().save();
            Lang.send(sender, "arena.kits-cleared", "arena", arena.getName());
            return;
        }
        if (args.length < 4) {
            Lang.send(sender, "arena.kits-usage");
            return;
        }
        Kit kit = plugin.getKitManager().get(args[3]);
        if (kit == null) {
            Lang.send(sender, "general.kit-not-found", "kit", args[3]);
            return;
        }
        if (action.equals("add")) {
            arena.getKits().add(kit.getName());
        } else if (action.equals("remove")) {
            arena.getKits().remove(kit.getName());
        } else {
            Lang.send(sender, "arena.kits-usage");
            return;
        }
        arenas().save();
        Lang.send(sender, "arena.kits-updated", "arena", arena.getName(),
                "kits", arena.getKits().isEmpty() ? Lang.get("arena.all-kits") : String.join(", ", arena.getKits()));
    }

    private void set(CommandSender sender, Arena arena, String[] args) {
        if (args.length < 3) {
            Lang.send(sender, "arena.set-usage");
            return;
        }
        String setting = args[2].toLowerCase();
        String value = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        try {
            switch (setting) {
                case "displayname":
                    if (value == null) {
                        throw new IllegalArgumentException();
                    }
                    arena.setDisplayName(value);
                    break;
                case "icon": {
                    Material material;
                    if (value != null) {
                        material = Material.matchMaterial(value);
                    } else if (sender instanceof Player) {
                        ItemStack hand = ((Player) sender).getItemInHand();
                        material = hand == null ? null : hand.getType();
                    } else {
                        material = null;
                    }
                    if (material == null || material == Material.AIR) {
                        throw new IllegalArgumentException();
                    }
                    arena.setIcon(material);
                    break;
                }
                case "maxinstances":
                    arena.setMaxInstances(Integer.parseInt(value));
                    break;
                case "voidy":
                    arena.setVoidY(Integer.parseInt(value));
                    break;
                case "buildlimit":
                    arena.setBuildLimitY(Integer.parseInt(value));
                    break;
                case "enabled":
                    if (value == null || !(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))) {
                        throw new IllegalArgumentException();
                    }
                    arena.setEnabled(Boolean.parseBoolean(value));
                    break;
                default:
                    Lang.send(sender, "arena.set-usage");
                    return;
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            Lang.send(sender, "general.invalid-value");
            return;
        }
        arenas().save();
        Lang.send(sender, "arena.setting-updated", "arena", arena.getName(), "setting", setting);
    }

    private void list(CommandSender sender) {
        Lang.send(sender, "arena.list-header", "count", arenas().getArenas().size());
        for (Arena arena : arenas().getArenas()) {
            String status;
            if (arena.isEditing()) {
                status = Lang.get("arena.status-editing");
            } else if (!arena.isEnabled()) {
                status = Lang.get("arena.status-disabled");
            } else if (!arena.isSetup() || !arenas().templateExists(arena)) {
                status = Lang.get("arena.status-incomplete");
            } else {
                status = Lang.get("arena.status-ready");
            }
            sender.sendMessage(Lang.get("arena.list-line", "arena", arena.getName(), "display", arena.getDisplayName(),
                    "status", status, "active", arena.getActiveInstances(), "max", arena.getMaxInstances()));
        }
    }

    private void info(CommandSender sender, Arena arena) {
        List<String> spawns = new ArrayList<>();
        for (int i = 0; i < arena.getSpawns().size(); i++) {
            spawns.add("#" + (i + 1) + " " + arena.getSpawns().get(i));
        }
        for (String line : Lang.list("arena.info",
                "arena", arena.getName(),
                "display", arena.getDisplayName(),
                "world", arena.getTemplateWorld(),
                "spawns", spawns.isEmpty() ? "-" : String.join(", ", spawns),
                "spectator", arena.getSpectatorSpawn() == null ? "-" : arena.getSpectatorSpawn().toString(),
                "kits", arena.getKits().isEmpty() ? Lang.get("arena.all-kits") : String.join(", ", arena.getKits()),
                "active", arena.getActiveInstances(),
                "max", arena.getMaxInstances(),
                "voidy", arena.getVoidY(),
                "buildlimit", arena.getBuildLimitY() <= 0 ? "-" : String.valueOf(arena.getBuildLimitY()),
                "enabled", Lang.get(arena.isEnabled() ? "general.answer-yes" : "general.answer-no"),
                "editing", Lang.get(arena.isEditing() ? "general.answer-yes" : "general.answer-no"))) {
            sender.sendMessage(line);
        }
    }

    private boolean checkWorld(Player player, Arena arena) {
        if (!player.getWorld().getName().equals(arena.getTemplateWorld())) {
            Lang.send(player, "arena.wrong-world", "arena", arena.getName());
            return false;
        }
        return true;
    }

    /** Lit l'index (1-based) en args[2] ; retourne l'index 0-based ou -1. */
    private int parseIndex(Player player, String[] args, int max) {
        if (args.length < 3) {
            Lang.send(player, "arena.usage");
            return -1;
        }
        try {
            int index = Integer.parseInt(args[2]) - 1;
            if (index < 0 || index >= max) {
                throw new NumberFormatException();
            }
            return index;
        } catch (NumberFormatException e) {
            Lang.send(player, "general.invalid-value");
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(SUBS);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("create") && !args[0].equalsIgnoreCase("fromschem")) {
            for (Arena arena : arenas().getArenas()) {
                out.add(arena.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            for (World world : Bukkit.getWorlds()) {
                out.add(world.getName());
            }
            out.addAll(schematics().list());
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("fromschem") || args[0].equalsIgnoreCase("paste"))) {
            out.addAll(schematics().list());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("kits")) {
            out.addAll(Arrays.asList("add", "remove", "clear"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            out.addAll(SETTINGS);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("delete")) {
            out.add("confirm");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("kits")) {
            for (Kit kit : plugin.getKitManager().getKits()) {
                out.add(kit.getName());
            }
        }
        return Completions.filter(out, args[args.length - 1]);
    }
}
