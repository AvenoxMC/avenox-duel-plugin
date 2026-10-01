package fr.duels.schematic;

import fr.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Recherche des schématiques dans les dossiers du serveur, chargement asynchrone
 * et collage progressif (quelques dizaines de milliers de blocs par tick).
 */
public class SchematicManager {

    private final DuelsPlugin plugin;
    private final Set<String> pastingWorlds = new HashSet<>();
    private LegacyMapper mapper;

    public SchematicManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        new File(plugin.getDataFolder(), "schematics").mkdirs();
        try (InputStream in = plugin.getResource("legacy-blocks.txt")) {
            mapper = new LegacyMapper(in);
        } catch (IOException | NullPointerException e) {
            plugin.getLogger().severe("Table de conversion des blocs introuvable : les .schem ne pourront pas être chargés.");
        }
    }

    // ------------------------------------------------------------------
    // Fichiers
    // ------------------------------------------------------------------

    public List<File> getFolders() {
        List<File> folders = new ArrayList<>();
        File serverRoot = Bukkit.getWorldContainer().getAbsoluteFile();
        for (String path : plugin.getConfig().getStringList("schematics.folders")) {
            File folder = new File(path);
            if (!folder.isAbsolute()) {
                folder = new File(serverRoot, path);
            }
            if (folder.isDirectory()) {
                folders.add(folder);
            }
        }
        return folders;
    }

    /** Noms affichables (chemin relatif au dossier, avec extension) de toutes les schématiques. */
    public List<String> list() {
        List<String> names = new ArrayList<>();
        for (File folder : getFolders()) {
            collect(folder, "", names, 0);
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private void collect(File folder, String prefix, List<String> out, int depth) {
        File[] files = folder.listFiles();
        if (files == null || depth > 4) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collect(file, prefix + file.getName() + "/", out, depth + 1);
            } else if (isSchematic(file)) {
                String name = prefix + file.getName();
                if (!out.contains(name)) {
                    out.add(name);
                }
            }
        }
    }

    private static boolean isSchematic(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".schem") || name.endsWith(".schematic");
    }

    public static boolean looksLikeSchematic(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".schem") || lower.endsWith(".schematic");
    }

    /** Trouve un fichier par nom, avec ou sans extension (ex : "arene1", "arene1.schem", "duels/arene1"). */
    public File find(String name) {
        String clean = name.replace('\\', '/');
        if (clean.contains("..")) {
            return null;
        }
        for (File folder : getFolders()) {
            for (String candidate : new String[]{clean, clean + ".schem", clean + ".schematic"}) {
                File file = new File(folder, candidate);
                if (file.isFile() && isSchematic(file)) {
                    return file;
                }
            }
        }
        // insensible à la casse
        for (String listed : list()) {
            String withoutExt = listed.replaceAll("(?i)\\.schem(atic)?$", "");
            if (listed.equalsIgnoreCase(clean) || withoutExt.equalsIgnoreCase(clean)) {
                for (File folder : getFolders()) {
                    File file = new File(folder, listed);
                    if (file.isFile()) {
                        return file;
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Chargement / collage
    // ------------------------------------------------------------------

    /** Lit et convertit la schématique hors du thread principal, puis rappelle sur le thread principal. */
    public void loadAsync(final File file, final Consumer<Schematic> onLoaded, final Consumer<String> onError) {
        if (mapper == null) {
            onError.accept("table de conversion absente");
            return;
        }
        final long maxVolume = plugin.getConfig().getLong("schematics.max-volume", 16000000L);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Schematic schematic = null;
            String error = null;
            try {
                synchronized (mapper) { // le rapport des blocs approximés est propre à chaque chargement
                    schematic = Schematic.load(file, mapper, maxVolume);
                }
            } catch (IOException | RuntimeException e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            } catch (OutOfMemoryError e) {
                error = "mémoire insuffisante";
            }
            final Schematic result = schematic;
            final String failure = error;
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result != null) {
                    onLoaded.accept(result);
                } else {
                    onError.accept(failure);
                }
            });
        });
    }

    public boolean isPasting(World world) {
        return pastingWorlds.contains(world.getName());
    }

    /**
     * Colle la schématique avec son coin minimum en (minX, minY, minZ), chunk par chunk.
     * L'air n'est pas collé (les mondes d'arène sont vides).
     *
     * @param progress reçoit le pourcentage d'avancement (environ toutes les 2 secondes)
     * @param done     appelé à la fin avec le nombre de blocs posés
     */
    public void paste(final Schematic schematic, final World world, final int minX, final int minY, final int minZ,
                      final IntConsumer progress, final IntConsumer done) {
        final int perTick = Math.max(1000, plugin.getConfig().getInt("schematics.blocks-per-tick", 40000));
        final int width = schematic.getWidth();
        final int height = schematic.getHeight();
        final int length = schematic.getLength();
        final int maxY = Math.min(255, minY + height - 1);

        // colonnes de chunks couvertes, traitées une par une pour ne charger chaque chunk qu'une fois
        final List<int[]> columns = new ArrayList<>();
        for (int cx = minX >> 4; cx <= (minX + width - 1) >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= (minZ + length - 1) >> 4; cz++) {
                columns.add(new int[]{cx, cz});
            }
        }
        pastingWorlds.add(world.getName());

        new BukkitRunnable() {
            private int column;
            private int placed;
            private int ticks;

            @Override
            @SuppressWarnings("deprecation") // setTypeIdAndData : seul moyen de poser id + data en 1.8
            public void run() {
                if (Bukkit.getWorld(world.getName()) == null) {
                    pastingWorlds.remove(world.getName());
                    cancel();
                    return;
                }
                int budget = perTick;
                while (budget > 0 && column < columns.size()) {
                    int[] chunk = columns.get(column++);
                    int fromX = Math.max(minX, chunk[0] << 4);
                    int toX = Math.min(minX + width - 1, (chunk[0] << 4) + 15);
                    int fromZ = Math.max(minZ, chunk[1] << 4);
                    int toZ = Math.min(minZ + length - 1, (chunk[1] << 4) + 15);
                    for (int y = Math.max(0, minY); y <= maxY; y++) {
                        for (int z = fromZ; z <= toZ; z++) {
                            for (int x = fromX; x <= toX; x++) {
                                int block = schematic.get(x - minX, y - minY, z - minZ);
                                if (block != LegacyMapper.AIR) {
                                    world.getBlockAt(x, y, z).setTypeIdAndData(block >> 4, (byte) (block & 0xF), false);
                                    placed++;
                                    budget--;
                                }
                            }
                        }
                    }
                }
                if (column >= columns.size()) {
                    pastingWorlds.remove(world.getName());
                    cancel();
                    done.accept(placed);
                    return;
                }
                if (++ticks % 40 == 0) {
                    progress.accept(column * 100 / columns.size());
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
