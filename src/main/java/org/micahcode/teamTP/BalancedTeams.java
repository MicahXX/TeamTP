package org.micahcode.teamTP;

import java.util.*;

public final class BalancedTeams {
    private BalancedTeams() { }

    public static <P, T> Map<P, T> assign(List<P> players, List<T> teams, Random random) {
        if (teams.isEmpty()) throw new IllegalArgumentException("At least one team is required.");
        if (new HashSet<>(players).size() != players.size()) throw new IllegalArgumentException("Duplicate participant.");
        List<P> shuffledPlayers = new ArrayList<>(players);
        List<T> shuffledTeams = new ArrayList<>(teams);
        Collections.shuffle(shuffledPlayers, random);
        Collections.shuffle(shuffledTeams, random);
        Map<P, T> result = new LinkedHashMap<>();
        for (int i = 0; i < shuffledPlayers.size(); i++) {
            result.put(shuffledPlayers.get(i), shuffledTeams.get(i % shuffledTeams.size()));
        }
        return result;
    }
}
