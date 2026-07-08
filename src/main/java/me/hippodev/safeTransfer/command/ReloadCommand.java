package me.hippodev.safeTransfer.command;

import me.hippodev.safeTransfer.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** Backs {@code /streload}: re-reads config.yml without a server restart. */
public final class ReloadCommand implements CommandExecutor {

    private static final String PERMISSION = "safetransfer.reload";

    private final MessageService messages;
    private final Runnable reloadAction;

    public ReloadCommand(MessageService messages, Runnable reloadAction) {
        this.messages = messages;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            messages.send(sender, "no-permission");
            return true;
        }

        reloadAction.run();
        messages.send(sender, "reloaded");
        return true;
    }
}
