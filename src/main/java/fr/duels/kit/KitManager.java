package fr.duels.kit;

import fr.duels.DuelsPlugin;
import fr.duels.util.ItemBuilder;
import fr.duels.util.YamlFiles;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KitManager {

    public static final List<String> SETTINGS = Collections.unmodifiableList(Arrays.asList(
            "enabled", "queue", "build", "break-map", "hunger", "regen", "no-damage", "water-kills", "hit-delay"));

    private final DuelsPlugin plugin;
    private final File file;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
    }

    public void load() {
        kits.clear();
        if (!file.exists()) {
            createDefaults();
            save();
            return;
        }
        YamlConfiguration config = YamlFiles.load(file);
        ConfigurationSection root = config.getConfigurationSection("kits");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Kit kit = new Kit(key);
            kit.setDisplayName(section.getString("display-name", "&e" + key));
            kit.setIcon(section.getItemStack("icon"));
            kit.setContents(toItems(section.getList("contents"), 36));
            kit.setArmor(toItems(section.getList("armor"), 4));
            List<PotionEffect> effects = new ArrayList<>();
            for (String raw : section.getStringList("effects")) {
                PotionEffect effect = parseEffect(raw);
                if (effect != null) {
                    effects.add(effect);
                } else {
                    plugin.getLogger().warning("Effet invalide dans le kit " + key + " : " + raw);
                }
            }
            kit.setEffects(effects);
            ConfigurationSection settings = section.getConfigurationSection("settings");
            if (settings != null) {
                for (String setting : SETTINGS) {
                    if (settings.contains(setting)) {
                        applySetting(kit, setting, String.valueOf(settings.get(setting)));
                    }
                }
            }
            kits.put(kit.getName(), kit);
        }
        plugin.getLogger().info(kits.size() + " kit(s) chargé(s).");
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Kit kit : kits.values()) {
            String path = "kits." + kit.getName() + ".";
            config.set(path + "display-name", kit.getDisplayName());
            config.set(path + "icon", kit.getRawIcon());
            config.set(path + "contents", Arrays.asList(kit.getContents()));
            config.set(path + "armor", Arrays.asList(kit.getArmor()));
            List<String> effects = new ArrayList<>();
            for (PotionEffect effect : kit.getEffects()) {
                effects.add(effect.getType().getName() + ":" + effect.getAmplifier() + ":" + effect.getDuration());
            }
            config.set(path + "effects", effects);
            for (String setting : SETTINGS) {
                config.set(path + "settings." + setting, getSetting(kit, setting));
            }
        }
        YamlFiles.save(config, file);
    }

    private static ItemStack[] toItems(List<?> list, int size) {
        ItemStack[] items = new ItemStack[size];
        if (list == null) {
            return items;
        }
        for (int i = 0; i < size && i < list.size(); i++) {
            Object o = list.get(i);
            items[i] = o instanceof ItemStack ? (ItemStack) o : null;
        }
        return items;
    }

    /** Format : TYPE:amplificateur:durée_en_ticks (ex : SPEED:1:999999). */
    private static PotionEffect parseEffect(String raw) {
        String[] parts = raw.split(":");
        PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
        if (type == null) {
            return null;
        }
        try {
            int amplifier = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int duration = parts.length > 2 ? Integer.parseInt(parts[2]) : Integer.MAX_VALUE;
            return new PotionEffect(type, duration, amplifier);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Object getSetting(Kit kit, String setting) {
        switch (setting) {
            case "enabled": return kit.isEnabled();
            case "queue": return kit.isQueue();
            case "build": return kit.isBuild();
            case "break-map": return kit.isBreakMap();
            case "hunger": return kit.isHunger();
            case "regen": return kit.isRegen();
            case "no-damage": return kit.isNoDamage();
            case "water-kills": return kit.isWaterKills();
            case "hit-delay": return kit.getHitDelay();
            default: return null;
        }
    }

    /** @return false si le réglage ou la valeur est invalide. */
    public boolean applySetting(Kit kit, String setting, String value) {
        if (setting.equals("hit-delay")) {
            try {
                kit.setHitDelay(Integer.parseInt(value));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            return false;
        }
        boolean b = Boolean.parseBoolean(value);
        switch (setting) {
            case "enabled": kit.setEnabled(b); return true;
            case "queue": kit.setQueue(b); return true;
            case "build": kit.setBuild(b); return true;
            case "break-map": kit.setBreakMap(b); return true;
            case "hunger": kit.setHunger(b); return true;
            case "regen": kit.setRegen(b); return true;
            case "no-damage": kit.setNoDamage(b); return true;
            case "water-kills": kit.setWaterKills(b); return true;
            default: return false;
        }
    }

    public Kit get(String name) {
        return name == null ? null : kits.get(name.toLowerCase());
    }

    public Collection<Kit> getKits() {
        return kits.values();
    }

    public List<Kit> getEnabledKits() {
        List<Kit> list = new ArrayList<>();
        for (Kit kit : kits.values()) {
            if (kit.isEnabled()) {
                list.add(kit);
            }
        }
        return list;
    }

    public List<Kit> getQueueKits() {
        List<Kit> list = new ArrayList<>();
        for (Kit kit : kits.values()) {
            if (kit.isEnabled() && kit.isQueue()) {
                list.add(kit);
            }
        }
        return list;
    }

    public Kit create(String name) {
        Kit kit = new Kit(name);
        kits.put(kit.getName(), kit);
        return kit;
    }

    public boolean delete(String name) {
        return kits.remove(name.toLowerCase()) != null;
    }

    // ------------------------------------------------------------------
    // Kits par défaut (créés au premier lancement, modifiables ensuite)
    // ------------------------------------------------------------------

    private void createDefaults() {
        ItemStack[] dArmor = {
                new ItemBuilder(Material.DIAMOND_BOOTS).enchant(Enchantment.PROTECTION_ENVIRONMENTAL, 2).enchant(Enchantment.DURABILITY, 3).build(),
                new ItemBuilder(Material.DIAMOND_LEGGINGS).enchant(Enchantment.PROTECTION_ENVIRONMENTAL, 2).enchant(Enchantment.DURABILITY, 3).build(),
                new ItemBuilder(Material.DIAMOND_CHESTPLATE).enchant(Enchantment.PROTECTION_ENVIRONMENTAL, 2).enchant(Enchantment.DURABILITY, 3).build(),
                new ItemBuilder(Material.DIAMOND_HELMET).enchant(Enchantment.PROTECTION_ENVIRONMENTAL, 2).enchant(Enchantment.DURABILITY, 3).build()
        };

        // NoDebuff
        Kit nodebuff = create("nodebuff");
        nodebuff.setDisplayName("&bNoDebuff");
        nodebuff.setIcon(new ItemStack(Material.POTION, 1, (short) 16421));
        ItemStack[] nd = new ItemStack[36];
        nd[0] = new ItemBuilder(Material.DIAMOND_SWORD).enchant(Enchantment.DAMAGE_ALL, 3).enchant(Enchantment.FIRE_ASPECT, 2).enchant(Enchantment.DURABILITY, 3).build();
        nd[1] = new ItemStack(Material.ENDER_PEARL, 16);
        nd[2] = new ItemStack(Material.POTION, 1, (short) 8226);
        nd[3] = new ItemStack(Material.POTION, 1, (short) 8259);
        nd[8] = new ItemStack(Material.COOKED_BEEF, 64);
        for (int i = 4; i < 36; i++) {
            if (nd[i] == null) {
                nd[i] = new ItemStack(Material.POTION, 1, (short) 16421);
            }
        }
        nd[17] = new ItemStack(Material.POTION, 1, (short) 8226);
        nd[26] = new ItemStack(Material.POTION, 1, (short) 8226);
        nd[35] = new ItemStack(Material.POTION, 1, (short) 8226);
        nodebuff.setContents(nd);
        nodebuff.setArmor(dArmor);

        // BuildUHC
        Kit buhc = create("builduhc");
        buhc.setDisplayName("&6BuildUHC");
        buhc.setIcon(new ItemStack(Material.LAVA_BUCKET));
        ItemStack[] bu = new ItemStack[36];
        bu[0] = new ItemBuilder(Material.DIAMOND_SWORD).enchant(Enchantment.DAMAGE_ALL, 3).build();
        bu[1] = new ItemStack(Material.FISHING_ROD);
        bu[2] = new ItemBuilder(Material.BOW).enchant(Enchantment.ARROW_DAMAGE, 3).build();
        bu[3] = new ItemStack(Material.COOKED_BEEF, 64);
        bu[4] = new ItemStack(Material.GOLDEN_APPLE, 6);
        bu[5] = new ItemStack(Material.LAVA_BUCKET);
        bu[6] = new ItemStack(Material.WATER_BUCKET);
        bu[7] = new ItemStack(Material.COBBLESTONE, 64);
        bu[8] = new ItemStack(Material.WOOD, 64);
        bu[9] = new ItemStack(Material.ARROW, 32);
        bu[10] = new ItemStack(Material.DIAMOND_PICKAXE);
        bu[11] = new ItemStack(Material.DIAMOND_AXE);
        bu[12] = new ItemStack(Material.LAVA_BUCKET);
        bu[13] = new ItemStack(Material.WATER_BUCKET);
        bu[16] = new ItemStack(Material.COBBLESTONE, 64);
        bu[17] = new ItemStack(Material.WOOD, 64);
        buhc.setContents(bu);
        buhc.setArmor(dArmor);
        buhc.setBuild(true);
        buhc.setRegen(false);

        // Sumo
        Kit sumo = create("sumo");
        sumo.setDisplayName("&aSumo");
        sumo.setIcon(new ItemStack(Material.LEASH));
        sumo.setNoDamage(true);
        sumo.setWaterKills(true);
        sumo.setHunger(false);

        // Combo
        Kit combo = create("combo");
        combo.setDisplayName("&dCombo");
        combo.setIcon(new ItemStack(Material.RAW_FISH, 1, (short) 3));
        ItemStack[] co = new ItemStack[36];
        co[0] = new ItemBuilder(Material.DIAMOND_SWORD).enchant(Enchantment.DAMAGE_ALL, 5).enchant(Enchantment.DURABILITY, 3).build();
        co[1] = new ItemStack(Material.GOLDEN_APPLE, 64, (short) 1);
        combo.setContents(co);
        combo.setArmor(new ItemStack[]{
                new ItemBuilder(Material.DIAMOND_BOOTS).enchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4).unbreakable().build(),
                new ItemBuilder(Material.DIAMOND_LEGGINGS).enchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4).unbreakable().build(),
                new ItemBuilder(Material.DIAMOND_CHESTPLATE).enchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4).unbreakable().build(),
                new ItemBuilder(Material.DIAMOND_HELMET).enchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4).unbreakable().build()
        });
        combo.setHitDelay(2);
        combo.setHunger(false);

        // Archer
        Kit archer = create("archer");
        archer.setDisplayName("&eArcher");
        archer.setIcon(new ItemStack(Material.BOW));
        ItemStack[] ar = new ItemStack[36];
        ar[0] = new ItemBuilder(Material.BOW).enchant(Enchantment.ARROW_INFINITE, 1).enchant(Enchantment.ARROW_DAMAGE, 2).unbreakable().build();
        ar[8] = new ItemStack(Material.COOKED_BEEF, 32);
        ar[9] = new ItemStack(Material.ARROW, 1);
        archer.setContents(ar);
        archer.setArmor(new ItemStack[]{
                new ItemStack(Material.LEATHER_BOOTS), new ItemStack(Material.LEATHER_LEGGINGS),
                new ItemStack(Material.LEATHER_CHESTPLATE), new ItemStack(Material.LEATHER_HELMET)
        });
        archer.setEffects(Collections.singletonList(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0)));
    }
}
