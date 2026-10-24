package org.micahcode.teamTP;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

public final class TeamCommand implements TabExecutor {
    private static final List<String> SUBCOMMANDS = List.of("help", "distribute", "setspawn", "tp", "list", "status", "cancel", "reload");
    private final TeamTP plugin;

    public TeamCommand(TeamTP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!sender.hasPermission("team.admin")) { messages.send(sender, "no-permission"); return true; }
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        try {
            if (action.equals("help")) { messages.send(sender, "help"); return true; }
            if (action.equals("status") && args.length == 1) {
                if (plugin.jobs().busy()) plugin.jobs().status(sender);
                else messages.send(sender, "idle");
                return true;
            }
            if (action.equals("cancel") && args.length == 1) {
                boolean busy = plugin.jobs().busy();
                plugin.jobs().cancel();
                messages.send(sender, busy ? "cancelled" : "idle");
                return true;
            }
            if (plugin.jobs().busy()) { messages.send(sender, "busy"); return true; }
            if (action.equals("reload") && args.length == 1) { plugin.loadSettings(sender); return true; }
            if (plugin.settings() == null) throw new IllegalArgumentException("TeamTP is not initialized. Check the console, then /team reload.");
            switch (action) {
                case "distribute" -> {
                    require(args.length == 1);
                    plugin.distribution().distribute(sender, plugin.settings(), messages);
                }
                case "setspawn" -> setSpawn(sender, args);
                case "tp" -> {
                    require(args.length == 2);
                    plugin.teleports().teleport(sender, args[1], plugin.settings(), messages, plugin.spawns());
                }
                case "list" -> { require(args.length == 1); list(sender); }
                default -> messages.send(sender, "help");
            }
        } catch (IllegalArgumentException error) {
            messages.send(sender, "error", "reason", error.getMessage());
        } catch (Exception error) {
            plugin.getLogger().log(Level.WARNING, "Command failed: " + action, error);
            messages.send(sender, "error", "reason", "Could not complete the command; check the console. Existing configuration was retained.");
        }
        return true;
    }

    private void setSpawn(CommandSender sender, String[] args) throws Exception {
        require(args.length == 2 || args.length == 6 || args.length == 8);
        Team team = plugin.settings().team(args[1]);
        Location location;
        if (args.length == 2) {
            if (!(sender instanceof Player player)) throw new IllegalArgumentException("Console: /team setspawn <team> <world> <x> <y> <z> [yaw pitch]");
            location = player.getLocation();
        } else {
            World world = Bukkit.getWorld(args[2]);
            if (world == null) throw new IllegalArgumentException("World is not loaded: " + args[2]);
            location = new Location(world, Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5]),
                    args.length == 8 ? Float.parseFloat(args[6]) : 0, args.length == 8 ? Float.parseFloat(args[7]) : 0);
        }
        plugin.spawns().save(team, location);
        plugin.messages().send(sender, "spawn-set", "team", team.displayName(), "location", format(location));
    }

    private void list(CommandSender sender) {
        Map<Team, Integer> counts = new HashMap<>();
        int unassigned = 0;
        int invalid = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                Optional<Team> membership = plugin.teams().membership(player, plugin.settings());
                if (membership.isPresent()) counts.merge(membership.get(), 1, Integer::sum);
                else unassigned++;
            } catch (IllegalArgumentException error) { invalid++; }
        }
        for (Team team : plugin.settings().teams()) {
            String spawn;
            try { spawn = format(plugin.spawns().get(team)); }
            catch (IllegalArgumentException error) { spawn = error.getMessage(); }
            plugin.messages().send(sender, "team-info", "id", team.id(), "team", team.displayName(), "group", team.group(),
                    "count", counts.getOrDefault(team, 0), "spawn", spawn);
        }
        plugin.messages().send(sender, "unassigned", "count", unassigned, "invalid", invalid);
    }

    private String format(Location location) {
        return String.format(Locale.ROOT, "%s %.2f %.2f %.2f (yaw %.1f, pitch %.1f)", location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    private void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid arguments. Use /team help.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("team.admin") || args.length == 0) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) options.addAll(SUBCOMMANDS);
        else if (args.length == 2 && plugin.settings() != null && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("setspawn"))) {
            plugin.settings().teams().forEach(team -> options.add(team.id()));
            if (args[0].equalsIgnoreCase("tp")) options.add("all");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setspawn")) Bukkit.getWorlds().forEach(world -> options.add(world.getName()));
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(partial)).toList();
    }
}
