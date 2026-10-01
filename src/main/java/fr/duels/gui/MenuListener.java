package fr.duels.gui;

import fr.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class MenuListener implements Listener {

    private final DuelsPlugin plugin;

    public MenuListener(DuelsPlugin plugin) {
        this.plugin = plugin;
        // rafraîchissement des menus "vivants" (compteurs de file, matchs en cours...)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
                if (holder instanceof Menu && ((Menu) holder).isAutoRefresh()) {
                    ((Menu) holder).refresh(player);
                }
            }
        }, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof Menu) || !(event.getWhoClicked() instanceof Player)) {
            return;
        }
        event.setCancelled(true);
        final Menu menu = (Menu) holder;
        final Player player = (Player) event.getWhoClicked();
        final int slot = event.getRawSlot();
        final ClickType click = event.getClick();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        // exécuté au tick suivant : on peut ouvrir un autre menu sans risque
        Bukkit.getScheduler().runTask(plugin, () -> menu.handleClick(player, slot, click));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
