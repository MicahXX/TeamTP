package org.micahcode.teamTP;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class BalancedTeamsTest {
    @Test
    void balancesEveryPopulationThroughOneThousand() {
        for (int count = 0; count <= 1000; count++) {
            List<Integer> players = IntStream.range(0, count).boxed().toList();
            List<String> teams = List.of("a", "b", "c", "d");
            Map<Integer, String> assignments = BalancedTeams.assign(players, teams, new Random(count));
            assertEquals(new HashSet<>(players), assignments.keySet());
            long[] sizes = teams.stream().mapToLong(team -> assignments.values().stream().filter(team::equals).count()).toArray();
            assertTrue(Arrays.stream(sizes).max().orElseThrow() - Arrays.stream(sizes).min().orElseThrow() <= 1);
        }
    }

    @Test
    void threeHundredPlayersGetSeventyFiveEach() {
        var result = BalancedTeams.assign(IntStream.range(0, 300).boxed().toList(), List.of("a", "b", "c", "d"), new Random(1));
        for (String team : List.of("a", "b", "c", "d")) assertEquals(75, result.values().stream().filter(team::equals).count());
    }

    @Test
    void remainderIsNotAlwaysAssignedToFirstTeam() {
        Set<String> recipients = new HashSet<>();
        Random random = new Random(42);
        for (int attempt = 0; attempt < 100; attempt++) recipients.add(BalancedTeams.assign(List.of(1), List.of("a", "b", "c", "d"), random).get(1));
        assertEquals(4, recipients.size());
    }

    @Test
    void rejectsMissingTeamsAndDuplicatePlayers() {
        assertThrows(IllegalArgumentException.class, () -> BalancedTeams.assign(List.of(1), List.of(), new Random()));
        assertThrows(IllegalArgumentException.class, () -> BalancedTeams.assign(List.of(1, 1), List.of("a"), new Random()));
    }
}
