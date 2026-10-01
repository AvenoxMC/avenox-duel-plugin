package fr.duels.gui;

import fr.duels.DuelsPlugin;
import fr.duels.util.ItemBuilder;
import fr.duels.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Menu d'inventaire simple : chaque sous-classe remplit ses boutons dans {@link #build(Player)}.
 * Les clics sont annulés et redirigés vers le gestionnaire du bouton par {@link MenuListener}.
 */
public abstract class Menu implements InventoryHolder {

    @FunctionalInterface
    public interface ClickHandler {
        void onClick(Player player, ClickType click);
    }

    protected final DuelsPlugin plugin;
    private final String title;
    private int rows;
    private final Map<Integer, ItemStack> items = new HashMap<>();
    private final Map<Integer, ClickHandler> handlers = new HashMap<>();
    private Inventory inventory;

    protected Menu(DuelsPlugin plugin, String title, int rows) {
        this.plugin = plugin;
        this.title = title;
        this.rows = Math.max(1, Math.min(6, rows));
    }

    protected abstract void build(Player viewer);

    /** Les menus "vivants" (compteurs) sont rafraîchis chaque seconde. */
    public boolean isAutoRefresh() {
        return false;
    }

    protected void setRows(int rows) {
        this.rows = Math.max(1, Math.min(6, rows));
    }

    protected int getSize() {
        return rows * 9;
    }

    protected void set(int slot, ItemStack item, ClickHandler handler) {
        if (slot < 0 || slot >= getSize()) {
            return;
        }
        items.put(slot, item);
        if (handler != null) {
            handlers.put(slot, handler);
        }
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    public void open(Player player) {
        items.clear();
        handlers.clear();
        build(player);
        inventory = Bukkit.createInventory(this, getSize(), trimTitle(Lang.color(title)));
        render();
        player.openInventory(inventory);
    }

    public void refresh(Player player) {
        int oldSize = inventory == null ? -1 : inventory.getSize();
        items.clear();
        handlers.clear();
        build(player);
        if (inventory == null || oldSize != getSize()) {
            open(player);
            return;
        }
        render();
        player.updateInventory();
    }

    private void render() {
        inventory.clear();
        if (plugin.getConfig().getBoolean("gui.filler", true)) {
            ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, 1,
                    (short) plugin.getConfig().getInt("gui.filler-color", 7)).name(" ").build();
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
        }
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }
    }

    void handleClick(Player player, int slot, ClickType click) {
        ClickHandler handler = handlers.get(slot);
        if (handler != null) {
            handler.onClick(player, click);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ------------------------------------------------------------------
    // Aides de mise en page
    // ------------------------------------------------------------------

    /** Nombre de lignes pour afficher {@code count} objets dans un cadre (bordure d'une case). */
    protected static int rowsFor(int count) {
        if (count > 28) {
            return 6;
        }
        return Math.min(6, Math.max(1, (int) Math.ceil(count / 7.0)) + 2);
    }

    /** Emplacements disponibles pour {@code count} objets (encadrés si possible). */
    protected static int[] slotsFor(int count) {
        if (count > 28) {
            int[] slots = new int[Math.min(54, count)];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = i;
            }
            return slots;
        }
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) {
            int row = 1 + i / 7;
            int col = 1 + i % 7;
            slots[i] = row * 9 + col;
        }
        return slots;
    }

    private static String trimTitle(String text) {
        return text.length() <= 32 ? text : text.substring(0, 32);
    }
}
