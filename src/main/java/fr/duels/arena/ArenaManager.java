package fr.duels.arena;

import fr.duels.DuelsPlugin;
import fr.duels.kit.Kit;
import fr.duels.util.SpawnPoint;
import fr.duels.util.YamlFiles;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ArenaManager {

    private final DuelsPlugin plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
    }

    public void load() {
        arenas.clear();
        YamlConfiguration config = YamlFiles.load(file);
        ConfigurationSection root = config.getConfigurationSection("arenas");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            Arena arena = new Arena(key, s.getString("template", plugin.getWorldService().getTemplatePrefix() + key));
            arena.setDisplayName(s.getString("display-name", "&f" + key));
            Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
            arena.setIcon(icon);
            for (String raw : s.getStringList("spawns")) {
                SpawnPoint point = SpawnPoint.parse(raw);
                if (point != null) {
                    arena.getSpawns().add(point);
                }
            }
            arena.setSpectatorSpawn(SpawnPoint.parse(s.getString("spectator", "")));
            for (String kit : s.getStringList("kits")) {
                arena.getKits().add(kit.toLowerCase());
            }
            arena.setMaxInstances(s.getInt("max-instances", plugin.getConfig().getInt("worlds.default-max-instances", 5)));
            arena.setEnabled(s.getBoolean("enabled", true));
            arena.setVoidY(s.getInt("void-y", 0));
            arena.setBuildLimitY(s.getInt("build-limit-y", 0));
            arenas.put(arena.getName(), arena);
            if (!plugin.getWorldService().getWorldFolder(arena.getTemplateWorld()).isDirectory()) {
                plugin.getLogger().warning("Le monde modèle de l'arène " + key + " (" + arena.getTemplateWorld() + ") est introuvable !");
            }
        }
        plugin.getLogger().info(arenas.size() + " arène(s) chargée(s).");
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            String path = "arenas." + arena.getName() + ".";
            config.set(path + "display-name", arena.getDisplayName());
            config.set(path + "template", arena.getTemplateWorld());
            config.set(path + "icon", arena.getIcon().name());
            List<String> spawns = new ArrayList<>();
            for (SpawnPoint point : arena.getSpawns()) {
                spawns.add(point.serialize());
            }
            config.set(path + "spawns", spawns);
            config.set(path + "spectator", arena.getSpectatorSpawn() == null ? "" : arena.getSpectatorSpawn().serialize());
            config.set(path + "kits", new ArrayList<>(arena.getKits()));
            config.set(path + "max-instances", arena.getMaxInstances());
            config.set(path + "enabled", arena.isEnabled());
            config.set(path + "void-y", arena.getVoidY());
            config.set(path + "build-limit-y", arena.getBuildLimitY());
        }
        YamlFiles.save(config, file);
    }

    public Arena get(String name) {
        return name == null ? null : arenas.get(name.toLowerCase());
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public Arena create(String name) {
        Arena arena = new Arena(name, plugin.getWorldService().getTemplatePrefix() + name.toLowerCase());
        arena.setMaxInstances(plugin.getConfig().getInt("worlds.default-max-instances", 5));
        arenas.put(arena.getName(), arena);
        return arena;
    }

    public void remove(Arena arena) {
        arenas.remove(arena.getName());
    }

    public int getTotalInstances() {
        int total = 0;
        for (Arena arena : arenas.values()) {
            total += arena.getActiveInstances();
        }
        return total;
    }

    private boolean hasGlobalCapacity() {
        int max = plugin.getConfig().getInt("worlds.max-loaded-instances", 30);
        return max <= 0 || getTotalInstances() < max;
    }

    public boolean templateExists(Arena arena) {
        return plugin.getWorldService().getWorldFolder(arena.getTemplateWorld()).isDirectory();
    }

    public boolean isAvailable(Arena arena, Kit kit) {
        return arena.isAvailable(kit) && templateExists(arena) && hasGlobalCapacity();
    }

    /** Arènes compatibles avec le kit (disponibles ou non), pour l'affichage. */
    public List<Arena> getArenasFor(Kit kit) {
        List<Arena> list = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (arena.isEnabled() && arena.isSetup() && arena.supports(kit)) {
                list.add(arena);
            }
        }
        return list;
    }

    /** Choisit au hasard une arène disponible pour ce kit, ou null s'il n'y en a aucune. */
    public Arena findAvailable(Kit kit) {
        if (!hasGlobalCapacity()) {
            return null;
        }
        List<Arena> available = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (isAvailable(arena, kit)) {
                available.add(arena);
            }
        }
        if (available.isEmpty()) {
            return null;
        }
        // Privilégie les arènes les moins utilisées pour répartir la charge.
        Collections.shuffle(available, ThreadLocalRandom.current());
        Arena best = available.get(0);
        for (Arena arena : available) {
            if (arena.getActiveInstances() < best.getActiveInstances()) {
                best = arena;
            }
        }
        return best;
    }
}
