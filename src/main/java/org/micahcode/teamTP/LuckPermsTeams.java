package org.micahcode.teamTP;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class LuckPermsTeams {
    private final LuckPerms api;

    public LuckPermsTeams(LuckPerms api) { this.api = api; }

    public CompletableFuture<Void> prepare(Settings settings) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (Team team : settings.teams()) {
            future = future.thenCompose(ignored -> api.getGroupManager().createAndLoadGroup(team.group()).thenApply(group -> null));
        }
        return future;
    }

    public Optional<Team> membership(Player player, Settings settings) {
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) throw new IllegalArgumentException("LuckPerms data is not loaded for " + player.getName() + ".");
        List<Team> matches = new ArrayList<>();
        for (Node node : user.getNodes()) {
            if (!(node instanceof InheritanceNode parent) || !isTeamNode(node, settings)) continue;
            if (!node.getValue() || node.hasExpiry() || !node.getContexts().isEmpty()) {
                throw new IllegalArgumentException("Unsupported temporary/contextual/negated team parent on " + player.getName() + ". Redistribute first.");
            }
            settings.teams().stream().filter(team -> team.group().equals(parent.getGroupName())).forEach(matches::add);
        }
        if (matches.size() > 1) throw new IllegalArgumentException("Multiple team groups on " + player.getName() + ". Redistribute first.");
        return matches.stream().findFirst();
    }

    public CompletableFuture<Boolean> assign(Player player, Team team, Settings settings) {
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) return CompletableFuture.failedFuture(new IllegalStateException("LuckPerms user not loaded."));
        List<Node> previous = user.getNodes().stream().filter(node -> isTeamNode(node, settings)).toList();
        InheritanceNode replacement = InheritanceNode.builder(team.group()).build();
        try {
            user.data().clear(node -> isTeamNode(node, settings));
            user.data().add(replacement);
            return api.getUserManager().saveUser(user).handle((ignored, error) -> {
                if (error != null) {
                    user.data().remove(replacement);
                    previous.forEach(user.data()::add);
                    throw new java.util.concurrent.CompletionException(error);
                }
                return true;
            });
        } catch (RuntimeException error) {
            user.data().remove(replacement);
            previous.forEach(user.data()::add);
            return CompletableFuture.failedFuture(error);
        }
    }

    private boolean isTeamNode(Node node, Settings settings) {
        return node instanceof InheritanceNode parent
                && settings.teams().stream().anyMatch(team -> team.group().equals(parent.getGroupName()));
    }
}
