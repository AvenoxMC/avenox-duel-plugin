package fr.duels.util;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Lecture / écriture YAML forcées en UTF-8 (la 1.8 dépend sinon du charset du système). */
public final class YamlFiles {

    private YamlFiles() {
    }

    public static YamlConfiguration load(File file) {
        YamlConfiguration config = new YamlConfiguration();
        if (!file.exists()) {
            return config;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            config.load(reader);
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Duels] Impossible de lire " + file.getName() + " : " + e.getMessage());
        }
        return config;
    }

    public static void save(YamlConfiguration config, File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(file.toPath(), config.saveToString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Bukkit.getLogger().severe("[Duels] Impossible d'écrire " + file.getName() + " : " + e.getMessage());
        }
    }
}
