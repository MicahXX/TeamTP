package org.micahcode.teamTP;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers team destinations, reloads, and normal respawn fallbacks. */
class RespawnListenerTest {
    private final TeamTP plugin = mock(TeamTP.class);
    private final LuckPermsTeams teams = mock(LuckPermsTeams.class);
    private final SpawnStore spawns = mock(SpawnStore.class);
    private final Player player = mock(Player.class);
    private final Settings settings = Settings.parse(SettingsTest.defaults());
    private final RespawnListener listener = new RespawnListener(plugin);
    private final Location normal = new Location(mock(World.class), 0, 70, 0);

    /** Supplies the active configuration and services without starting a server. */
    RespawnListenerTest() {
        when(plugin.settings()).thenReturn(settings);
        when(plugin.teams()).thenReturn(teams);
        when(plugin.spawns()).thenReturn(spawns);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TeamTP-respawn-test"));
    }

    /** Builds a real event so assertions cover the resulting respawn location. */
    private PlayerRespawnEvent event(PlayerRespawnEvent.RespawnReason reason) {
        return new PlayerRespawnEvent(player, normal, true, false, false, reason);
    }

    /** Each team uses its own world, exact coordinates, and orientation even with a bed. */
    @Test
    void deathUsesEachTeamsSavedSpawn() {
        for (Team team : settings.teams()) {
            Location destination = new Location(mock(World.class), 123.5, 80, -45.5, 90, 30);
            when(teams.membership(player, settings)).thenReturn(Optional.of(team));
            when(spawns.get(team)).thenReturn(destination);
            PlayerRespawnEvent event = event(PlayerRespawnEvent.RespawnReason.DEATH);
            listener.onRespawn(event);
            assertEquals(destination, event.getRespawnLocation());
        }
    }

    /** A disabled setting takes effect on the existing listener immediately after reload. */
    @Test
    void honorsReloadedSettingsAndSpawnStore() {
        Team team = settings.team("a");
        when(teams.membership(player, settings)).thenReturn(Optional.of(team));
        SpawnStore reloadedSpawns = mock(SpawnStore.class);
        Location destination = new Location(mock(World.class), 200, 90, 50);
        when(plugin.spawns()).thenReturn(reloadedSpawns);
        when(reloadedSpawns.get(team)).thenReturn(destination);
        PlayerRespawnEvent enabled = event(PlayerRespawnEvent.RespawnReason.DEATH);
        listener.onRespawn(enabled);
        assertEquals(destination, enabled.getRespawnLocation());
        var config = SettingsTest.defaults();
        config.set("respawn.at-team-spawn", false);
        when(plugin.settings()).thenReturn(Settings.parse(config));
        PlayerRespawnEvent disabled = event(PlayerRespawnEvent.RespawnReason.DEATH);
        listener.onRespawn(disabled);
        assertEquals(normal, disabled.getRespawnLocation());
        verifyNoInteractions(spawns);
        verify(reloadedSpawns, times(1)).get(team);
    }

    /** End returns and other non-death respawns retain their original destination. */
    @Test
    void ignoresNonDeathRespawns() {
        for (var reason : PlayerRespawnEvent.RespawnReason.values()) {
            if (reason == PlayerRespawnEvent.RespawnReason.DEATH) continue;
            PlayerRespawnEvent event = event(reason);
            listener.onRespawn(event);
            assertEquals(normal, event.getRespawnLocation());
        }
        verifyNoInteractions(teams, spawns);
    }

    /** Initial asynchronous configuration loading must not interrupt normal respawning. */
    @Test
    void ignoresRespawnsBeforeConfigurationIsReady() {
        when(plugin.settings()).thenReturn(null);
        PlayerRespawnEvent event = event(PlayerRespawnEvent.RespawnReason.DEATH);
        assertDoesNotThrow(() -> listener.onRespawn(event));
        assertEquals(normal, event.getRespawnLocation());
        verifyNoInteractions(teams, spawns);
    }

    /** Unassigned players retain their normal destination. */
    @Test
    void keepsNormalSpawnWithoutTeam() {
        when(teams.membership(player, settings)).thenReturn(Optional.empty());
        PlayerRespawnEvent event = event(PlayerRespawnEvent.RespawnReason.DEATH);
        listener.onRespawn(event);
        assertEquals(normal, event.getRespawnLocation());
        verifyNoInteractions(spawns);
    }

    /** Invalid or unavailable LuckPerms membership is handled without breaking respawn. */
    @Test
    void keepsNormalSpawnForInvalidMembership() {
        when(teams.membership(player, settings)).thenThrow(new IllegalArgumentException("Multiple team groups"));
        PlayerRespawnEvent event = event(PlayerRespawnEvent.RespawnReason.DEATH);
        assertDoesNotThrow(() -> listener.onRespawn(event));
        assertEquals(normal, event.getRespawnLocation());
        verifyNoInteractions(spawns);
    }

    /** Missing spawns and unloaded worlds retain the server's original destination. */
    @Test
    void keepsNormalSpawnWhenTeamDestinationIsUnavailable() {
        Team team = settings.team("a");
        when(teams.membership(player, settings)).thenReturn(Optional.of(team));
        when(spawns.get(team)).thenThrow(new IllegalArgumentException("Spawn world is not loaded"));
        PlayerRespawnEvent event = event(PlayerRespawnEvent.RespawnReason.DEATH);
        assertDoesNotThrow(() -> listener.onRespawn(event));
        assertEquals(normal, event.getRespawnLocation());
    }
}
