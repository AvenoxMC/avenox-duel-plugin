package fr.duels.schematic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Convertit un état de bloc moderne ("minecraft:oak_stairs[facing=east,half=bottom]")
 * en bloc 1.8 encodé (id << 4 | data).
 *
 * 1. correspondance exacte dans la table de WorldEdit (legacy-blocks.txt) ;
 * 2. même bloc, propriétés les plus proches (ex : "waterlogged" ajouté après la 1.12) ;
 * 3. renommages et blocs récents remplacés par un équivalent proche ;
 * 4. les ids 1.9 -> 1.12 inexistants en 1.8 sont remplacés (béton -> laine, etc.).
 */
public final class LegacyMapper {

    public static final int AIR = 0;
    private static final int STONE = 1 << 4;
    /** Dernier id de bloc existant en 1.8 (dark_oak_door). */
    private static final int MAX_ID_18 = 197;

    private static final List<String> WOOD_SPECIES = Arrays.asList(
            "pale_oak_", "mangrove_", "cherry_", "bamboo_", "crimson_", "warped_");
    private static final List<String> WOOD_SUFFIXES = Arrays.asList(
            "_planks", "_log", "_wood", "_stairs", "_slab", "_fence_gate", "_fence", "_trapdoor", "_door",
            "_button", "_pressure_plate", "_leaves", "_sapling", "_sign", "_wall_sign");
    private static final List<String> SMALL_DECORATIONS = Arrays.asList(
            "flower", "grass", "fern", "bush", "sapling", "vine", "roots", "sprouts", "coral", "torch", "candle",
            "lantern", "chain", "rod", "rail", "banner", "head", "skull", "pot", "plant", "mushroom", "pickle",
            "kelp", "seagrass", "lichen", "dripleaf", "spore", "azalea", "petals", "frogspawn", "cobweb",
            "hanging_sign", "button", "lever", "tripwire", "amethyst_bud", "cluster", "egg", "campfire",
            "bell", "pointed_dripstone", "scaffolding", "carpet", "light_block");

