package fr.duels.match;

public enum MatchState {
    /** Copie et chargement du monde en cours. */
    LOADING,
    /** Joueurs téléportés, compte à rebours (immobiles). */
    STARTING,
    /** Combat en cours. */
    FIGHTING,
    /** Vainqueur annoncé, retour au lobby imminent. */
    ENDING,
    /** Match terminé et nettoyé. */
    ENDED
}
