package org.micahcode.teamTP;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class RespawnListener implements Listener {
    private final TeamTP plugin;

    public RespawnListener(TeamTP plugin) {
        this.plugin = plugin;
    }

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
            plugin.getLogger().warning("Using normal respawn for " + event.getPlayer().getName()
                    + ": " + error.getMessage());
        }
    }
}
