package fr.duels.gui;

import fr.duels.DuelsPlugin;
import fr.duels.kit.Kit;
import fr.duels.util.ItemBuilder;
import fr.duels.util.Lang;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Menu principal "Jouer" : un kit = un mode de jeu ; cliquer rejoint (ou quitte) sa file. */
public class QueueMenu extends Menu {

    public QueueMenu(DuelsPlugin plugin) {
        super(plugin, Lang.raw("menu.queue.title"), 3);
    }

    @Override
    public boolean isAutoRefresh() {
        return true;
    }

    @Override
    protected void build(Player viewer) {
        List<Kit> kits = plugin.getKitManager().getQueueKits();
        Kit current = plugin.getQueueManager().getQueuedKit(viewer.getUniqueId());
        int rows = rowsFor(Math.max(1, kits.size()));
        if (current != null && kits.size() <= 28) {
            rows = Math.min(6, rows + 1);
        }
        setRows(rows);

        int[] slots = slotsFor(kits.size());
        for (int i = 0; i < slots.length; i++) {
            final Kit kit = kits.get(i);
            int playing = plugin.getMatchManager().countPlaying(kit);
            int arenas = plugin.getArenaManager().getArenasFor(kit).size();
            ItemBuilder icon = new ItemBuilder(kit.getIcon())
                    .name(kit.getDisplayName())
                    .lore(Lang.list("menu.queue.kit-lore",
                            "queued", plugin.getQueueManager().getQueued(kit),
                            "playing", playing,
                            "arenas", arenas))
                    .amount(Math.max(1, playing))
                    .hideFlags();
            if (kit == current) {
                icon.glow().addLore(Lang.list("menu.queue.kit-lore-queued"));
            } else {
                icon.addLore(Lang.list("menu.queue.kit-lore-click"));
            }
            set(slots[i], icon.build(), (player, click) -> {
                if (plugin.getQueueManager().getQueuedKit(player.getUniqueId()) == kit) {
                    plugin.getQueueManager().leave(player, true);
                } else {
                    plugin.getQueueManager().join(player, kit);
                }
                if (player.getOpenInventory().getTopInventory().getHolder() == this) {
                    refresh(player);
                }
            });
        }

        if (kits.isEmpty()) {
            set(13, new ItemBuilder(Material.BARRIER).name(Lang.raw("menu.queue.no-kits")).build());
        }
        if (current != null) {
            set(getSize() - 5, new ItemBuilder(Material.BARRIER).name(Lang.raw("menu.queue.leave"))
                    .lore(Lang.list("menu.queue.leave-lore", "kit", current.getDisplayName())).build(), (player, click) -> {
                plugin.getQueueManager().leave(player, true);
                refresh(player);
            });
        }
    }
}
