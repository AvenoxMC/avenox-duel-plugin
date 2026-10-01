package fr.duels;

import fr.duels.arena.Arena;
import fr.duels.arena.ArenaManager;
import fr.duels.arena.VoidGenerator;
import fr.duels.arena.WorldService;
import fr.duels.command.ArenaCommand;
import fr.duels.command.DuelCommand;
import fr.duels.command.DuelsCommand;
import fr.duels.command.KitCommand;
import fr.duels.command.PartyCommand;
import fr.duels.command.PlayerCommands;
import fr.duels.duel.RequestManager;
import fr.duels.gui.MenuListener;
import fr.duels.kit.KitManager;
import fr.duels.listener.MatchListener;
import fr.duels.listener.PlayerListener;
import fr.duels.listener.WorldListener;
import fr.duels.lobby.LobbyManager;
import fr.duels.match.MatchManager;
import fr.duels.party.PartyManager;
import fr.duels.queue.QueueManager;
import fr.duels.schematic.SchematicManager;
import fr.duels.stats.StatsManager;
import fr.duels.util.Lang;
import fr.duels.util.YamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class DuelsPlugin extends JavaPlugin {

    private YamlConfiguration config;
    private WorldService worldService;
    private KitManager kitManager;
    private ArenaManager arenaManager;
    private StatsManager statsManager;
    private LobbyManager lobbyManager;
    private PartyManager partyManager;
    private RequestManager requestManager;
    private MatchManager matchManager;
    private QueueManager queueManager;
    private SchematicManager schematicManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Lang.load(this);

        worldService = new WorldService(this);
        worldService.cleanupLeftovers();

        kitManager = new KitManager(this);
        kitManager.load();
        arenaManager = new ArenaManager(this);
        arenaManager.load();
        statsManager = new StatsManager(this);
        statsManager.load();
        lobbyManager = new LobbyManager(this);
        lobbyManager.load();
        partyManager = new PartyManager(this);
        requestManager = new RequestManager(this);
        matchManager = new MatchManager(this);
        queueManager = new QueueManager(this);
        schematicManager = new SchematicManager(this);
        schematicManager.init();

        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MatchListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WorldListener(this), this);

        PlayerCommands playerCommands = new PlayerCommands(this);
        register("duel", new DuelCommand(this));
        register("party", new PartyCommand(this));
        register("queue", playerCommands);
        register("leave", playerCommands);
        register("spectate", playerCommands);
        register("stats", playerCommands);
        register("arena", new ArenaCommand(this));
        register("dkit", new KitCommand(this));
        register("duels", new DuelsCommand(this));

        queueManager.start();
        long saveInterval = getConfig().getLong("stats.save-interval", 300L) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, statsManager::save, saveInterval, saveInterval);

        // en cas de /reload : on remet les joueurs connectés au lobby
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (getConfig().getBoolean("lobby.teleport-on-join", true) || worldService.isDuelsWorld(player.getWorld().getName())) {
                lobbyManager.sendToLobby(player);
            } else {
                lobbyManager.giveItems(player);
            }
        }
        getLogger().info("Duels activé.");
    }

    @Override
    public void onDisable() {
        if (matchManager != null) {
            matchManager.shutdown();
        }
        if (arenaManager != null) {
            // les arènes en cours d'édition sont sauvegardées et déchargées
            for (Arena arena : arenaManager.getArenas()) {
                World world = Bukkit.getWorld(arena.getTemplateWorld());
                if (world != null) {
                    worldService.unloadTemplate(world, true);
                }
                arena.setEditing(false);
            }
            arenaManager.save();
        }
        if (statsManager != null) {
            statsManager.save();
        }
    }

    /** Recharge config.yml, messages.yml et kits.yml (les arènes se modifient via /arena). */
    public void reload() {
        reloadConfig();
        Lang.load(this);
        lobbyManager.load();
        kitManager.load();
    }

    private <T extends CommandExecutor & TabCompleter> void register(String name, T handler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Commande absente du plugin.yml : " + name);
            return;
        }
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    // config.yml lu/écrit en UTF-8 quel que soit le charset du système (accents dans les noms d'objets)
    @Override
    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    @Override
    public void reloadConfig() {
        config = YamlFiles.load(new File(getDataFolder(), "config.yml"));
        InputStream defaults = getResource("config.yml");
        if (defaults != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
    }

    @Override
    public void saveConfig() {
        YamlFiles.save(config, new File(getDataFolder(), "config.yml"));
    }

    /** Permet d'utiliser "Duels" comme générateur (bukkit.yml, Multiverse...) pour créer des mondes vides. */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidGenerator();
    }

    public WorldService getWorldService() {
        return worldService;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public RequestManager getRequestManager() {
        return requestManager;
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public SchematicManager getSchematicManager() {
        return schematicManager;
    }
}
