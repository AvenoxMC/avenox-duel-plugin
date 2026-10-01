package fr.duels.gui;

import fr.duels.DuelsPlugin;
import fr.duels.kit.Kit;
import fr.duels.util.ItemBuilder;
import fr.duels.util.Lang;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BiConsumer;

/** Choix d'un kit (défi, combat de party...). */
public class KitSelectMenu extends Menu {

    private final BiConsumer<Player, Kit> callback;

    public KitSelectMenu(DuelsPlugin plugin, String title, BiConsumer<Player, Kit> callback) {
        super(plugin, title, 3);
        this.callback = callback;
    }

    @Override
    protected void build(Player viewer) {
        List<Kit> kits = plugin.getKitManager().getEnabledKits();
        setRows(rowsFor(Math.max(1, kits.size())));
        if (kits.isEmpty()) {
            set(13, new ItemBuilder(Material.BARRIER).name(Lang.raw("menu.queue.no-kits")).build());
            return;
        }
        int[] slots = slotsFor(kits.size());
        for (int i = 0; i < slots.length; i++) {
            final Kit kit = kits.get(i);
            set(slots[i], new ItemBuilder(kit.getIcon())
                    .name(kit.getDisplayName())
                    .lore(Lang.list("menu.kit-select.lore", "arenas", plugin.getArenaManager().getArenasFor(kit).size()))
                    .hideFlags()
                    .build(), (player, click) -> {
                player.closeInventory();
                callback.accept(player, kit);
            });
        }
    }
}
