package org.micahcode.teamTP;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Messages {
    private final YamlConfiguration config;

    public Messages(YamlConfiguration config) {
        this.config = config;
    }

    public void send(CommandSender sender, String key, Object... replacements) {
        String message = config.getString("messages." + key, key);
        if (message.isEmpty()) return;
        message = config.getString("messages.prefix", "") + message;
        for (int index = 0; index < replacements.length; index += 2) {
            message = message.replace("{" + replacements[index] + "}", String.valueOf(replacements[index + 1]));
        }
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}
