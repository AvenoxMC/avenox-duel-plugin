package fr.duels.util;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public final class PlayerUtil {

    private PlayerUtil() {
    }

    /** Remet un joueur dans un état "propre" (inventaire vide, vie pleine, aucun effet). */
    public static void reset(Player player, GameMode gameMode) {
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory().getType() == InventoryType.CRAFTING) {
            player.getOpenInventory().getTopInventory().clear();
        }
        player.closeInventory();
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        if (player.getGameMode() != gameMode) {
            player.setGameMode(gameMode);
        }
        if (!player.isDead()) {
            player.setHealth(player.getMaxHealth());
        }
        player.setFoodLevel(20);
        player.setSaturation(10f);
        player.setExhaustion(0f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setLevel(0);
        player.setExp(0f);
        player.setMaximumNoDamageTicks(20);
        if (gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        player.updateInventory();
    }

    public static String hearts(Player player) {
        double hearts = Math.round(player.getHealth()) / 2.0;
        return (hearts == Math.floor(hearts) ? String.valueOf((int) hearts) : String.valueOf(hearts));
    }

    public static String formatTime(long seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
