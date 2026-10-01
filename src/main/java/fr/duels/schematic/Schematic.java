package fr.duels.schematic;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Schématique chargée en mémoire, déjà convertie en blocs 1.8 (id << 4 | data, 0 = air).
 *
 * Formats lus :
 *  - .schem      : Sponge Schematic v1, v2 et v3 (WorldEdit 7+, FAWE) — blocs 1.13+ convertis ;
 *  - .schematic  : MCEdit / WorldEdit 6 (ids numériques, natif en 1.8).
 */
public final class Schematic {

    private final String name;
    private final int width;
    private final int height;
    private final int length;
    private final int[] blocks;
    /** Décalage entre la position de collage (le joueur lors du //copy) et le coin minimum. */
    private final int[] offset;
    private final Map<String, Integer> approximated;

    private Schematic(String name, int width, int height, int length, int[] blocks, int[] offset,
                      Map<String, Integer> approximated) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.length = length;
        this.blocks = blocks;
        this.offset = offset;
        this.approximated = approximated;
    }

    /** Lecture + conversion (à appeler hors du thread principal pour les grosses maps). */
    public static Schematic load(File file, LegacyMapper mapper, long maxVolume) throws IOException {
        Map<String, Object> root = Nbt.read(file);
        Map<String, Object> nested = Nbt.compound(root, "Schematic");
        if (nested != null && !root.containsKey("Width")) {
            root = nested; // Sponge v3 : tout est dans le compound "Schematic"
        }
        int width = Nbt.integer(root, "Width", 0);
        int height = Nbt.integer(root, "Height", 0);
        int length = Nbt.integer(root, "Length", 0);
        long volume = (long) width * height * length;
        if (volume <= 0) {
            throw new IOException("Dimensions invalides (" + width + "x" + height + "x" + length + ")");
        }
        if (maxVolume > 0 && volume > maxVolume) {
            throw new IOException("Schématique trop grande : " + volume + " blocs (max " + maxVolume + ")");
        }
        int[] blocks = new int[(int) volume];
        Map<String, Object> blocksTag = Nbt.compound(root, "Blocks");

        if (blocksTag != null) {
            // Sponge v3
            readPalette(Nbt.compound(blocksTag, "Palette"), (byte[]) blocksTag.get("Data"), blocks, mapper);
        } else if (root.get("Palette") instanceof Map) {
            // Sponge v1 / v2
            readPalette(Nbt.compound(root, "Palette"), (byte[]) root.get("BlockData"), blocks, mapper);
        } else if (root.get("Blocks") instanceof byte[]) {
            // MCEdit (.schematic)
            readLegacy(root, blocks, mapper);
        } else {
            throw new IOException("Format de schématique non reconnu");
        }

        int[] offset = readOffset(root, blocksTag != null);
        String name = file.getName();
        Map<String, Integer> approximated = mapper.drainApproximated();
        return new Schematic(name, width, height, length, blocks, offset, approximated);
    }

    private static void readPalette(Map<String, Object> palette, byte[] data, int[] blocks, LegacyMapper mapper)
            throws IOException {
        if (palette == null || data == null) {
            throw new IOException("Palette ou données de blocs manquantes");
        }
        int max = 0;
        for (Object id : palette.values()) {
            max = Math.max(max, ((Number) id).intValue());
        }
        int[] converted = new int[max + 1];
        for (Map.Entry<String, Object> entry : palette.entrySet()) {
            converted[((Number) entry.getValue()).intValue()] = mapper.toLegacy(entry.getKey());
        }
        // indices de palette encodés en VarInt
        int index = 0;
        int i = 0;
        while (i < data.length && index < blocks.length) {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                if (i >= data.length) {
                    throw new IOException("Données de blocs tronquées");
                }
                b = data[i++];
                value |= (b & 0x7F) << shift;
                shift += 7;
                if (shift > 35) {
                    throw new IOException("VarInt invalide");
                }
            } while ((b & 0x80) != 0);
            blocks[index++] = value < converted.length ? converted[value] : LegacyMapper.AIR;
        }
    }

    private static void readLegacy(Map<String, Object> root, int[] blocks, LegacyMapper mapper) throws IOException {
        byte[] ids = (byte[]) root.get("Blocks");
        byte[] data = root.get("Data") instanceof byte[] ? (byte[]) root.get("Data") : new byte[ids.length];
        byte[] add = root.get("AddBlocks") instanceof byte[] ? (byte[]) root.get("AddBlocks") : null;
        if (ids.length < blocks.length) {
            throw new IOException("Données de blocs tronquées");
        }
        for (int i = 0; i < blocks.length; i++) {
            int id = ids[i] & 0xFF;
            if (add != null && (i >> 1) < add.length) {
                int extra = (i & 1) == 0 ? (add[i >> 1] & 0x0F) : ((add[i >> 1] >> 4) & 0x0F);
                id |= extra << 8;
            }
            blocks[i] = id == 0 ? LegacyMapper.AIR : mapper.legacyTo18(id, data[i]);
        }
    }

    private static int[] readOffset(Map<String, Object> root, boolean v3) {
        Map<String, Object> metadata = Nbt.compound(root, "Metadata");
        Map<String, Object> source = metadata != null && metadata.containsKey("WEOffsetX") ? metadata
                : root.containsKey("WEOffsetX") ? root : null;
        if (source != null) {
            return new int[]{Nbt.integer(source, "WEOffsetX", 0), Nbt.integer(source, "WEOffsetY", 0),
                    Nbt.integer(source, "WEOffsetZ", 0)};
        }
        if (v3 && root.get("Offset") instanceof int[] && ((int[]) root.get("Offset")).length >= 3) {
            int[] o = (int[]) root.get("Offset");
            return new int[]{o[0], o[1], o[2]};
        }
        return new int[3];
    }

    /** Bloc 1.8 encodé aux coordonnées relatives (0 = air). */
    public int get(int x, int y, int z) {
        return blocks[x + z * width + y * width * length];
    }

    public String getName() {
        return name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLength() {
        return length;
    }

    public int[] getOffset() {
        return offset.clone();
    }

    public long countBlocks() {
        long count = 0;
        for (int block : blocks) {
            if (block != LegacyMapper.AIR) {
                count++;
            }
        }
        return count;
    }

    /** Blocs (noms modernes) convertis approximativement, avec leur nombre de variantes. */
    public Map<String, Integer> getApproximated() {
        return Collections.unmodifiableMap(approximated);
    }

    public List<String> describeApproximated(int max) {
        List<String> list = new java.util.ArrayList<>();
        for (String key : approximated.keySet()) {
            if (list.size() >= max) {
                list.add("...");
                break;
            }
            list.add(key);
        }
        return list;
    }
}
