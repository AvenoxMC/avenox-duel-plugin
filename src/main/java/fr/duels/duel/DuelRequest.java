package fr.duels.duel;

import java.util.UUID;

public class DuelRequest {

    private final UUID sender;
    private final UUID target;
    private final String kit;
    private final String arena;
    private final boolean party;
    private final long expiresAt;

    public DuelRequest(UUID sender, UUID target, String kit, String arena, boolean party, long expiresAt) {
        this.sender = sender;
        this.target = target;
        this.kit = kit;
        this.arena = arena;
        this.party = party;
        this.expiresAt = expiresAt;
    }

    public UUID getSender() {
        return sender;
    }

    public UUID getTarget() {
        return target;
    }

    public String getKit() {
        return kit;
    }

    public String getArena() {
        return arena;
    }

    public boolean isParty() {
        return party;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
