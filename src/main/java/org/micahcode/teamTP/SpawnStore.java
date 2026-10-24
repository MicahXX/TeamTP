package org.micahcode.teamTP;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.*;

public final class SpawnStore {
    private final Path file;
    private YamlConfiguration data;

    public SpawnStore(Path file) throws Exception {
        this.file = file;
        data = new YamlConfiguration();
        if (Files.exists(file)) data.load(file.toFile());
    }

    public Location get(Team team) {
        String path = "teams." + team.id();
        if (!data.isConfigurationSection(path)) throw new IllegalArgumentException("No spawn set for " + team.id() + ".");
        World world = null;
        String uuid = data.getString(path + ".world-uuid");
        if (uuid != null) {
            try { world = Bukkit.getWorld(java.util.UUID.fromString(uuid)); }
            catch (IllegalArgumentException ignored) {  }
        }
        if (world == null) world = Bukkit.getWorld(data.getString(path + ".world", ""));
        if (world == null) throw new IllegalArgumentException("Spawn world is not loaded for " + team.id() + ".");
        Location location = new Location(world, number(path + ".x"), number(path + ".y"), number(path + ".z"),
                (float) number(path + ".yaw"), (float) number(path + ".pitch"));
        validate(location);
        return location;
    }

    private double number(String path) {
        Object value = data.get(path);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("Invalid spawn coordinate: " + path);
        }
        return number.doubleValue();
    }

    public static void validate(Location location) {
        World world = location.getWorld();
        if (world == null || !Double.isFinite(location.getX()) || !Double.isFinite(location.getY())
                || !Double.isFinite(location.getZ()) || !Float.isFinite(location.getYaw())
                || !Float.isFinite(location.getPitch()) || Math.abs(location.getX()) > 29_999_984
                || Math.abs(location.getZ()) > 29_999_984 || location.getY() < world.getMinHeight() + 1
                || location.getY() >= world.getMaxHeight() - 2 || !world.getWorldBorder().isInside(location)) {
            throw new IllegalArgumentException("Spawn must be finite, inside the world border and within build height.");
        }
    }

    public void save(Team team, Location location) throws Exception {
        validate(location);
        YamlConfiguration next = new YamlConfiguration();
        next.loadFromString(data.saveToString());
        String path = "teams." + team.id();
        next.set(path + ".world", location.getWorld().getName());
        next.set(path + ".world-uuid", location.getWorld().getUID().toString());
        next.set(path + ".x", location.getX());
        next.set(path + ".y", location.getY());
        next.set(path + ".z", location.getZ());
        next.set(path + ".yaw", location.getYaw());
        next.set(path + ".pitch", location.getPitch());
        Path temporary = Files.createTempFile(file.getParent(), "spawns-", ".tmp");
        try {
            next.save(temporary.toFile());
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            data = next;
        } catch (IOException error) {
            throw new IOException("Could not atomically save spawns.yml; previous spawns retained.", error);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
