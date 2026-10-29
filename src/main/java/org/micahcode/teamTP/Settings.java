package org.micahcode.teamTP;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

/** Validated configuration shared by team commands and death respawning. */
public record Settings(List<Team> teams, int startsPerTick, int concurrentTeleports,
                       int concurrentSaves, int timeoutSeconds, int spreadRadius,
                       int verticalSearch, boolean safeLocations, boolean generateChunks,
                       boolean skipSpectators, String exemptPermission, boolean notifyPlayers,
                       boolean respawnAtTeamSpawn) {

    /** Parses configuration values and rejects invalid team or behavior settings. */
    public static Settings parse(YamlConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("teams");
        if (section == null || section.getKeys(false).isEmpty() || section.getKeys(false).size() > 32) {
            throw new IllegalArgumentException("Configure between 1 and 32 teams.");
        }
        List<Team> teams = new ArrayList<>();
        Set<String> groups = new HashSet<>();
        for (String id : section.getKeys(false)) {
            if (!id.matches("[a-z0-9_-]{1,32}") || id.equals("all")) {
                throw new IllegalArgumentException("Invalid team ID: " + id + " (lowercase; 'all' is reserved).");
            }
            String group = section.getString(id + ".group", "");
            if (!group.matches("[a-z0-9_-]{1,64}") || group.equals("default") || !groups.add(group)) {
                throw new IllegalArgumentException("Team groups must be unique, lowercase dedicated groups, never 'default': " + id);
            }
            teams.add(new Team(id, section.getString(id + ".display-name", id), group));
        }
        String exempt = config.getString("distribution.exempt-permission", "team.exempt");
        if (!exempt.isEmpty() && !exempt.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid distribution.exempt-permission.");
        }
        return new Settings(List.copyOf(teams), integer(config, "performance.starts-per-tick", 1, 20),
                integer(config, "performance.concurrent-teleports", 1, 64),
                integer(config, "performance.concurrent-saves", 1, 32),
                integer(config, "performance.stall-timeout-seconds", 10, 600),
                integer(config, "teleport.spread-radius", 0, 8),
                integer(config, "teleport.vertical-search", 0, 4),
                bool(config, "teleport.safe-locations"), bool(config, "teleport.generate-chunks"),
                bool(config, "distribution.skip-spectators"), exempt,
                bool(config, "distribution.notify-players"), bool(config, "respawn.at-team-spawn"));
    }

    private static int integer(YamlConfiguration config, String path, int min, int max) {
        if (!config.isInt(path) || config.getInt(path) < min || config.getInt(path) > max) {
            throw new IllegalArgumentException(path + " must be an integer between " + min + " and " + max + ".");
        }
        return config.getInt(path);
    }

    private static boolean bool(YamlConfiguration config, String path) {
        if (!config.isBoolean(path)) throw new IllegalArgumentException(path + " must be true or false.");
        return config.getBoolean(path);
    }

    public Team team(String id) {
        return teams.stream().filter(team -> team.id().equalsIgnoreCase(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown team: " + id));
    }
}
