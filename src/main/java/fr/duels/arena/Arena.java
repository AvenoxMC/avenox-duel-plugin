package fr.duels.arena;

import fr.duels.kit.Kit;
import fr.duels.util.SpawnPoint;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Une arène = un monde "modèle" (template) stocké déchargé sur le disque.
 * Chaque match en charge une copie jetable, ce qui garantit une map intacte à chaque partie.
 */
public class Arena {

    private final String name;
    private String displayName;
    private String templateWorld;
    private Material icon = Material.PAPER;
    private final List<SpawnPoint> spawns = new ArrayList<>();
    private SpawnPoint spectatorSpawn;
    private final Set<String> kits = new LinkedHashSet<>();
    private int maxInstances = 5;
    private boolean enabled = true;
    private int voidY = 0;
    private int buildLimitY = 0;

    // État en mémoire uniquement
    private int activeInstances;
    private boolean editing;

    public Arena(String name, String templateWorld) {
        this.name = name.toLowerCase();
        this.displayName = "&f" + name;
        this.templateWorld = templateWorld;
    }

    public boolean isSetup() {
        return spawns.size() >= 2;
    }

    public boolean supports(Kit kit) {
        return kits.isEmpty() || kits.contains(kit.getName());
    }

    /** Disponible = configurée, activée, compatible avec le kit et avec une place d'instance libre. */
    public boolean isAvailable(Kit kit) {
        return enabled && !editing && isSetup() && supports(kit) && activeInstances < maxInstances;
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

    public String getTemplateWorld() {
        return templateWorld;
    }

    public void setTemplateWorld(String templateWorld) {
        this.templateWorld = templateWorld;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon == null || icon == Material.AIR ? Material.PAPER : icon;
    }

    public List<SpawnPoint> getSpawns() {
        return spawns;
    }

    public SpawnPoint getSpectatorSpawn() {
        return spectatorSpawn;
    }

    public void setSpectatorSpawn(SpawnPoint spectatorSpawn) {
        this.spectatorSpawn = spectatorSpawn;
    }

    public Set<String> getKits() {
        return kits;
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    public void setMaxInstances(int maxInstances) {
        this.maxInstances = Math.max(1, maxInstances);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getVoidY() {
        return voidY;
    }

    public void setVoidY(int voidY) {
        this.voidY = voidY;
    }

    public int getBuildLimitY() {
        return buildLimitY;
    }

    public void setBuildLimitY(int buildLimitY) {
        this.buildLimitY = buildLimitY;
    }

    public int getActiveInstances() {
        return activeInstances;
    }

    public void incrementInstances() {
        activeInstances++;
    }

    public void decrementInstances() {
        activeInstances = Math.max(0, activeInstances - 1);
    }

    public boolean isEditing() {
        return editing;
    }

    public void setEditing(boolean editing) {
        this.editing = editing;
    }
}
