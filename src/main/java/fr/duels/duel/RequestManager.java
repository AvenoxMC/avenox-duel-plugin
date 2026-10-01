package fr.duels.duel;

import fr.duels.DuelsPlugin;
import fr.duels.arena.Arena;
import fr.duels.kit.Kit;
import fr.duels.match.MatchType;
import fr.duels.party.Party;
import fr.duels.util.Lang;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Défis directs : joueur contre joueur, ou party contre party (entre chefs). */
public class RequestManager {

    private final DuelsPlugin plugin;
    /** cible -> (expéditeur -> défi) */
    private final Map<UUID, Map<UUID, DuelRequest>> requests = new HashMap<>();

    public RequestManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(Player sender, Player target, Kit kit, Arena arena) {
        if (sender.equals(target)) {
            Lang.send(sender, "duel.self");
            return;
        }
        if (plugin.getMatchManager().isBusy(sender.getUniqueId())) {
            Lang.send(sender, "general.busy");
            return;
        }
        if (plugin.getMatchManager().isBusy(target.getUniqueId())) {
            Lang.send(sender, "duel.target-busy", "player", target.getName());
            return;
        }
        Party senderParty = plugin.getPartyManager().getParty(sender.getUniqueId());
        Party targetParty = plugin.getPartyManager().getParty(target.getUniqueId());
        boolean partyDuel = false;
        if (senderParty != null) {
            if (!senderParty.isLeader(sender.getUniqueId())) {
                Lang.send(sender, "party.not-leader");
                return;
            }
            if (targetParty == null || !targetParty.isLeader(target.getUniqueId())) {
                Lang.send(sender, "duel.target-not-party-leader", "player", target.getName());
                return;
            }
            if (senderParty == targetParty) {
                Lang.send(sender, "duel.same-party");
                return;
            }
            partyDuel = true;
        } else if (targetParty != null) {
            Lang.send(sender, "duel.target-in-party", "player", target.getName());
            return;
        }
        Map<UUID, DuelRequest> map = requests.computeIfAbsent(target.getUniqueId(), k -> new HashMap<>());
        DuelRequest existing = map.get(sender.getUniqueId());
        if (existing != null && !existing.isExpired()) {
            Lang.send(sender, "duel.already-sent", "player", target.getName());
            return;
        }
        long expire = plugin.getConfig().getLong("duel.request-expire", 60L) * 1000L;
        map.put(sender.getUniqueId(), new DuelRequest(sender.getUniqueId(), target.getUniqueId(), kit.getName(),
                arena == null ? null : arena.getName(), partyDuel, System.currentTimeMillis() + expire));

        String arenaName = arena == null ? Lang.get("general.random-arena") : arena.getDisplayName();
        if (partyDuel) {
            Lang.send(sender, "duel.party-sent", "player", target.getName(), "kit", kit.getDisplayName(), "arena", arenaName);
            Lang.send(target, "duel.party-received", "player", sender.getName(), "kit", kit.getDisplayName(),
                    "arena", arenaName, "size", senderParty.size());
        } else {
            Lang.send(sender, "duel.sent", "player", target.getName(), "kit", kit.getDisplayName(), "arena", arenaName);
            Lang.send(target, "duel.received", "player", sender.getName(), "kit", kit.getDisplayName(), "arena", arenaName);
        }

        TextComponent accept = new TextComponent(Lang.get("duel.accept-button"));
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel accept " + sender.getName()));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(Lang.get("duel.accept-hover")).create()));
        TextComponent deny = new TextComponent(Lang.get("duel.deny-button"));
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel deny " + sender.getName()));
        deny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(Lang.get("duel.deny-hover")).create()));
        target.spigot().sendMessage(new BaseComponent[]{new TextComponent(Lang.prefix()), accept, new TextComponent(" "), deny});
    }

    public void accept(Player target, String senderName) {
        Player sender = Bukkit.getPlayerExact(senderName);
        DuelRequest request = sender == null ? null : take(target.getUniqueId(), sender.getUniqueId());
        if (request == null || request.isExpired()) {
            Lang.send(target, "duel.no-request", "player", senderName);
            return;
        }
        Kit kit = plugin.getKitManager().get(request.getKit());
        if (kit == null || !kit.isEnabled()) {
            Lang.send(target, "duel.kit-removed");
            return;
        }
        Arena arena = request.getArena() == null ? null : plugin.getArenaManager().get(request.getArena());

        List<List<Player>> teams;
        MatchType type;
        if (request.isParty()) {
            Party senderParty = plugin.getPartyManager().getParty(sender.getUniqueId());
            Party targetParty = plugin.getPartyManager().getParty(target.getUniqueId());
            if (senderParty == null || targetParty == null || !senderParty.isLeader(sender.getUniqueId())
                    || !targetParty.isLeader(target.getUniqueId()) || senderParty == targetParty) {
                Lang.send(target, "duel.party-changed");
                return;
            }
            List<Player> a = senderParty.getOnlineMembers();
            List<Player> b = targetParty.getOnlineMembers();
            if (anyBusy(a) || anyBusy(b)) {
                Lang.send(target, "party.members-busy");
                Lang.send(sender, "party.members-busy");
                return;
            }
            teams = Arrays.asList(a, b);
            type = MatchType.PARTY_VS_PARTY;
        } else {
            if (plugin.getPartyManager().getParty(sender.getUniqueId()) != null
                    || plugin.getPartyManager().getParty(target.getUniqueId()) != null) {
                Lang.send(target, "duel.party-changed");
                return;
            }
            if (plugin.getMatchManager().isBusy(sender.getUniqueId()) || plugin.getMatchManager().isBusy(target.getUniqueId())) {
                Lang.send(target, "duel.target-busy", "player", sender.getName());
                return;
            }
            teams = Arrays.asList(Collections.singletonList(sender), Collections.singletonList(target));
            type = MatchType.DUEL;
        }

        if (plugin.getMatchManager().startMatch(type, kit, arena, teams) == null) {
            String key = arena == null ? "match.no-arena" : "match.arena-unavailable";
            Lang.send(target, key);
            Lang.send(sender, key);
        }
    }

    public void deny(Player target, String senderName) {
        Player sender = Bukkit.getPlayerExact(senderName);
        DuelRequest request = sender == null ? null : take(target.getUniqueId(), sender.getUniqueId());
        if (request == null) {
            Lang.send(target, "duel.no-request", "player", senderName);
            return;
        }
        Lang.send(target, "duel.denied", "player", sender.getName());
        Lang.send(sender, "duel.denied-sender", "player", target.getName());
    }

    private DuelRequest take(UUID target, UUID sender) {
        Map<UUID, DuelRequest> map = requests.get(target);
        return map == null ? null : map.remove(sender);
    }

    private boolean anyBusy(List<Player> players) {
        for (Player player : players) {
            if (plugin.getMatchManager().isBusy(player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    /** Supprime toutes les demandes envoyées ou reçues par ce joueur. */
    public void clear(UUID uuid) {
        requests.remove(uuid);
        for (Iterator<Map<UUID, DuelRequest>> it = requests.values().iterator(); it.hasNext(); ) {
            Map<UUID, DuelRequest> map = it.next();
            map.remove(uuid);
            if (map.isEmpty()) {
                it.remove();
            }
        }
    }
}
