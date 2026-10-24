package org.micahcode.teamTP;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeleportServiceTest {
    private final Settings settings = Settings.parse(SettingsTest.defaults());
    private final Messages messages = mock(Messages.class);
    private final CommandSender sender = mock(CommandSender.class);
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final LuckPermsTeams teams = mock(LuckPermsTeams.class);
    private final SpawnStore spawns = mock(SpawnStore.class);
    private final AtomicReference<Runnable> tick = new AtomicReference<>();
    private final Map<UUID, Player> players = new LinkedHashMap<>();
    private final Map<Player, Location> expected = new HashMap<>();
    private final Map<Player, CompletableFuture<Boolean>> active = new HashMap<>();
    private final Set<Player> dispatched = new HashSet<>();
    private final JobRunner jobs;
    private final TeleportService service;

    TeleportServiceTest() {
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TeamTP-test"));
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenAnswer(call -> {
            tick.set(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        jobs = new JobRunner(plugin);
        service = new TeleportService(plugin, teams, jobs, Runnable::run);
        World world = SpawnStoreTest.world();
        for (int i = 0; i < settings.teams().size(); i++) {
            when(spawns.get(settings.teams().get(i))).thenReturn(new Location(world, i * 100, 70, 0));
        }
    }

    private void addPlayers(int count) {
        for (int i = 0; i < count; i++) {
            Player player = mock(Player.class);
            UUID uuid = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(uuid);
            when(player.getName()).thenReturn("Player" + i);
            when(player.isOnline()).thenReturn(true);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getPassengers()).thenReturn(List.of());
            Team team = settings.teams().get(i % 4);
            when(teams.membership(player, settings)).thenReturn(Optional.of(team));
            expected.put(player, spawns.get(team));
            when(player.teleportAsync(any(Location.class), eq(TeleportCause.PLUGIN))).thenAnswer(call -> {
                assertEquals(expected.get(player), call.getArgument(0));
                assertTrue(dispatched.add(player), "Player dispatched twice");
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                active.put(player, future);
                return future;
            });
            players.put(uuid, player);
        }
    }

    @Test
    void teleportsThreeHundredPlayersToCorrectTeamsWithoutExceedingLimits() {
        addPlayers(300);
        try (var bukkit = mockStatic(Bukkit.class);
             var safe = mockStatic(SafeDestinations.class);
             var constructed = mockConstruction(SafeDestinations.class, (mock, context) ->
                     when(mock.prepare(any(Location.class), eq(settings))).thenAnswer(call ->
                             CompletableFuture.completedFuture(List.of(call.getArgument(0, Location.class)))))) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players.values());
            bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> players.get(call.getArgument(0)));
            safe.when(() -> SafeDestinations.valid(any(Location.class), eq(true))).thenReturn(true);
            service.teleport(sender, "all", settings, messages, spawns);
            for (int iteration = 0; iteration < 2000 && jobs.busy(); iteration++) {
                if (iteration % 3 == 0) {
                    active.values().forEach(future -> future.complete(true));
                    active.clear();
                }
                int before = dispatched.size();
                tick.get().run();
                assertTrue(dispatched.size() - before <= 4);
                assertTrue(active.size() <= 8);
            }
            assertFalse(jobs.busy());
            assertEquals(300, dispatched.size());
            verify(constructed.constructed().getFirst()).close();
            for (Player player : players.values()) verify(player).setFallDistance(0);
        } finally { jobs.close(); service.close(); }
    }

    @Test
    void preparationFailureMovesNobodyAndReleasesTickets() {
        addPlayers(8);
        try (var bukkit = mockStatic(Bukkit.class);
             var constructed = mockConstruction(SafeDestinations.class, (mock, context) ->
                     when(mock.prepare(any(Location.class), eq(settings))).thenReturn(
                             CompletableFuture.failedFuture(new IllegalArgumentException("Unsafe spawn"))))) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players.values());
            service.teleport(sender, "all", settings, messages, spawns);
            for (int i = 0; i < 10 && jobs.busy(); i++) tick.get().run();
            assertFalse(jobs.busy());
            assertTrue(dispatched.isEmpty());
            verify(constructed.constructed().getFirst()).close();
        } finally { jobs.close(); service.close(); }
    }

    @Test
    void missingSpawnRejectsWholeOperationBeforeQueueing() {
        addPlayers(8);
        when(spawns.get(settings.team("d"))).thenThrow(new IllegalArgumentException("Missing spawn"));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players.values());
            assertThrows(IllegalArgumentException.class, () -> service.teleport(sender, "all", settings, messages, spawns));
            assertFalse(jobs.busy());
            assertTrue(dispatched.isEmpty());
        } finally { jobs.close(); service.close(); }
    }

    @Test
    void rechecksDisconnectsMembershipDeathVehiclesAndExemptionsAfterSnapshot() {
        addPlayers(8);
        List<Player> snapshot = List.copyOf(players.values());
        try (var bukkit = mockStatic(Bukkit.class);
             var safe = mockStatic(SafeDestinations.class);
             var constructed = mockConstruction(SafeDestinations.class, (mock, context) ->
                     when(mock.prepare(any(Location.class), eq(settings))).thenAnswer(call ->
                             CompletableFuture.completedFuture(List.of(call.getArgument(0, Location.class)))))) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players.values());
            bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> players.get(call.getArgument(0)));
            safe.when(() -> SafeDestinations.valid(any(Location.class), eq(true))).thenReturn(true);
            service.teleport(sender, "all", settings, messages, spawns);
            when(snapshot.get(0).isOnline()).thenReturn(false);
            when(teams.membership(snapshot.get(1), settings)).thenReturn(Optional.of(settings.team("a")));
            when(snapshot.get(2).isDead()).thenReturn(true);
            when(snapshot.get(3).isInsideVehicle()).thenReturn(true);
            when(snapshot.get(4).hasPermission("team.exempt")).thenReturn(true);
            when(snapshot.get(5).getGameMode()).thenReturn(GameMode.SPECTATOR);
            for (int i = 0; i < 20 && jobs.busy(); i++) {
                active.values().forEach(future -> future.complete(false));
                active.clear();
                tick.get().run();
            }
            assertFalse(jobs.busy());
            assertEquals(Set.of(snapshot.get(6), snapshot.get(7)), dispatched);
            for (Player player : snapshot) verify(player, never()).setFallDistance(anyFloat());
        } finally { jobs.close(); service.close(); }
    }

    @Test
    void cancellationDuringPreparationNeverStartsPlayerTeleports() {
        addPlayers(8);
        CompletableFuture<List<Location>> loading = new CompletableFuture<>();
        try (var bukkit = mockStatic(Bukkit.class);
             var constructed = mockConstruction(SafeDestinations.class, (mock, context) ->
                     when(mock.prepare(any(Location.class), eq(settings))).thenReturn(loading))) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players.values());
            service.teleport(sender, "all", settings, messages, spawns);
            tick.get().run();
            jobs.cancel();
            assertTrue(jobs.busy());
            loading.complete(List.of(spawns.get(settings.team("a"))));
            tick.get().run();
            assertFalse(jobs.busy());
            assertTrue(dispatched.isEmpty());
            verify(constructed.constructed().getFirst()).close();
        } finally { jobs.close(); service.close(); }
    }
}
