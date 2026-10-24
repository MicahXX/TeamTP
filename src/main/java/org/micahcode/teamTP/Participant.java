package org.micahcode.teamTP;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public record Participant(Player player, Team team) {

    public static boolean eligible(Player player, Settings settings) {
        return player.isOnline() && (!settings.skipSpectators() || player.getGameMode() != GameMode.SPECTATOR)
                && (settings.exemptPermission().isEmpty() || !player.hasPermission(settings.exemptPermission()));
    }

    public boolean current(Settings settings) {
        return Bukkit.getPlayer(player.getUniqueId()) == player && eligible(player, settings);
    }

    @Override
    public String toString() { return player.getName() + " (" + player.getUniqueId() + ") -> " + team.id(); }
}
