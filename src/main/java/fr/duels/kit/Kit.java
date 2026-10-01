package fr.duels.kit;

import fr.duels.util.PlayerUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

public class Kit {

    private final String name;
    private String displayName;
    private ItemStack icon;
    private ItemStack[] contents = new ItemStack[36];
    private ItemStack[] armor = new ItemStack[4];
    private List<PotionEffect> effects = new ArrayList<>();

    // Réglages de gameplay
    private boolean enabled = true;
    private boolean queue = true;
    private boolean build = false;
    private boolean breakMap = false;
    private boolean hunger = true;
    private boolean regen = true;
    private boolean noDamage = false;
    private boolean waterKills = false;
    private int hitDelay = 20;

    public Kit(String name) {
        this.name = name.toLowerCase();
        this.displayName = "&e" + name;
    }

    /** Équipe le joueur avec le kit (inventaire, armure, effets, réglages de combat). */
    public void apply(Player player) {
        PlayerUtil.reset(player, GameMode.SURVIVAL);
        player.getInventory().setContents(cloneArray(contents, 36));
        player.getInventory().setArmorContents(cloneArray(armor, 4));
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect, true);
        }
        player.setMaximumNoDamageTicks(hitDelay);
        player.updateInventory();
    }

    /** Copie l'inventaire, l'armure et les effets actuels du joueur dans le kit. */
    public void copyFrom(Player player) {
        contents = cloneArray(player.getInventory().getContents(), 36);
        armor = cloneArray(player.getInventory().getArmorContents(), 4);
        effects = new ArrayList<>(player.getActivePotionEffects());
    }

    private static ItemStack[] cloneArray(ItemStack[] source, int size) {
        ItemStack[] copy = new ItemStack[size];
        for (int i = 0; i < size && i < source.length; i++) {
            ItemStack item = source[i];
            copy[i] = (item == null || item.getType() == Material.AIR) ? null : item.clone();
        }
        return copy;
    }

    public ItemStack getIcon() {
        if (icon != null && icon.getType() != Material.AIR) {
            return icon.clone();
        }
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                return new ItemStack(item.getType(), 1, item.getDurability());
            }
        }
        return new ItemStack(Material.DIAMOND_SWORD);
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon == null ? null : icon.clone();
    }

    public ItemStack getRawIcon() {
        return icon;
    }

    public ItemStack[] getContents() {
        return contents;
    }

    public void setContents(ItemStack[] contents) {
        this.contents = cloneArray(contents, 36);
    }

    public ItemStack[] getArmor() {
        return armor;
    }

    public void setArmor(ItemStack[] armor) {
        this.armor = cloneArray(armor, 4);
    }

    public List<PotionEffect> getEffects() {
        return effects;
    }

    public void setEffects(List<PotionEffect> effects) {
        this.effects = new ArrayList<>(effects);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isQueue() {
        return queue;
    }

    public void setQueue(boolean queue) {
        this.queue = queue;
    }

    public boolean isBuild() {
        return build;
    }

    public void setBuild(boolean build) {
        this.build = build;
    }

    public boolean isBreakMap() {
        return breakMap;
    }

    public void setBreakMap(boolean breakMap) {
        this.breakMap = breakMap;
    }

    public boolean isHunger() {
        return hunger;
    }

    public void setHunger(boolean hunger) {
        this.hunger = hunger;
    }

    public boolean isRegen() {
        return regen;
    }

    public void setRegen(boolean regen) {
        this.regen = regen;
    }

    public boolean isNoDamage() {
        return noDamage;
    }

    public void setNoDamage(boolean noDamage) {
        this.noDamage = noDamage;
    }

    public boolean isWaterKills() {
        return waterKills;
    }

    public void setWaterKills(boolean waterKills) {
        this.waterKills = waterKills;
    }

    public int getHitDelay() {
        return hitDelay;
    }

    public void setHitDelay(int hitDelay) {
        this.hitDelay = Math.max(0, hitDelay);
    }
}
