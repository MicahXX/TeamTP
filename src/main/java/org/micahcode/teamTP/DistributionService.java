package org.micahcode.teamTP;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class DistributionService {
    private final LuckPermsTeams teams;
    private final JobRunner jobs;
    private final Executor mainThread;

    public DistributionService(LuckPermsTeams teams, JobRunner jobs, Executor mainThread) {
        this.teams = teams;
        this.jobs = jobs;
        this.mainThread = mainThread;
    }

    public void distribute(CommandSender sender, Settings settings, Messages messages) {
        List<Player> players = Bukkit.getOnlinePlayers().stream().filter(player -> Participant.eligible(player, settings))
                .map(player -> (Player) player).toList();
        if (players.isEmpty()) throw new IllegalArgumentException("No eligible online players.");
        Map<Player, Team> assignments = BalancedTeams.assign(players, settings.teams(), new Random());
        List<Participant> participants = assignments.entrySet().stream().map(entry -> new Participant(entry.getKey(), entry.getValue())).toList();
        jobs.start("distribution", sender, messages, settings, participants, settings.concurrentSaves(), participant -> {
            if (!participant.current(settings)) return CompletableFuture.completedFuture(false);
            return teams.assign(participant.player(), participant.team(), settings).thenApplyAsync(success -> {
                if (success && settings.notifyPlayers() && participant.current(settings)) {
                    messages.send(participant.player(), "assigned", "team", participant.team().displayName());
                }
                return success;
            }, mainThread);
        }, success -> {
            messages.send(sender, success ? "balanced" : "partial-distribution");
        });
    }
}
