package fr.duels.listener;

import fr.duels.DuelsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldInitEvent;

public class WorldListener implements Listener {

    private final DuelsPlugin plugin;

    public WorldListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Évite de charger ~400 chunks de spawn à chaque copie de map (gros gain de temps de chargement). */
    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        if (plugin.getWorldService().isDuelsWorld(event.getWorld().getName())) {
            event.getWorld().setKeepSpawnInMemory(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState() && plugin.getWorldService().isDuelsWorld(event.getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (plugin.getWorldService().isInstance(event.getLocation().getWorld())
                && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            event.setCancelled(true);
        }
    }
}
