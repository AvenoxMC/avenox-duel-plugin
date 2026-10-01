package fr.duels.lobby;

import fr.duels.DuelsPlugin;
import fr.duels.util.ItemBuilder;
import fr.duels.util.PlayerUtil;
import fr.duels.util.YamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LobbyManager {

    private final DuelsPlugin plugin;
    private final Map<String, ItemStack> items = new LinkedHashMap<>();
    private final Map<String, Integer> slots = new LinkedHashMap<>();
    private Location lobby;

    public LobbyManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    private File locationFile() {
        return new File(plugin.getDataFolder(), "lobby.yml");
    }

    public void load() {
        lobby = parseLocation(YamlFiles.load(locationFile()).getString("location", ""));
        items.clear();
        slots.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("lobby.items");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null || !s.getBoolean("enabled", true)) {
                continue;
            }
            Material material = Material.matchMaterial(s.getString("material", "STONE"));
            if (material == null) {
                plugin.getLogger().warning("Matériau invalide pour l'objet de lobby " + key);
                continue;
            }
            items.put(key, new ItemBuilder(material, 1, (short) s.getInt("data", 0))
                    .name(s.getString("name", key))
                    .lore(s.getStringList("lore"))
                    .hideFlags()
                    .build());
            slots.put(key, s.getInt("slot", 0));
        }
    }

    public Location getLobby() {
        if (lobby == null || lobby.getWorld() == null) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return lobby;
    }

    public void setLobby(Location location) {
        this.lobby = location.clone();
        YamlConfiguration data = new YamlConfiguration();
        data.set("location", String.format(Locale.ROOT, "%s,%.2f,%.2f,%.2f,%.1f,%.1f",
                location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()));
        YamlFiles.save(data, locationFile());
    }

    public GameMode getLobbyGameMode() {
        try {
            return GameMode.valueOf(plugin.getConfig().getString("lobby.gamemode", "ADVENTURE").toUpperCase());
        } catch (IllegalArgumentException e) {
            return GameMode.ADVENTURE;
        }
    }

    /** Réinitialise le joueur, le téléporte au lobby et lui donne les objets du lobby. */
    public void sendToLobby(Player player) {
        PlayerUtil.reset(player, getLobbyGameMode());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        player.teleport(getLobby());
        giveItems(player);
    }

    /** Hotbar du lobby, qui dépend de l'état du joueur (seul, en file, en party). */
    public void giveItems(Player player) {
        if (!plugin.getConfig().getBoolean("lobby.give-items", true)) {
            return;
        }
        List<String> keys;
        if (plugin.getPartyManager().getParty(player.getUniqueId()) != null) {
            keys = Arrays.asList("party-menu", "spectate", "party-leave");
        } else if (plugin.getQueueManager().isQueued(player.getUniqueId())) {
            keys = Arrays.asList("queue", "spectate", "party-create", "leave-queue");
        } else {
            keys = Arrays.asList("queue", "spectate", "party-create", "stats");
        }
        player.getInventory().clear();
        for (String key : keys) {
            ItemStack item = items.get(key);
            if (item != null) {
                player.getInventory().setItem(slots.get(key), item.clone());
            }
        }
        player.updateInventory();
    }

    /** @return la clé de l'objet de lobby correspondant, ou null. */
    public String getAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            return null;
        }
        for (Map.Entry<String, ItemStack> entry : items.entrySet()) {
            ItemStack lobbyItem = entry.getValue();
            if (lobbyItem.getType() == item.getType()
                    && meta.getDisplayName().equals(lobbyItem.getItemMeta().getDisplayName())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Location parseLocation(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String[] p = text.split(",");
        if (p.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(p[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    p.length > 4 ? Float.parseFloat(p[4]) : 0f, p.length > 5 ? Float.parseFloat(p[5]) : 0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
