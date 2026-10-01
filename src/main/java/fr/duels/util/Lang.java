package fr.duels.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès aux messages de messages.yml (avec repli sur la version embarquée dans le jar).
 * Les placeholders s'écrivent {nom} et se passent par paires : get("cle", "nom", valeur, ...).
 */
public final class Lang {

    private static YamlConfiguration messages = new YamlConfiguration();

    private Lang() {
    }

    public static void load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlFiles.load(file);
        InputStream in = plugin.getResource("messages.yml");
        if (in != null) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    public static String raw(String key) {
        String value = messages.getString(key);
        return value == null ? key : value;
    }

    public static String get(String key, Object... placeholders) {
        return color(replace(raw(key), placeholders));
    }

    public static List<String> list(String key, Object... placeholders) {
        List<String> out = new ArrayList<>();
        for (String line : messages.getStringList(key)) {
            out.add(color(replace(line, placeholders)));
        }
        return out;
    }

    public static void send(CommandSender to, String key, Object... placeholders) {
        String message = get(key, placeholders);
        if (message.isEmpty()) {
            return;
        }
        to.sendMessage(prefix() + message);
    }

    public static String prefix() {
        return color(raw("prefix"));
    }

    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String replace(String text, Object... placeholders) {
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return text;
    }
}
