package me.hippodev.safeTransfer.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/**
 * Loads every player-facing message from {@code config.yml} (under
 * {@code messages.*}) and renders it with MiniMessage, so admins can
 * reword/restyle anything without touching code.
 */
public final class MessageService {

    private final Plugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessageService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void send(CommandSender sender, String key, TagResolver... placeholders) {
        sender.sendMessage(render(key, placeholders));
    }

    public Component render(String key, TagResolver... placeholders) {
        String template = plugin.getConfig().getString("messages." + key);
        if (template == null || template.isEmpty()) {
            return Component.text("Missing message: messages." + key);
        }
        return miniMessage.deserialize(template, placeholders);
    }
}
