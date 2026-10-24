package org.micahcode.teamTP;

import org.bukkit.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpawnStoreTest {
    @TempDir Path directory;

    static World world() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getName()).thenReturn("arena");
        when(world.getUID()).thenReturn(UUID.randomUUID());
        WorldBorder border = mock(WorldBorder.class);
        when(border.isInside(any(Location.class))).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        return world;
    }

    @Test
    void roundTripsExactCoordinatesWorldAndDirection() throws Exception {
        World world = world();
        Team team = new Team("a", "A", "team_a");
        Path file = directory.resolve("spawns.yml");
        Location expected = new Location(world, -120.25, 70.5, 300.75, 135, -20);
        new SpawnStore(file).save(team, expected);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(world.getUID())).thenReturn(world);
            assertEquals(expected, new SpawnStore(file).get(team));
        }
    }

    @Test
    void rejectsNonFiniteAndOutOfBoundsCoordinatesWithoutChangingSavedFile() throws Exception {
        World world = world();
        Team team = new Team("a", "A", "team_a");
        Path file = directory.resolve("spawns.yml");
        SpawnStore store = new SpawnStore(file);
        store.save(team, new Location(world, 0.5, 70, 0.5));
        String original = Files.readString(file);
        for (double x : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 30_000_000}) {
            assertThrows(IllegalArgumentException.class, () -> store.save(team, new Location(world, x, 70, 0)));
        }
        assertThrows(IllegalArgumentException.class, () -> store.save(team, new Location(world, 0, 400, 0)));
        assertEquals(original, Files.readString(file));
    }

    @Test
    void corruptYamlDoesNotSilentlyResetSpawns() throws Exception {
        Path file = directory.resolve("spawns.yml");
        Files.writeString(file, "teams: [unclosed");
        assertThrows(Exception.class, () -> new SpawnStore(file));
        assertEquals("teams: [unclosed", Files.readString(file));
    }

    @Test
    void missingAndUnloadedWorldsAreExplicitFailures() throws Exception {
        SpawnStore store = new SpawnStore(directory.resolve("spawns.yml"));
        Team team = new Team("a", "A", "team_a");
        assertThrows(IllegalArgumentException.class, () -> store.get(team));
        store.save(team, new Location(world(), 0.5, 70, 0.5));
        try (var ignored = mockStatic(Bukkit.class)) {
            assertThrows(IllegalArgumentException.class, () -> store.get(team));
        }
    }
}
