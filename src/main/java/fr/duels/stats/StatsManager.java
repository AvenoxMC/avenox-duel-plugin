package fr.duels.stats;

import fr.duels.DuelsPlugin;
import fr.duels.util.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatsManager {

    private final File file;
    private final Map<UUID, PlayerStats> stats = new HashMap<>();

    public StatsManager(DuelsPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    public void load() {
        stats.clear();
        YamlConfiguration config = YamlFiles.load(file);
        ConfigurationSection root = config.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection s = root.getConfigurationSection(key);
                stats.put(uuid, new PlayerStats(s.getString("name", "?"), s.getInt("wins"), s.getInt("losses"),
                        s.getInt("kills"), s.getInt("deaths")));
            } catch (IllegalArgumentException ignored) {
                // entrée invalide
            }
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerStats> entry : stats.entrySet()) {
            String path = "players." + entry.getKey() + ".";
            PlayerStats s = entry.getValue();
            config.set(path + "name", s.getName());
            config.set(path + "wins", s.getWins());
            config.set(path + "losses", s.getLosses());
            config.set(path + "kills", s.getKills());
            config.set(path + "deaths", s.getDeaths());
        }
        YamlFiles.save(config, file);
    }

    public PlayerStats get(Player player) {
        return get(player.getUniqueId(), player.getName());
    }

    public PlayerStats get(UUID uuid, String name) {
        PlayerStats s = stats.get(uuid);
        if (s == null) {
            s = new PlayerStats(name, 0, 0, 0, 0);
            stats.put(uuid, s);
        } else if (name != null && !name.equals("?")) {
            s.setName(name);
        }
        return s;
    }

    public PlayerStats find(String name) {
        for (PlayerStats s : stats.values()) {
            if (s.getName().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }
}
