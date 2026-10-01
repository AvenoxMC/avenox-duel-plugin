package fr.duels.gui;

import fr.duels.DuelsPlugin;
import fr.duels.match.MatchType;
import fr.duels.party.Party;
import fr.duels.util.ItemBuilder;
import fr.duels.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Menu de party : lancer un FFA / un match en équipes / défier une autre party, gérer les membres. */
public class PartyMenu extends Menu {

    public PartyMenu(DuelsPlugin plugin) {
        super(plugin, Lang.raw("menu.party.title"), 6);
    }

    @Override
    protected void build(Player viewer) {
        final Party party = plugin.getPartyManager().getParty(viewer.getUniqueId());
        if (party == null) {
            setRows(3);
            set(13, new ItemBuilder(Material.NAME_TAG).name(Lang.raw("menu.party.create"))
                    .lore(Lang.list("menu.party.create-lore")).build(), (player, click) -> {
                if (plugin.getPartyManager().create(player) != null) {
                    new PartyMenu(plugin).open(player);
                }
            });
            return;
        }
        boolean leader = party.isLeader(viewer.getUniqueId());

        set(4, ItemBuilder.skull(party.getLeaderName())
                .name(Lang.raw("menu.party.info-name").replace("{leader}", party.getLeaderName()))
                .lore(Lang.list("menu.party.info-lore",
                        "size", party.size(),
                        "max", plugin.getPartyManager().getMaxSize(),
                        "open", Lang.get(party.isOpen() ? "general.answer-yes" : "general.answer-no")))
                .build());

        if (leader) {
            set(19, new ItemBuilder(Material.GOLD_SWORD).name(Lang.raw("menu.party.ffa"))
                    .lore(Lang.list("menu.party.ffa-lore")).hideFlags().build(),
                    (player, click) -> openFight(player, MatchType.PARTY_FFA));
            set(21, new ItemBuilder(Material.IRON_SWORD).name(Lang.raw("menu.party.split"))
                    .lore(Lang.list("menu.party.split-lore")).hideFlags().build(),
                    (player, click) -> openFight(player, MatchType.PARTY_SPLIT));
            set(23, new ItemBuilder(Material.DIAMOND_SWORD).name(Lang.raw("menu.party.versus"))
                    .lore(Lang.list("menu.party.versus-lore")).hideFlags().build(),
                    (player, click) -> new PartyListMenu(plugin).open(player));
            set(25, new ItemBuilder(Material.INK_SACK, 1, (short) (party.isOpen() ? 10 : 8))
                    .name(Lang.raw(party.isOpen() ? "menu.party.open" : "menu.party.closed"))
                    .lore(Lang.list("menu.party.toggle-lore")).build(), (player, click) -> {
                plugin.getPartyManager().toggleOpen(player);
                new PartyMenu(plugin).open(player);
            });
        } else {
            set(22, new ItemBuilder(Material.BOOK).name(Lang.raw("menu.party.member-info"))
                    .lore(Lang.list("menu.party.member-info-lore")).build());
        }

        List<UUID> members = new ArrayList<>(party.getMembers());
        for (int i = 0; i < members.size() && i < 18; i++) {
            final UUID uuid = members.get(i);
            final String name = String.valueOf(Bukkit.getOfflinePlayer(uuid).getName());
            ItemBuilder head = ItemBuilder.skull(name)
                    .name((party.isLeader(uuid) ? "&6★ " : "&e") + name);
            if (leader && !party.isLeader(uuid)) {
                head.lore(Lang.list("menu.party.member-lore-leader"));
            }
            set(27 + i, head.build(), (player, click) -> {
                Party current = plugin.getPartyManager().getParty(player.getUniqueId());
                if (current == null || !current.isLeader(player.getUniqueId()) || current.isLeader(uuid)) {
                    return;
                }
                if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                    plugin.getPartyManager().kick(player, name);
                } else if (click == ClickType.RIGHT) {
                    plugin.getPartyManager().promote(player, name);
                } else {
                    return;
                }
                new PartyMenu(plugin).open(player);
            });
        }

        if (leader) {
            set(49, new ItemBuilder(Material.TNT).name(Lang.raw("menu.party.disband")).build(), (player, click) -> {
                player.closeInventory();
                plugin.getPartyManager().disband(player);
            });
        } else {
            set(49, new ItemBuilder(Material.INK_SACK, 1, (short) 1).name(Lang.raw("menu.party.leave")).build(), (player, click) -> {
                player.closeInventory();
                plugin.getPartyManager().leave(player);
            });
        }
    }

    private void openFight(Player player, final MatchType type) {
        new KitSelectMenu(plugin, Lang.raw("menu.kit-select.title"), (p, kit) ->
                ArenaSelectMenu.openOrSkip(plugin, p, kit, (p2, arena) ->
                        plugin.getPartyManager().startPartyFight(p2, type, kit, arena))).open(player);
    }
}
