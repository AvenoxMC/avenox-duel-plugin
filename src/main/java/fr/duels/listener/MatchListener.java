package fr.duels.listener;

import fr.duels.DuelsPlugin;
import fr.duels.match.Match;
import fr.duels.match.MatchState;
import fr.duels.match.MatchTeam;
import fr.duels.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;

/** Règles de combat à l'intérieur d'un match. */
public class MatchListener implements Listener {

    private final DuelsPlugin plugin;

    public MatchListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    private Match matchOf(Player player) {
        return plugin.getMatchManager().getMatch(player.getUniqueId());
    }

    private static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Dégâts et morts
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (plugin.getMatchManager().getSpectating(attacker.getUniqueId()) != null) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Match match = matchOf(victim);
        Match attackerMatch = matchOf(attacker);
        if (match == null && attackerMatch == null) {
            return;
        }
        if (match == null || match != attackerMatch || match.getState() != MatchState.FIGHTING
                || !match.isAlive(attacker.getUniqueId()) || !match.isAlive(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        MatchTeam team = match.getTeam(victim.getUniqueId());
        if (!attacker.equals(victim) && team != null && team.getMembers().contains(attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!attacker.equals(victim)) {
            match.recordDamage(victim, attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (plugin.getMatchManager().getSpectating(player.getUniqueId()) != null) {
            event.setCancelled(true);
            return;
        }
        Match match = matchOf(player);
        if (match == null) {
            return;
        }
        if (!match.isAlive(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (match.getState() != MatchState.FIGHTING) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && match.getWorld() != null) {
                player.teleport(match.getSpawnFor(player));
            }
            return;
        }
        boolean isVoid = event.getCause() == EntityDamageEvent.DamageCause.VOID;
        if (match.getKit().isNoDamage() && !isVoid) {
            event.setDamage(0);
            return;
        }
        if (isVoid || event.getFinalDamage() >= player.getHealth()) {
            event.setCancelled(true);
            match.eliminate(player, Match.Elimination.DEATH);
        }
    }

    /** Filet de sécurité (ex : /kill) : si un joueur meurt malgré tout, on le traite comme éliminé. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        Match match = matchOf(player);
        if (match == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setDeathMessage(null);
        match.eliminate(player, Match.Elimination.DEATH);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final Match match = matchOf(player);
        if (match == null || match.getWorld() == null || match.getState() == MatchState.ENDED) {
            return;
        }
        event.setRespawnLocation(match.getSpectatorLocation());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (matchOf(player) == match && match.getState() != MatchState.ENDED) {
                match.makeDeadSpectator(player);
            } else {
                plugin.getLobbyManager().sendToLobby(player);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        Match match = matchOf(player);
        if (match != null && (!match.getKit().isHunger() || match.getState() != MatchState.FIGHTING)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Match match = matchOf((Player) event.getEntity());
        if (match != null && !match.getKit().isRegen()
                && (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Déplacements
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()
                && Math.abs(from.getX() - to.getX()) < 0.01 && Math.abs(from.getZ() - to.getZ()) < 0.01) {
            return;
        }
        Player player = event.getPlayer();
        Match match = matchOf(player);
        if (match == null || !match.isAlive(player.getUniqueId())) {
            return;
        }
        if (match.getState() == MatchState.STARTING) {
            // gel pendant le compte à rebours (la tête peut bouger)
            if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                Location back = from.clone();
                back.setYaw(to.getYaw());
                back.setPitch(to.getPitch());
                event.setTo(back);
            }
            return;
        }
        if (match.getState() != MatchState.FIGHTING) {
            return;
        }
        if (to.getY() < match.getArena().getVoidY()) {
            match.eliminate(player, Match.Elimination.DEATH);
            return;
        }
        if (match.getKit().isWaterKills()) {
            Material type = to.getBlock().getType();
            if (type == Material.WATER || type == Material.STATIONARY_WATER) {
                match.eliminate(player, Match.Elimination.DEATH);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // en mode spectateur, la 1.8 permet de se téléporter vers n'importe quel joueur du serveur
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        Player player = event.getPlayer();
        Match match = matchOf(player);
        if (match == null) {
            match = plugin.getMatchManager().getSpectating(player.getUniqueId());
        }
        if (match != null && match.getWorld() != null && !match.getWorld().equals(event.getTo().getWorld())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private boolean canBuild(Player player, Match match, Block block) {
        if (match.getState() != MatchState.FIGHTING || !match.isAlive(player.getUniqueId()) || !match.getKit().isBuild()) {
            return false;
        }
        int limit = match.getArena().getBuildLimitY();
        if (limit > 0 && block.getY() > limit) {
            Lang.send(player, "match.build-limit");
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match == null) {
            return;
        }
        if (!canBuild(event.getPlayer(), match, event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);
        match.addPlacedBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match == null) {
            return;
        }
        Block block = event.getBlock();
        if (!canBuild(event.getPlayer(), match, block)
                || (!match.getKit().isBreakMap() && !match.isPlacedBlock(block))) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);
        match.removePlacedBlock(block);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match == null) {
            return;
        }
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (!canBuild(event.getPlayer(), match, target)) {
            event.setCancelled(true);
            return;
        }
        match.addPlacedBlock(target);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match == null) {
            return;
        }
        Block block = event.getBlockClicked();
        if (!canBuild(event.getPlayer(), match, block)
                || (!match.getKit().isBreakMap() && !match.isPlacedBlock(block))) {
            event.setCancelled(true);
            return;
        }
        match.removePlacedBlock(block);
    }

    /** L'eau et la lave posées par les joueurs coulent : les blocs atteints deviennent "posés". */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (!plugin.getWorldService().isInstance(event.getBlock().getWorld())) {
            return;
        }
        Match match = plugin.getMatchManager().getMatchByWorld(event.getBlock().getWorld());
        if (match != null && match.isPlacedBlock(event.getBlock())) {
            match.addPlacedBlock(event.getToBlock());
        }
    }

    /** Cobblestone / obsidienne générées par eau + lave : cassables par les joueurs. */
    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (!plugin.getWorldService().isInstance(event.getBlock().getWorld())) {
            return;
        }
        Match match = plugin.getMatchManager().getMatchByWorld(event.getBlock().getWorld());
        if (match != null) {
            match.addPlacedBlock(event.getBlock());
        }
    }

    // ------------------------------------------------------------------
    // Divers
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match != null && (!plugin.getConfig().getBoolean("match.allow-item-drop", true)
                || match.getState() != MatchState.FIGHTING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("duels.admin") || !plugin.getMatchManager().isBusy(player.getUniqueId())) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        List<String> allowed = plugin.getConfig().getStringList("match.allowed-commands");
        for (String command : allowed) {
            if (command.equalsIgnoreCase(label)) {
                return;
            }
        }
        event.setCancelled(true);
        Lang.send(player, "match.command-blocked");
    }
}
