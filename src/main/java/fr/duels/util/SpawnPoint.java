package fr.duels.util;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/** Position sans monde : une arène est rejouée dans une copie de monde différente à chaque match. */
public final class SpawnPoint {

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public SpawnPoint(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static SpawnPoint of(Location location) {
        return new SpawnPoint(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public static SpawnPoint parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String[] parts = text.split(",");
        if (parts.length < 3) {
            return null;
        }
        try {
            return new SpawnPoint(
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    parts.length > 3 ? Float.parseFloat(parts[3]) : 0f,
                    parts.length > 4 ? Float.parseFloat(parts[4]) : 0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public double getY() {
        return y;
    }

    public String serialize() {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.1f,%.1f", x, y, z, yaw, pitch);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%.1f / %.1f / %.1f", x, y, z);
    }
}
