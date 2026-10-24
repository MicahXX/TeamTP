package org.micahcode.teamTP;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LuckPermsTeamsTest {
    private final Settings settings = Settings.parse(SettingsTest.defaults());
    private final LuckPerms api = mock(LuckPerms.class);
    private final UserManager manager = mock(UserManager.class);
    private final User user = mock(User.class);
    private final NodeMap data = mock(NodeMap.class);
    private final Player player = mock(Player.class);
    private final Set<Node> nodes = new HashSet<>();
    private final LuckPermsTeams teams = new LuckPermsTeams(api);

    LuckPermsTeamsTest() {
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Tester");
        when(api.getUserManager()).thenReturn(manager);
        when(manager.getUser(uuid)).thenReturn(user);
        when(user.getNodes()).thenAnswer(ignored -> List.copyOf(nodes));
        when(user.data()).thenReturn(data);
        doAnswer(call -> { nodes.removeIf(call.<Predicate<Node>>getArgument(0)); return null; }).when(data).clear(any(Predicate.class));
        when(data.add(any(Node.class))).thenAnswer(call -> { nodes.add(call.getArgument(0)); return DataMutateResult.SUCCESS; });
        when(data.remove(any(Node.class))).thenAnswer(call -> { nodes.remove(call.getArgument(0)); return DataMutateResult.SUCCESS; });
    }

    private InheritanceNode parent(String group) {
        InheritanceNode node = mock(InheritanceNode.class);
        when(node.getGroupName()).thenReturn(group);
        when(node.getValue()).thenReturn(true);
        ImmutableContextSet context = mock(ImmutableContextSet.class);
        when(context.isEmpty()).thenReturn(true);
        when(node.getContexts()).thenReturn(context);
        return node;
    }

    @Test
    void membershipUsesDirectGroupsAndRejectsAmbiguity() {
        assertTrue(teams.membership(player, settings).isEmpty());
        nodes.add(parent("team_a"));
        nodes.add(parent("admin"));
        assertEquals("a", teams.membership(player, settings).orElseThrow().id());
        nodes.add(parent("team_b"));
        assertThrows(IllegalArgumentException.class, () -> teams.membership(player, settings));
    }

    @Test
    void rejectsContextualTemporaryAndNegatedTeamParents() {
        for (int variant = 0; variant < 3; variant++) {
            nodes.clear();
            InheritanceNode node = parent("team_a");
            if (variant == 0) when(node.hasExpiry()).thenReturn(true);
            if (variant == 1) when(node.getValue()).thenReturn(false);
            if (variant == 2) when(node.getContexts().isEmpty()).thenReturn(false);
            nodes.add(node);
            assertThrows(IllegalArgumentException.class, () -> teams.membership(player, settings));
        }
    }

    @Test
    void savesOnlyTeamChangesAndRetainsRankAndPermissions() {
        InheritanceNode old = parent("team_a");
        InheritanceNode rank = parent("admin");
        Node permission = mock(Node.class);
        nodes.addAll(List.of(old, rank, permission));
        InheritanceNode replacement = parent("team_b");
        CompletableFuture<Void> storage = new CompletableFuture<>();
        when(manager.saveUser(user)).thenReturn(storage);
        try (var factory = mockStatic(InheritanceNode.class)) {
            var builder = mock(InheritanceNode.Builder.class);
            factory.when(() -> InheritanceNode.builder("team_b")).thenReturn(builder);
            when(builder.build()).thenReturn(replacement);
            CompletableFuture<Boolean> result = teams.assign(player, settings.team("b"), settings);
            assertFalse(result.isDone());
            assertEquals(Set.of(rank, permission, replacement), nodes);
            storage.complete(null);
            assertTrue(result.join());
            verify(user, never()).setPrimaryGroup(anyString());
        }
    }

    @Test
    void failedStorageRestoresOldTeamAndReportsFailure() {
        InheritanceNode old = parent("team_a");
        InheritanceNode rank = parent("vip");
        nodes.addAll(List.of(old, rank));
        InheritanceNode replacement = parent("team_b");
        when(manager.saveUser(user)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Database unavailable")));
        try (var factory = mockStatic(InheritanceNode.class)) {
            var builder = mock(InheritanceNode.Builder.class);
            factory.when(() -> InheritanceNode.builder("team_b")).thenReturn(builder);
            when(builder.build()).thenReturn(replacement);
            assertThrows(java.util.concurrent.CompletionException.class, () -> teams.assign(player, settings.team("b"), settings).join());
            assertEquals(Set.of(old, rank), nodes);
        }
    }
}
