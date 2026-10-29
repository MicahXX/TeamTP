package org.micahcode.teamTP;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Applies saved team destinations to death respawns using the active configuration. */
public final class RespawnListener implements Listener {
    private final TeamTP plugin;

    /** Keeps access to the latest settings and spawn store after configuration reloads. */
    public RespawnListener(TeamTP plugin) {
        this.plugin = plugin;
    }

    /** Overrides death respawns when a team and its saved destination are available. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Settings settings = plugin.settings();
        if (settings == null || !settings.respawnAtTeamSpawn()
                || event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) return;

        SpawnStore spawns = plugin.spawns();
        if (spawns == null) return;
        try {
            plugin.teams().membership(event.getPlayer(), settings)
                    .ifPresent(team -> event.setRespawnLocation(spawns.get(team)));
        } catch (IllegalArgumentException error) {
            // Preserve the server's respawn location if membership or the saved spawn is invalid.
            plugin.getLogger().warning("Using normal respawn for " + event.getPlayer().getName()
                    + ": " + error.getMessage());
        }
    }
}
