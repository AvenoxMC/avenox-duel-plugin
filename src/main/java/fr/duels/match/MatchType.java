package fr.duels.match;

import fr.duels.util.Lang;

public enum MatchType {
    /** 1v1 (file d'attente ou défi). */
    DUEL,
    /** Party : chacun pour soi. */
    PARTY_FFA,
    /** Party coupée en deux équipes. */
    PARTY_SPLIT,
    /** Party contre party. */
    PARTY_VS_PARTY;

    public String getDisplayName() {
        return Lang.get("match-types." + name().toLowerCase());
    }
}
