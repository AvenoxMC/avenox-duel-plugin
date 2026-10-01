package fr.duels.gui;

import fr.duels.DuelsPlugin;
import fr.duels.party.Party;
import fr.duels.util.ItemBuilder;
import fr.duels.util.Lang;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Liste des autres parties disponibles, pour les défier. */
public class PartyListMenu extends Menu {

    public PartyListMenu(DuelsPlugin plugin) {
        super(plugin, Lang.raw("menu.party-list.title"), 3);
    }

    @Override
    protected void build(Player viewer) {
        Party own = plugin.getPartyManager().getParty(viewer.getUniqueId());
        List<Party> parties = new ArrayList<>();
        for (Party party : plugin.getPartyManager().getParties()) {
            Player leader = party.getLeaderPlayer();
            if (party != own && leader != null && !plugin.getMatchManager().isBusy(leader.getUniqueId())) {
                parties.add(party);
            }
        }
        setRows(rowsFor(Math.max(1, parties.size())));
        if (parties.isEmpty()) {
            set(13, new ItemBuilder(Material.BARRIER).name(Lang.raw("menu.party-list.empty")).build());
            return;
        }
        int[] slots = slotsFor(parties.size());
        for (int i = 0; i < slots.length; i++) {
            final Party party = parties.get(i);
            List<String> names = new ArrayList<>();
            for (Player member : party.getOnlineMembers()) {
                names.add(member.getName());
            }
            set(slots[i], ItemBuilder.skull(party.getLeaderName())
                    .name(Lang.raw("menu.party-list.name").replace("{leader}", party.getLeaderName()))
                    .lore(Lang.list("menu.party-list.lore", "size", party.size(), "members", String.join(", ", names)))
                    .build(), (player, click) -> {
                final Player leader = party.getLeaderPlayer();
                if (leader == null) {
                    player.closeInventory();
                    return;
                }
                new KitSelectMenu(plugin, Lang.raw("menu.kit-select.title"), (p, kit) ->
                        ArenaSelectMenu.openOrSkip(plugin, p, kit, (p2, arena) ->
                                plugin.getRequestManager().send(p2, leader, kit, arena))).open(player);
            });
        }
    }
}
