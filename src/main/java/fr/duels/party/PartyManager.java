package fr.duels.party;

import fr.duels.DuelsPlugin;
import fr.duels.arena.Arena;
import fr.duels.kit.Kit;
import fr.duels.match.MatchType;
import fr.duels.util.Lang;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager {

    private final DuelsPlugin plugin;
    private final Map<UUID, Party> byPlayer = new ConcurrentHashMap<>();
    private final Set<Party> parties = new LinkedHashSet<>();

    public PartyManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public Party getParty(UUID uuid) {
        return byPlayer.get(uuid);
    }

    public Collection<Party> getParties() {
        return parties;
    }

    public Party create(Player player) {
        if (byPlayer.containsKey(player.getUniqueId())) {
            Lang.send(player, "party.already-in");
            return null;
        }
        Party party = new Party(player.getUniqueId());
        parties.add(party);
        byPlayer.put(player.getUniqueId(), party);
        plugin.getQueueManager().leave(player, false);
        Lang.send(player, "party.created");
        refresh(player);
        return party;
    }

    public void invite(Player leader, Player target) {
        Party party = getParty(leader.getUniqueId());
        if (party == null) {
            party = create(leader);
            if (party == null) {
                return;
            }
        }
        if (!party.isLeader(leader.getUniqueId())) {
            Lang.send(leader, "party.not-leader");
            return;
        }
        if (target.equals(leader)) {
            Lang.send(leader, "party.invite-self");
            return;
        }
        if (getParty(target.getUniqueId()) != null) {
            Lang.send(leader, "party.target-in-party", "player", target.getName());
            return;
        }
        if (party.size() >= getMaxSize()) {
            Lang.send(leader, "party.full");
            return;
        }
        if (party.hasInvite(target.getUniqueId())) {
            Lang.send(leader, "party.already-invited", "player", target.getName());
            return;
        }
        party.invite(target.getUniqueId(), plugin.getConfig().getLong("party.invite-expire", 60L) * 1000L);
        party.broadcast("party.invite-broadcast", "player", target.getName(), "leader", leader.getName());

        Lang.send(target, "party.invite-received", "leader", leader.getName());
        TextComponent accept = new TextComponent(Lang.get("party.invite-accept-button"));
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party accept " + leader.getName()));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(Lang.get("party.invite-accept-hover")).create()));
        target.spigot().sendMessage(new BaseComponent[]{new TextComponent(Lang.prefix()), accept});
    }

    public void join(Player player, String leaderName) {
        Player leader = Bukkit.getPlayerExact(leaderName);
        Party party = leader == null ? null : getParty(leader.getUniqueId());
        if (party == null) {
            Lang.send(player, "party.not-found", "player", leaderName);
            return;
        }
        if (getParty(player.getUniqueId()) != null) {
            Lang.send(player, "party.already-in");
            return;
        }
        if (!party.hasInvite(player.getUniqueId()) && !party.isOpen()) {
            Lang.send(player, "party.no-invite", "player", leaderName);
            return;
        }
        if (party.size() >= getMaxSize()) {
            Lang.send(player, "party.full");
            return;
        }
        party.removeInvite(player.getUniqueId());
        party.getMembers().add(player.getUniqueId());
        byPlayer.put(player.getUniqueId(), party);
        plugin.getQueueManager().leave(player, false);
        party.broadcast("party.joined", "player", player.getName());
        refreshAll(party);
    }

    public void leave(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) {
            Lang.send(player, "party.not-in");
            return;
        }
        party.broadcast("party.left", "player", player.getName());
        removeMember(party, player.getUniqueId());
    }

    private void removeMember(Party party, UUID uuid) {
        party.getMembers().remove(uuid);
        byPlayer.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            refresh(player);
        }
        if (party.getMembers().isEmpty()) {
            parties.remove(party);
            return;
        }
        if (party.isLeader(uuid)) {
            party.setLeader(party.getMembers().iterator().next());
            party.broadcast("party.new-leader", "player", party.getLeaderName());
        }
        refreshAll(party);
    }

    public void kick(Player leader, String targetName) {
        Party party = requireLeader(leader);
        if (party == null) {
            return;
        }
        UUID target = findMember(party, targetName);
        if (target == null) {
            Lang.send(leader, "party.not-member", "player", targetName);
            return;
        }
        if (target.equals(leader.getUniqueId())) {
            Lang.send(leader, "party.kick-self");
            return;
        }
        party.broadcast("party.kicked-broadcast", "player", targetName);
        removeMember(party, target);
        Player kicked = Bukkit.getPlayer(target);
        if (kicked != null) {
            Lang.send(kicked, "party.kicked");
        }
    }

    public void promote(Player leader, String targetName) {
        Party party = requireLeader(leader);
        if (party == null) {
            return;
        }
        UUID target = findMember(party, targetName);
        if (target == null || target.equals(leader.getUniqueId())) {
            Lang.send(leader, "party.not-member", "player", targetName);
            return;
        }
        party.setLeader(target);
        party.broadcast("party.new-leader", "player", party.getLeaderName());
        refreshAll(party);
    }

    public void disband(Player leader) {
        Party party = requireLeader(leader);
        if (party != null) {
            disband(party);
        }
    }

    public void disband(Party party) {
        party.broadcast("party.disbanded");
        parties.remove(party);
        for (UUID uuid : new ArrayList<>(party.getMembers())) {
            byPlayer.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                refresh(player);
            }
        }
        party.getMembers().clear();
    }

    public void toggleOpen(Player leader) {
        Party party = requireLeader(leader);
        if (party == null) {
            return;
        }
        party.setOpen(!party.isOpen());
        party.broadcast(party.isOpen() ? "party.opened" : "party.closed");
    }

    public void chat(Player player, String message) {
        Party party = getParty(player.getUniqueId());
        if (party == null) {
            Lang.send(player, "party.not-in");
            return;
        }
        String formatted = Lang.get("party.chat-format", "player", player.getName()).replace("{message}", message);
        for (Player member : party.getOnlineMembers()) {
            member.sendMessage(formatted);
        }
    }

    public void info(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) {
            Lang.send(player, "party.not-in");
            return;
        }
        List<String> names = new ArrayList<>();
        for (UUID uuid : party.getMembers()) {
            Player member = Bukkit.getPlayer(uuid);
            String name = member != null ? member.getName() : String.valueOf(Bukkit.getOfflinePlayer(uuid).getName());
            names.add((party.isLeader(uuid) ? "&6★ " : "&7") + name);
        }
        for (String line : Lang.list("party.info",
                "leader", party.getLeaderName(),
                "size", party.size(),
                "max", getMaxSize(),
                "members", Lang.color(String.join("&7, ", names)),
                "open", Lang.get(party.isOpen() ? "general.answer-yes" : "general.answer-no"))) {
            player.sendMessage(line);
        }
    }

    /** Lance un match interne à la party (FFA ou deux équipes). */
    public void startPartyFight(Player leader, MatchType type, Kit kit, Arena arena) {
        Party party = requireLeader(leader);
        if (party == null) {
            return;
        }
        List<Player> members = party.getOnlineMembers();
        if (members.size() < 2) {
            Lang.send(leader, "party.not-enough");
            return;
        }
        for (Player member : members) {
            if (plugin.getMatchManager().isBusy(member.getUniqueId())) {
                Lang.send(leader, "party.members-busy");
                return;
            }
        }
        List<List<Player>> teams = new ArrayList<>();
        if (type == MatchType.PARTY_FFA) {
            for (Player member : members) {
                teams.add(Collections.singletonList(member));
            }
        } else {
            Collections.shuffle(members);
            List<Player> a = new ArrayList<>();
            List<Player> b = new ArrayList<>();
            for (int i = 0; i < members.size(); i++) {
                (i % 2 == 0 ? a : b).add(members.get(i));
            }
            teams.add(a);
            teams.add(b);
        }
        if (plugin.getMatchManager().startMatch(type, kit, arena, teams) == null) {
            Lang.send(leader, arena == null ? "match.no-arena" : "match.arena-unavailable");
        }
    }

    public void handleQuit(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party != null && plugin.getConfig().getBoolean("party.leave-on-quit", true)) {
            party.broadcast("party.left", "player", player.getName());
            removeMember(party, player.getUniqueId());
        }
    }

    private Party requireLeader(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) {
            Lang.send(player, "party.not-in");
            return null;
        }
        if (!party.isLeader(player.getUniqueId())) {
            Lang.send(player, "party.not-leader");
            return null;
        }
        return party;
    }

    private UUID findMember(Party party, String name) {
        for (UUID uuid : party.getMembers()) {
            String memberName = Bukkit.getOfflinePlayer(uuid).getName();
            if (memberName != null && memberName.equalsIgnoreCase(name)) {
                return uuid;
            }
        }
        return null;
    }

    public int getMaxSize() {
        return plugin.getConfig().getInt("party.max-size", 16);
    }

    private void refresh(Player player) {
        if (player.isOnline() && !plugin.getMatchManager().isBusy(player.getUniqueId())) {
            plugin.getLobbyManager().giveItems(player);
        }
    }

    private void refreshAll(Party party) {
        for (Player member : party.getOnlineMembers()) {
            refresh(member);
        }
    }
}