    /** Renommages après la 1.13 (nom moderne -> nom 1.13 présent dans la table). */
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        ALIASES.put("short_grass", "grass");
        ALIASES.put("dirt_path", "grass_path");
        ALIASES.put("oak_sign", "sign");
        ALIASES.put("oak_wall_sign", "wall_sign");
        ALIASES.put("smooth_stone_slab", "stone_slab");
        ALIASES.put("cave_air", "air");
        ALIASES.put("void_air", "air");
        ALIASES.put("bubble_column", "water");
        ALIASES.put("oak_wood", "oak_log");
        ALIASES.put("spruce_wood", "spruce_log");
        ALIASES.put("birch_wood", "birch_log");
        ALIASES.put("jungle_wood", "jungle_log");
        ALIASES.put("acacia_wood", "acacia_log");
        ALIASES.put("dark_oak_wood", "dark_oak_log");
    }

    private final Map<String, Integer> exact = new HashMap<>();
    private final Map<String, List<Candidate>> byName = new HashMap<>();
    private final Map<String, Integer> cache = new HashMap<>();
    private final Map<String, Integer> approximated = new TreeMap<>();

    private static final class Candidate {
        final Map<String, String> properties;
        final int legacy;

        Candidate(Map<String, String> properties, int legacy) {
            this.properties = properties;
            this.legacy = legacy;
        }
    }

    public LegacyMapper(InputStream table) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(table, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int space = line.indexOf(' ');
                String[] idData = line.substring(0, space).split(":");
                int legacy = (Integer.parseInt(idData[0]) << 4) | Integer.parseInt(idData[1]);
                String state = line.substring(space + 1).trim();
                String name = stateName(state);
                Map<String, String> properties = stateProperties(state);
                String key = normalize(name, properties);
                Integer previous = exact.get(key);
                // eau / lave : on préfère les sources immobiles (9, 11) aux blocs "qui coulent" (8, 10)
                if (previous == null || isFlowingVariantOf(previous, legacy)) {
                    exact.put(key, legacy);
                }
                byName.computeIfAbsent(name, k -> new ArrayList<>()).add(new Candidate(properties, legacy));
            }
        }
    }

    private static boolean isFlowingVariantOf(int previous, int legacy) {
        int prevId = previous >> 4;
        int newId = legacy >> 4;
        return (prevId == 8 && newId == 9) || (prevId == 10 && newId == 11);
    }

    /** Conversion d'un état moderne en bloc 1.8 (id << 4 | data). Thread-safe. */
    public synchronized int toLegacy(String state) {
        Integer cached = cache.get(state);
        if (cached != null) {
            return cached;
        }
        String name = stateName(state);
        Map<String, String> properties = stateProperties(state);
        Integer result = lookup(name, properties);
        if (result == null && name.startsWith("stripped_")) {
            // bûches écorcées (1.13+) -> bûche normale de la même essence
            result = lookup(name.substring("stripped_".length()), properties);
            if (result != null) {
                approximated.merge(name, 1, Integer::sum);
            }
        }
        if (result == null) {
            String replacement = approximate(name);
            result = lookup(replacement, properties);
            if (result == null) {
                result = isDecoration(name) ? AIR : STONE;
            }
            approximated.merge(name, 1, Integer::sum);
        }
        int fixed = to18(result);
        cache.put(state, fixed);
        return fixed;
    }

    /** Conversion d'un bloc legacy (fichiers .schematic MCEdit) vers un bloc valide en 1.8. */
    public int legacyTo18(int id, int data) {
        return to18((id << 4) | (data & 0xF));
    }

    /** Noms de blocs convertis approximativement depuis la dernière remise à zéro. */
    public synchronized Map<String, Integer> drainApproximated() {
        Map<String, Integer> copy = new TreeMap<>(approximated);
        approximated.clear();
        return copy;
    }

    private Integer lookup(String name, Map<String, String> properties) {
        String alias = ALIASES.get(name);
        if (alias != null) {
            name = alias;
        }
        Integer value = exact.get(normalize(name, properties));
        if (value != null) {
            return value;
        }
        List<Candidate> candidates = byName.get(name);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Candidate best = candidates.get(0);
        int bestScore = -1;
        for (Candidate candidate : candidates) {
            int score = 0;
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (entry.getValue().equals(candidate.properties.get(entry.getKey()))) {
                    score++;
                }
            }
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best.legacy;
    }

    /** Remplaçant le plus proche pour un bloc absent de la table (blocs 1.13+). */
    private static String approximate(String name) {
        String base = name;
        if (base.startsWith("stripped_")) {
            base = base.substring("stripped_".length());
        }
        if (base.endsWith("_stem") || base.endsWith("_hyphae")) {
            return "oak_log";
        }
        if (base.endsWith("_hanging_sign")) {
            return "air";
        }
        for (String species : WOOD_SPECIES) {
            if (base.startsWith(species)) {
                for (String suffix : WOOD_SUFFIXES) {
                    if (base.endsWith(suffix)) {
                        return suffix.equals("_sign") ? "sign" : suffix.equals("_wall_sign") ? "wall_sign"
                                : suffix.equals("_wood") ? "oak_log" : "oak" + suffix;
                    }
                }
            }
        }
        if (base.endsWith("_wall_sign")) {
            return "wall_sign";
        }
        if (base.endsWith("_sign")) {
            return "sign";
        }
        if (base.endsWith("_stairs")) {
            return "stone_brick_stairs";
        }
        if (base.endsWith("_slab")) {
            return "stone_slab";
        }
        if (base.endsWith("_wall")) {
            return "cobblestone_wall";
        }
        if (base.endsWith("_fence_gate")) {
            return "oak_fence_gate";
        }
        if (base.endsWith("_fence")) {
            return "oak_fence";
        }
        if (base.endsWith("_trapdoor")) {
            return "oak_trapdoor";
        }
        if (base.endsWith("_door")) {
            return "oak_door";
        }
        if (base.endsWith("_button")) {
            return "stone_button";
        }
        if (base.endsWith("_pressure_plate")) {
            return "stone_pressure_plate";
        }
        if (base.endsWith("_leaves")) {
            return "oak_leaves";
        }
        if (base.endsWith("_glass_pane")) {
            return "glass_pane";
        }
        if (base.endsWith("glass")) {
            return "glass";
        }
        if (base.endsWith("_terracotta")) {
            return "terracotta";
        }
        if (base.endsWith("_ore")) {
            return "stone";
        }
        if (base.contains("copper")) {
            return "orange_terracotta";
        }
        if (base.contains("netherite") || base.contains("ancient_debris") || base.contains("crying_obsidian")) {
            return "obsidian";
        }
        if (base.contains("brick")) {
            return "stone_bricks";
        }
        if (base.contains("blackstone") || base.contains("basalt")) {
            return "coal_block";
        }
        if (base.contains("deepslate") || base.contains("tuff") || base.contains("sculk")) {
            return "cobblestone";
        }
        if (base.contains("calcite") || base.contains("quartz")) {
            return "quartz_block";
        }
        if (base.contains("moss") || base.contains("nylium")) {
            return "grass_block";
        }
        if (base.contains("mud") || base.contains("rooted_dirt")) {
            return "dirt";
        }
        if (base.contains("honey") || base.contains("slime")) {
            return "slime_block";
        }
        if (base.contains("froglight") || base.contains("shroomlight")) {
            return "glowstone";
        }
        if (base.equals("smooth_stone")) {
            return "smooth_stone";
        }
        return isDecoration(base) ? "air" : "stone";
    }

    private static boolean isDecoration(String name) {
        for (String word : SMALL_DECORATIONS) {
            if (name.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** Remplace les ids postérieurs à la 1.8 (1.9 -> 1.12) par un équivalent 1.8. */
    private static int to18(int legacy) {
        int id = legacy >> 4;
        int data = legacy & 0xF;
        if (id <= MAX_ID_18) {
            return legacy;
        }
        switch (id) {
            case 201: case 202: return (159 << 4) | 10;          // purpur -> argile violette
            case 203: return (109 << 4) | data;                   // escaliers purpur -> escaliers pierre
            case 204: return (43 << 4);                           // double dalle purpur
            case 205: return (44 << 4) | (data & 8);              // dalle purpur
            case 206: return (121 << 4);                          // briques de l'End -> pierre de l'End
            case 207: return (59 << 4) | Math.min(7, data * 2);   // betteraves -> blé
            case 208: return (2 << 4);                            // chemin -> herbe
            case 210: case 211: return (137 << 4);                // blocs de commande
            case 212: return (79 << 4);                           // glace givrée -> glace
            case 213: return (87 << 4);                           // magma -> netherrack
            case 214: return (159 << 4) | 14;                     // bloc de verrues -> argile rouge
            case 215: return (112 << 4);                          // briques du Nether rouges
            case 216: return (155 << 4);                          // bloc d'os -> quartz
            case 218: return (23 << 4) | (data & 7);              // observateur -> distributeur
            case 251: case 252: return (35 << 4) | data;          // béton -> laine de même couleur
            default:
                if (id >= 219 && id <= 234) {
                    return (159 << 4) | (id - 219);               // boîtes de shulker -> argile colorée
                }
                if (id >= 235 && id <= 250) {
                    return (159 << 4) | (id - 235);               // terre cuite émaillée -> argile colorée
                }
                return AIR;                                       // end rod, chorus, structure...
        }
    }

    // ------------------------------------------------------------------
    // Analyse des états "minecraft:nom[cle=valeur,...]"
    // ------------------------------------------------------------------

    private static String stateName(String state) {
        int bracket = state.indexOf('[');
        String name = bracket < 0 ? state : state.substring(0, bracket);
        int colon = name.indexOf(':');
        return (colon < 0 ? name : name.substring(colon + 1)).trim().toLowerCase();
    }

    private static Map<String, String> stateProperties(String state) {
        Map<String, String> properties = new TreeMap<>();
        int open = state.indexOf('[');
        int close = state.lastIndexOf(']');
        if (open < 0 || close <= open) {
            return properties;
        }
        for (String pair : state.substring(open + 1, close).split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                properties.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return properties;
    }

    private static String normalize(String name, Map<String, String> properties) {
        if (properties.isEmpty()) {
            return name;
        }
        StringBuilder builder = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : new TreeMap<>(properties).entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.append(']').toString();
    }
}
