package me.hippodev.safeTransfer;

import me.hippodev.safeTransfer.api.SafeTransferAPI;
import me.hippodev.safeTransfer.command.ReloadCommand;
import me.hippodev.safeTransfer.command.SafeTransferCommand;
import me.hippodev.safeTransfer.debug.DebugLogger;
import me.hippodev.safeTransfer.message.MessageService;
import me.hippodev.safeTransfer.transfer.TransferService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class Core extends JavaPlugin {

    private DebugLogger debug;

    @Override
    public void onEnable() {
        // Player#transfer (the transfer packet) only exists from Paper/Folia
        // 1.20.6 onward. plugin.yml's api-version can't express that patch-level
        // floor (only major.minor), so this is the actual minimum-version gate.
        if (!hasPlayerTransfer()) {
            getLogger().severe("This server does not support Player#transfer (requires Paper/Folia 1.20.6 or newer). Disabling SafeTransfer.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        debug = new DebugLogger(getLogger(), getConfig().getBoolean("debug", false));
        MessageService messages = new MessageService(this);
        TransferService transferService = new TransferService(this, messages, debug);

        SafeTransferCommand command = new SafeTransferCommand(this, messages, debug, transferService);
        getCommand("safetransfer").setExecutor(command);
        getCommand("safetransfer").setTabCompleter(command);

        getCommand("streload").setExecutor(new ReloadCommand(messages, this::reload));

        getServer().getServicesManager().register(
                SafeTransferAPI.class, SafeTransferAPI.create(transferService), this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
    }

    /**
     * Re-reads config.yml from disk. Timeouts, delay, and messages are read
     * fresh from config on every use already, so reloading them needs no
     * extra wiring here - only the cached debug-enabled flag needs updating.
     */
    public void reload() {
        reloadConfig();
        debug.setEnabled(getConfig().getBoolean("debug", false));
    }

    private boolean hasPlayerTransfer() {
        try {
            Player.class.getMethod("transfer", String.class, int.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
