package fr.duels.schematic;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Lecteur NBT minimal (lecture seule).
 * Compound -> Map<String,Object>, List -> List<Object>, tableaux -> byte[] / int[] / long[].
 */
final class Nbt {

    private Nbt() {
    }

    /** Lit un fichier NBT (compressé GZIP ou non) et retourne le compound racine. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> read(File file) throws IOException {
        try (InputStream raw = new BufferedInputStream(new FileInputStream(file))) {
            raw.mark(2);
            int b1 = raw.read();
            int b2 = raw.read();
            raw.reset();
            InputStream in = (b1 == 0x1f && b2 == 0x8b) ? new GZIPInputStream(raw) : raw;
            DataInputStream data = new DataInputStream(new BufferedInputStream(in));
            int type = data.readUnsignedByte();
            if (type != 10) {
                throw new IOException("Le fichier ne commence pas par un compound NBT");
            }
            data.readUTF(); // nom de la racine
            return (Map<String, Object>) readPayload(data, 10, 0);
        }
    }

    private static Object readPayload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > 512) {
            throw new IOException("NBT trop profond");
        }
        switch (type) {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7: {
                byte[] bytes = new byte[checkLength(in.readInt())];
                in.readFully(bytes);
                return bytes;
            }
            case 8:
                return in.readUTF();
            case 9: {
                int elementType = in.readUnsignedByte();
                int length = checkLength(in.readInt());
                List<Object> list = new ArrayList<>(Math.min(length, 4096));
                for (int i = 0; i < length; i++) {
                    list.add(readPayload(in, elementType, depth + 1));
                }
                return list;
            }
            case 10: {
                Map<String, Object> map = new HashMap<>();
                while (true) {
                    int childType = in.readUnsignedByte();
                    if (childType == 0) {
                        return map;
                    }
                    String name = in.readUTF();
                    map.put(name, readPayload(in, childType, depth + 1));
                }
            }
            case 11: {
                int[] ints = new int[checkLength(in.readInt())];
                for (int i = 0; i < ints.length; i++) {
                    ints[i] = in.readInt();
                }
                return ints;
            }
            case 12: {
                long[] longs = new long[checkLength(in.readInt())];
                for (int i = 0; i < longs.length; i++) {
                    longs[i] = in.readLong();
                }
                return longs;
            }
            case 0:
                return null;
            default:
                throw new IOException("Type de tag NBT inconnu : " + type);
        }
    }

    private static int checkLength(int length) throws IOException {
        if (length < 0 || length > 256 * 1024 * 1024) {
            throw new IOException("Taille NBT invalide : " + length);
        }
        return length;
    }

    // ------------------------------------------------------------------
    // Accès typés
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static Map<String, Object> compound(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    static int integer(Map<String, Object> map, String key, int def) {
        Object value = map.get(key);
        if (value instanceof Short) {
            return ((Short) value) & 0xFFFF; // Width/Height/Length sont des shorts non signés
        }
        return value instanceof Number ? ((Number) value).intValue() : def;
    }
}
