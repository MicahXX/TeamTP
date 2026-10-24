package org.micahcode.teamTP;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafeDestinationsTest {
    private World platform() {
        World world = SpawnStoreTest.world();
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Material solid = mock(Material.class);
        when(solid.isOccluding()).thenReturn(true);
        when(solid.isSolid()).thenReturn(true);
        Block floor = mock(Block.class);
        when(floor.getType()).thenReturn(solid);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(mock(Material.class));
        when(air.isEmpty()).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> (int) call.getArgument(1) == 69 ? floor : air);
        return world;
    }

    @Test
    void acceptsClearFlatPlatform() {
        assertTrue(SafeDestinations.valid(new Location(platform(), 0.5, 70, 0.5), true));
    }

    @Test
    void refusesMissingFloorObstructedHeadroomAndAdjacentHazards() {
        World world = platform();
        Block obstruction = mock(Block.class);
        when(obstruction.getType()).thenReturn(Material.LAVA);
        when(world.getBlockAt(1, 70, 0)).thenReturn(obstruction);
        assertFalse(SafeDestinations.valid(new Location(world, 0.5, 70, 0.5), true));
        World obstructed = platform();
        when(obstructed.getBlockAt(0, 71, 0)).thenReturn(obstruction);
        assertFalse(SafeDestinations.valid(new Location(obstructed, 0.5, 70, 0.5), true));
        World hole = platform();
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(mock(Material.class));
        when(hole.getBlockAt(0, 69, 0)).thenReturn(air);
        assertFalse(SafeDestinations.valid(new Location(hole, 0.5, 70, 0.5), true));
    }

    @Test
    void neverChecksUnloadedChunksOrBypassesBorderWhenSafetyDisabled() {
        World world = platform();
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertFalse(SafeDestinations.valid(new Location(world, 0.5, 70, 0.5), false));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        when(world.getWorldBorder().isInside(any(Location.class))).thenReturn(false);
        assertFalse(SafeDestinations.valid(new Location(world, 0.5, 70, 0.5), false));
    }

    @Test
    void asynchronouslyPreparesSpreadAndReleasesEveryTicket() {
        World world = platform();
        JavaPlugin plugin = mock(JavaPlugin.class);
        List<Chunk> loaded = new ArrayList<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(true))).thenAnswer(call -> {
            Chunk chunk = mock(Chunk.class);
            loaded.add(chunk);
            return CompletableFuture.completedFuture(chunk);
        });
        SafeDestinations destinations = new SafeDestinations(plugin, Runnable::run);
        List<Location> spots = destinations.prepare(new Location(world, 0.5, 70, 0.5, 90, 15),
                Settings.parse(SettingsTest.defaults())).join();
        assertEquals(121, spots.size());
        assertEquals(new Location(world, 0.5, 70, 0.5, 90, 15), spots.getFirst());
        assertEquals(4, loaded.size());
        destinations.close();
        for (Chunk chunk : loaded) {
            verify(chunk).addPluginChunkTicket(plugin);
            verify(chunk).removePluginChunkTicket(plugin);
        }
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    void closingDuringLoadPreventsLateTicketAcquisition() {
        World world = platform();
        JavaPlugin plugin = mock(JavaPlugin.class);
        CompletableFuture<Chunk> loading = new CompletableFuture<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(true))).thenReturn(loading);
        SafeDestinations destinations = new SafeDestinations(plugin, Runnable::run);
        var preparing = destinations.prepare(new Location(world, 0.5, 70, 0.5), Settings.parse(SettingsTest.defaults()));
        destinations.close();
        Chunk chunk = mock(Chunk.class);
        loading.complete(chunk);
        assertTrue(preparing.isCompletedExceptionally());
        verify(chunk, never()).addPluginChunkTicket(plugin);
        verify(world, times(1)).getChunkAtAsync(anyInt(), anyInt(), eq(true));
    }

    @Test
    void refusesUngeneratedChunksWhenGenerationIsDisabled() {
        World world = platform();
        var config = SettingsTest.defaults();
        config.set("teleport.generate-chunks", false);
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false))).thenReturn(CompletableFuture.completedFuture(null));
        try (SafeDestinations destinations = new SafeDestinations(mock(JavaPlugin.class), Runnable::run)) {
            assertTrue(destinations.prepare(new Location(world, 0.5, 70, 0.5), Settings.parse(config)).isCompletedExceptionally());
        }
    }
}
