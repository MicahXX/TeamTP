package org.micahcode.teamTP;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class TeleportService implements AutoCloseable {
    private final JavaPlugin plugin;
    private final LuckPermsTeams teams;
    private final JobRunner jobs;
    private final Executor mainThread;
    private SafeDestinations destinations;

    public TeleportService(JavaPlugin plugin, LuckPermsTeams teams, JobRunner jobs, Executor mainThread) {
        this.plugin = plugin;
        this.teams = teams;
        this.jobs = jobs;
        this.mainThread = mainThread;
    }

    public void teleport(CommandSender sender, String selection, Settings settings, Messages messages, SpawnStore spawns) {
        Team selected = selection.equalsIgnoreCase("all") ? null : settings.team(selection);
        List<Participant> participants = new ArrayList<>();
        int skipped = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!Participant.eligible(player, settings)) { skipped++; continue; }
            Optional<Team> team = teams.membership(player, settings);
            if (team.isEmpty() || (selected != null && !selected.equals(team.get()))) { skipped++; continue; }
            participants.add(new Participant(player, team.get()));
        }
        if (participants.isEmpty()) throw new IllegalArgumentException("No eligible online players in the selected team(s).");
        Map<Team, Location> origins = new LinkedHashMap<>();
        for (Participant participant : participants) {
            origins.computeIfAbsent(participant.team(), spawns::get);
        }
        messages.send(sender, "selected", "count", participants.size(), "skipped", skipped);
        destinations = new SafeDestinations(plugin, mainThread);
        Map<Team, List<Location>> landings = new HashMap<>();
        jobs.start("destination preparation", sender, messages, settings, origins.keySet(), 2,
                team -> destinations.prepare(origins.get(team), settings).thenApply(locations -> {
                    landings.put(team, locations);
                    return true;
                }), success -> {
                    if (!success) {
                        close();
                        messages.send(sender, "preflight-failed");
                        return;
                    }
                    dispatch(sender, settings, messages, participants, landings);
                });
    }

    private void dispatch(CommandSender sender, Settings settings, Messages messages, List<Participant> participants,
                          Map<Team, List<Location>> landings) {
        Map<Team, Integer> indices = new HashMap<>();
        for (Map.Entry<Team, List<Location>> entry : landings.entrySet()) {
            long count = participants.stream().filter(player -> player.team().equals(entry.getKey())).count();
            if (count > entry.getValue().size()) messages.send(sender, "shared-landings", "team", entry.getKey().displayName(), "spots", entry.getValue().size());
        }
        jobs.start("teleport", sender, messages, settings, participants, settings.concurrentTeleports(), participant -> {
            Player player = participant.player();
            if (!participant.current(settings) || player.isDead() || player.isInsideVehicle() || !player.getPassengers().isEmpty()
                    || !teams.membership(player, settings).filter(participant.team()::equals).isPresent()) {
                return CompletableFuture.completedFuture(false);
            }
            List<Location> options = landings.get(participant.team());
            int index = indices.getOrDefault(participant.team(), 0);
            Location destination = null;
            for (int attempts = 0; attempts < options.size(); attempts++) {
                Location candidate = options.get(index++ % options.size());
                if (SafeDestinations.valid(candidate, settings.safeLocations())) { destination = candidate; break; }
            }
            indices.put(participant.team(), index);
            if (destination == null) return CompletableFuture.completedFuture(false);
            return player.teleportAsync(destination.clone(), TeleportCause.PLUGIN).thenApplyAsync(success -> {
                if (success && player.isOnline()) player.setFallDistance(0);
                return success;
            }, mainThread);
        }, success -> close());
    }

    @Override
    public void close() {
        if (destinations != null) {
            destinations.close();
            destinations = null;
        }
    }
}
