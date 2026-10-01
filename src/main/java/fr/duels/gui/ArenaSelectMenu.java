package fr.duels.gui;

import fr.duels.DuelsPlugin;
import fr.duels.arena.Arena;
import fr.duels.kit.Kit;
import fr.duels.util.ItemBuilder;
import fr.duels.util.Lang;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BiConsumer;

/** Choix de la map (ou "aléatoire") parmi les arènes compatibles avec le kit. */
public class ArenaSelectMenu extends Menu {

    private final Kit kit;
    private final BiConsumer<Player, Arena> callback;

    private ArenaSelectMenu(DuelsPlugin plugin, Kit kit, BiConsumer<Player, Arena> callback) {
        super(plugin, Lang.raw("menu.arena-select.title"), 3);
        this.kit = kit;
        this.callback = callback;
    }

    /** Ouvre le menu, ou appelle directement le callback (arène aléatoire) si le choix est désactivé ou inutile. */
    public static void openOrSkip(DuelsPlugin plugin, Player player, Kit kit, BiConsumer<Player, Arena> callback) {
        if (!plugin.getConfig().getBoolean("duel.arena-select", true)
                || plugin.getArenaManager().getArenasFor(kit).size() <= 1) {
            callback.accept(player, null);
            return;
        }
        new ArenaSelectMenu(plugin, kit, callback).open(player);
    }

    @Override
    public boolean isAutoRefresh() {
        return true;
    }

    @Override
    protected void build(Player viewer) {
        List<Arena> arenas = plugin.getArenaManager().getArenasFor(kit);
        setRows(rowsFor(arenas.size() + 1));
        int[] slots = slotsFor(arenas.size() + 1);

        set(slots[0], new ItemBuilder(Material.ENDER_PEARL)
                .name(Lang.raw("menu.arena-select.random"))
                .lore(Lang.list("menu.arena-select.random-lore"))
                .build(), (player, click) -> {
            player.closeInventory();
            callback.accept(player, null);
        });

        for (int i = 0; i < arenas.size(); i++) {
            final Arena arena = arenas.get(i);
            boolean available = plugin.getArenaManager().isAvailable(arena, kit);
            set(slots[i + 1], new ItemBuilder(arena.getIcon())
                    .name(arena.getDisplayName())
                    .lore(Lang.list(available ? "menu.arena-select.available" : "menu.arena-select.unavailable",
                            "active", arena.getActiveInstances(), "max", arena.getMaxInstances()))
                    .hideFlags()
                    .build(), (player, click) -> {
                player.closeInventory();
                callback.accept(player, arena);
            });
        }
    }
}
