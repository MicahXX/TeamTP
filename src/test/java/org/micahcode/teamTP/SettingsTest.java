package org.micahcode.teamTP;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    static YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(SettingsTest.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
    }

    @Test
    void defaultsAreValidAndBounded() {
        Settings settings = Settings.parse(defaults());
        assertEquals(4, settings.teams().size());
        assertEquals(4, settings.startsPerTick());
        assertEquals(8, settings.concurrentTeleports());
        assertEquals("team_a", settings.team("A").group());
        assertTrue(settings.respawnAtTeamSpawn());
    }

    /** Verifies that existing configs inherit the enabled default and can opt out. */
    @Test
    void supportsDefaultAndExplicitRespawnSettings() {
        var config = defaults();
        config.set("respawn", null);
        config.setDefaults(defaults());
        assertTrue(Settings.parse(config).respawnAtTeamSpawn());
        config.set("respawn.at-team-spawn", false);
        assertFalse(Settings.parse(config).respawnAtTeamSpawn());
        config.set("respawn.at-team-spawn", "maybe");
        assertThrows(IllegalArgumentException.class, () -> Settings.parse(config));
    }

    @Test
    void rejectsDangerousOrDuplicateTeamGroups() {
        for (String group : new String[]{"default", "team_b", "UpperCase", "", "bad group"}) {
            var config = defaults();
            config.set("teams.a.group", group);
            assertThrows(IllegalArgumentException.class, () -> Settings.parse(config));
        }
    }

    @Test
    void rejectsMalformedPerformanceAndBooleanSettings() {
        for (Object value : new Object[]{0, -1, 65, 1.5, "eight"}) {
            var config = defaults();
            config.set("performance.concurrent-teleports", value);
            assertThrows(IllegalArgumentException.class, () -> Settings.parse(config));
        }
        var config = defaults();
        config.set("teleport.safe-locations", "maybe");
        assertThrows(IllegalArgumentException.class, () -> Settings.parse(config));
    }

    @Test
    void supportsReplacingDefaultsWithCustomTeamCount() {
        var config = defaults();
        config.set("teams", null);
        config.set("teams.red.group", "event_red");
        config.set("teams.blue.group", "event_blue");
        assertEquals(2, Settings.parse(config).teams().size());
    }

    @Test
    void rejectsReservedTeamName() {
        var config = defaults();
        config.set("teams.all.group", "team_all");
        assertThrows(IllegalArgumentException.class, () -> Settings.parse(config));
    }
}
