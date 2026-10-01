package fr.duels.stats;

public class PlayerStats {

    private String name;
    private int wins;
    private int losses;
    private int kills;
    private int deaths;

    public PlayerStats(String name, int wins, int losses, int kills, int deaths) {
        this.name = name;
        this.wins = wins;
        this.losses = losses;
        this.kills = kills;
        this.deaths = deaths;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public void addWin() {
        wins++;
    }

    public void addLoss() {
        losses++;
    }

    public void addKill() {
        kills++;
    }

    public void addDeath() {
        deaths++;
    }

    public String getRatio() {
        double ratio = deaths == 0 ? kills : (double) kills / deaths;
        return String.format(java.util.Locale.ROOT, "%.2f", ratio);
    }
}
