package fr.duels.arena;

import fr.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Gère les mondes du plugin :
 *  - templates (duels_tpl_*)   : mondes modèles, déchargés sauf pendant l'édition ;
 *  - instances (duels_match_*) : copies jetables chargées le temps d'un match puis supprimées.
 */
public class WorldService {

    private static final Set<String> SKIPPED_FILES = new HashSet<>(Arrays.asList("uid.dat", "session.lock"));
    private static final Set<String> SKIPPED_DIRS = new HashSet<>(Arrays.asList("playerdata", "stats"));

    private final DuelsPlugin plugin;
    private final AtomicInteger counter = new AtomicInteger();

    public WorldService(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public String getTemplatePrefix() {
        return plugin.getConfig().getString("worlds.template-prefix", "duels_tpl_");
    }

    public String getInstancePrefix() {
        return plugin.getConfig().getString("worlds.instance-prefix", "duels_match_");
    }

    public boolean isTemplate(World world) {
        return world != null && world.getName().startsWith(getTemplatePrefix());
    }

    public boolean isInstance(World world) {
        return world != null && world.getName().startsWith(getInstancePrefix());
    }

    public boolean isDuelsWorld(String name) {
        return name.startsWith(getTemplatePrefix()) || name.startsWith(getInstancePrefix());
    }

    public File getWorldFolder(String name) {
        return new File(Bukkit.getWorldContainer(), name);
    }

    public String nextInstanceName(Arena arena) {
        return getInstancePrefix() + arena.getName() + "_" + counter.incrementAndGet();
    }

    // ------------------------------------------------------------------
    // Chargement
    // ------------------------------------------------------------------

    /** Crée un nouveau monde vide avec une petite plateforme de verre pour l'édition. */
    public World createVoidWorld(String name) {
        return createVoidWorld(name, true);
    }

    /** Crée un nouveau monde vide, avec ou sans plateforme de verre en 0 63 0. */
    public World createVoidWorld(String name, boolean platform) {
        World world = new WorldCreator(name)
                .generator(new VoidGenerator())
                .type(WorldType.FLAT)
                .generateStructures(false)
                .createWorld();
        configure(world, false);
        world.setSpawnLocation(0, 64, 0);
        if (!platform) {
            return world;
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block block = world.getBlockAt(x, 63, z);
                if (block.getType() == Material.AIR) {
                    block.setType(Material.GLASS);
                }
            }
        }
        return world;
    }

    /** Charge (ou récupère s'il est déjà chargé) un monde existant sur le disque. */
    public World loadWorld(String name, boolean instance) {
        World world = Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }
        world = new WorldCreator(name).generator(new VoidGenerator()).generateStructures(false).createWorld();
        if (world != null) {
            configure(world, instance);
        }
        return world;
    }

    private void configure(World world, boolean instance) {
        world.setKeepSpawnInMemory(false);
        world.setAutoSave(!instance);
        world.setPVP(true);
        world.setSpawnFlags(false, false);
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setTime(plugin.getConfig().getLong("worlds.time", 6000L));
        try {
            world.setDifficulty(Difficulty.valueOf(plugin.getConfig().getString("worlds.difficulty", "NORMAL").toUpperCase()));
        } catch (IllegalArgumentException e) {
            world.setDifficulty(Difficulty.NORMAL);
        }
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", String.valueOf(plugin.getConfig().getBoolean("worlds.fire-spread", false)));
        world.setGameRuleValue("mobGriefing", "false");
        world.setGameRuleValue("showDeathMessages", "false");
    }

    // ------------------------------------------------------------------
    // Copie / suppression
    // ------------------------------------------------------------------

    /** Copie un dossier de monde en asynchrone, puis rappelle {@code done} sur le thread principal. */
    public void copyWorldAsync(final File source, final String targetName, final Consumer<Boolean> done) {
        final File target = getWorldFolder(targetName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok;
            try {
                deleteRecursively(target);
                copyFolder(source.toPath(), target.toPath());
                ok = true;
            } catch (IOException e) {
                plugin.getLogger().severe("Copie du monde " + source.getName() + " -> " + targetName + " impossible : " + e);
                ok = false;
            }
            final boolean result = ok;
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> done.accept(result));
            }
        });
    }

    private static void copyFolder(final Path source, final Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            throw new IOException("Dossier introuvable : " + source);
        }
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(source) && SKIPPED_DIRS.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!SKIPPED_FILES.contains(file.getFileName().toString())) {
                    Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Décharge un monde (sans sauvegarder) puis supprime son dossier. */
    public void unloadAndDelete(World world) {
        if (world == null) {
            return;
        }
        evacuate(world);
        final File folder = world.getWorldFolder();
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().warning("Impossible de décharger " + world.getName() + " (il sera supprimé au prochain démarrage).");
            return;
        }
        deleteLater(folder, 3);
    }

    /** Variante synchrone utilisée à l'arrêt du serveur. */
    public void unloadAndDeleteNow(World world) {
        if (world == null) {
            return;
        }
        evacuate(world);
        File folder = world.getWorldFolder();
        if (Bukkit.unloadWorld(world, false)) {
            try {
                deleteRecursively(folder);
            } catch (IOException ignored) {
                // nettoyé au prochain démarrage
            }
        }
    }

    /** Décharge un template (en sauvegardant si demandé). */
    public boolean unloadTemplate(World world, boolean save) {
        evacuate(world);
        if (save) {
            world.save();
        }
        return Bukkit.unloadWorld(world, save);
    }

    private void evacuate(World world) {
        for (Player player : new ArrayList<>(world.getPlayers())) {
            plugin.getLobbyManager().sendToLobby(player);
            if (player.getWorld().equals(world)) {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        }
    }

    private void deleteLater(final File folder, final int attempts) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                deleteRecursively(folder);
            } catch (IOException e) {
                if (attempts > 1) {
                    deleteLater(folder, attempts - 1);
                } else {
                    plugin.getLogger().warning("Suppression de " + folder.getName() + " impossible : " + e.getMessage());
                }
            }
        }, 40L);
    }

    public void deleteAsync(final File folder) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                deleteRecursively(folder);
            } catch (IOException e) {
                plugin.getLogger().warning("Suppression de " + folder.getName() + " impossible : " + e.getMessage());
            }
        });
    }

    public static void deleteRecursively(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                Files.delete(path);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Supprime les copies de matchs restées sur le disque (crash, arrêt brutal...). */
    public void cleanupLeftovers() {
        File[] files = Bukkit.getWorldContainer().listFiles();
        if (files == null) {
            return;
        }
        int removed = 0;
        for (File file : files) {
            if (file.isDirectory() && file.getName().startsWith(getInstancePrefix()) && Bukkit.getWorld(file.getName()) == null) {
                try {
                    deleteRecursively(file);
                    removed++;
                } catch (IOException e) {
                    plugin.getLogger().warning("Impossible de supprimer l'ancienne instance " + file.getName());
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info(removed + " ancienne(s) instance(s) de match supprimée(s).");
        }
    }
}
