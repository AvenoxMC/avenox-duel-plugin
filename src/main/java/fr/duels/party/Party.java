package fr.duels.party;

import fr.duels.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Party {

    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Map<UUID, Long> invites = new HashMap<>();
    private boolean open;

    public Party(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public int size() {
        return members.size();
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public void invite(UUID uuid, long expireMillis) {
        invites.put(uuid, System.currentTimeMillis() + expireMillis);
    }

    public boolean hasInvite(UUID uuid) {
        Long expire = invites.get(uuid);
        if (expire == null) {
            return false;
        }
        if (expire < System.currentTimeMillis()) {
            invites.remove(uuid);
            return false;
        }
        return true;
    }

    public void removeInvite(UUID uuid) {
        invites.remove(uuid);
    }

    public Player getLeaderPlayer() {
        return Bukkit.getPlayer(leader);
    }

    public String getLeaderName() {
        Player player = getLeaderPlayer();
        return player != null ? player.getName() : String.valueOf(Bukkit.getOfflinePlayer(leader).getName());
    }

    public List<Player> getOnlineMembers() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                list.add(player);
            }
        }
        return list;
    }

    public void broadcast(String key, Object... placeholders) {
        String message = Lang.get(key, placeholders);
        if (message.isEmpty()) {
            return;
        }
        String full = Lang.get("party.prefix") + message;
        for (Player player : getOnlineMembers()) {
            player.sendMessage(full);
        }
    }
}
