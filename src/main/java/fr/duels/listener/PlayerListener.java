package fr.duels.listener;

import fr.duels.DuelsPlugin;
import fr.duels.gui.Menu;
import fr.duels.gui.PartyMenu;
import fr.duels.gui.QueueMenu;
import fr.duels.gui.SpectateMenu;
import fr.duels.party.Party;
import fr.duels.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Protection du lobby, objets du lobby, connexions / déconnexions et chat de party. */
public class PlayerListener implements Listener {

    private final DuelsPlugin plugin;

    public PlayerListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Au lobby = ni en match, ni spectateur, ni en train d'éditer une arène. */
    private boolean inLobby(Player player) {
        return !plugin.getMatchManager().isBusy(player.getUniqueId())
                && !plugin.getWorldService().isTemplate(player.getWorld());
    }

    private boolean bypass(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("duels.admin");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.getConfig().getBoolean("lobby.teleport-on-join", true)
                    || plugin.getWorldService().isDuelsWorld(player.getWorld().getName())) {
                plugin.getLobbyManager().sendToLobby(player);
            } else {
                plugin.getLobbyManager().giveItems(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getQueueManager().leave(player, false);
        plugin.getMatchManager().handleQuit(player);
        plugin.getRequestManager().clear(player.getUniqueId());
        plugin.getPartyManager().handleQuit(player);
        if (plugin.getWorldService().isInstance(player.getWorld())) {
            // la copie de la map va être supprimée : on sauvegarde le joueur au lobby
            PlayerUtil.reset(player, plugin.getLobbyManager().getLobbyGameMode());
            player.teleport(plugin.getLobbyManager().getLobby());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!inLobby(player)) {
            return;
        }
        String action = plugin.getLobbyManager().getAction(event.getItem());
        if (action == null) {
            return;
        }
        event.setCancelled(true);
        switch (action) {
            case "queue":
                new QueueMenu(plugin).open(player);
                break;
            case "spectate":
                new SpectateMenu(plugin).open(player);
                break;
            case "party-create":
                plugin.getPartyManager().create(player);
                break;
            case "party-menu":
                new PartyMenu(plugin).open(player);
                break;
            case "party-leave":
                plugin.getPartyManager().leave(player);
                break;
            case "leave-queue":
                plugin.getQueueManager().leave(player, true);
                break;
            case "stats":
                player.performCommand("stats");
                break;
            default:
                break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (!inLobby(player) || bypass(player) || !plugin.getConfig().getBoolean("lobby.lock-inventory", true)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            return;
        }
        boolean ownInventory = event.getClickedInventory() != null
                && event.getClickedInventory().getType() == InventoryType.PLAYER;
        if (ownInventory || event.getClick() == ClickType.NUMBER_KEY
                || event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (inLobby(player) && !bypass(player) && plugin.getConfig().getBoolean("lobby.lock-inventory", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        if (inLobby(player) && !bypass(player) && plugin.getConfig().getBoolean("lobby.lock-inventory", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (!inLobby(player) || !plugin.getConfig().getBoolean("lobby.disable-damage", true)) {
            return;
        }
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            player.setFallDistance(0f);
            player.teleport(plugin.getLobbyManager().getLobby());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && inLobby((Player) event.getEntity())
                && plugin.getConfig().getBoolean("lobby.disable-hunger", true)) {
            event.setCancelled(true);
            ((Player) event.getEntity()).setFoodLevel(20);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (inLobby(player) && !bypass(player) && plugin.getConfig().getBoolean("lobby.protect-blocks", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (inLobby(player) && !bypass(player) && plugin.getConfig().getBoolean("lobby.protect-blocks", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String prefix = plugin.getConfig().getString("party.chat-prefix", "@");
        if (prefix.isEmpty() || !event.getMessage().startsWith(prefix) || event.getMessage().length() <= prefix.length()) {
            return;
        }
        final Player player = event.getPlayer();
        Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            return;
        }
        final String message = event.getMessage().substring(prefix.length()).trim();
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getPartyManager().chat(player, message));
    }
}
