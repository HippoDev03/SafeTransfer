package me.hippodev.safeTransfer.transfer;

import me.hippodev.safeTransfer.api.TransferOutcome;
import me.hippodev.safeTransfer.debug.DebugLogger;
import me.hippodev.safeTransfer.message.MessageService;
import me.hippodev.safeTransfer.ping.StatusPinger;
import me.hippodev.safeTransfer.resolve.AddressResolver;
import me.hippodev.safeTransfer.resolve.ResolvedAddress;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates: resolve target address (SRV/CNAME aware) -> confirm reachable
 * via a real status ping -> only then transfer the requested player(s),
 * staggered by a configurable delay for mass transfers.
 * <p>
 * Uses Paper's region-aware scheduler API throughout (GlobalRegionScheduler
 * for non-entity-bound work, each Player's own EntityScheduler for the
 * actual transfer) so this works unchanged on both regular Paper and Folia.
 * <p>
 * The resolve+ping step ({@link #resolveAndPing}) is shared by both the
 * chat-facing {@code /safetransfer} command path and the silent
 * {@link me.hippodev.safeTransfer.api.SafeTransferAPI} path used by other plugins.
 */
public final class TransferService {

    private final Plugin plugin;
    private final AddressResolver resolver;
    private final MessageService messages;
    private final DebugLogger debug;

    public TransferService(Plugin plugin, MessageService messages, DebugLogger debug) {
        this.plugin = plugin;
        this.messages = messages;
        this.debug = debug;
        this.resolver = new AddressResolver(plugin.getLogger(), debug);
    }

    /**
     * Command-facing entry point: resolves, pings, and (if reachable)
     * transfers {@code targets}, sending chat feedback to {@code sender} at
     * every step.
     */
    public void transfer(CommandSender sender, List<Player> targets, String rawAddress, int delayTicks) {
        resolveAndPing(rawAddress).thenAccept(result -> {
            if (!result.reachable()) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                        messages.send(sender, "aborted",
                                Placeholder.unparsed("host", result.resolved().host()),
                                Placeholder.unparsed("port", String.valueOf(result.resolved().port())),
                                Placeholder.unparsed("reason", result.failureReason())));
                return;
            }

            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                messages.send(sender, "reachable",
                        Placeholder.unparsed("host", result.resolved().host()),
                        Placeholder.unparsed("port", String.valueOf(result.resolved().port())),
                        Placeholder.unparsed("count", String.valueOf(targets.size())),
                        Placeholder.unparsed("delay", String.valueOf(delayTicks)));
                scheduleTransfers(sender, targets, result.resolved(), delayTicks);
            });
        });
    }

    /**
     * API entry point: same resolve -> ping -> transfer flow, but silent -
     * no chat messages are sent to anyone. Used by {@link me.hippodev.safeTransfer.api.SafeTransferAPI}.
     */
    public CompletableFuture<TransferOutcome> transferSilently(Collection<? extends Player> targets, String rawAddress, int delayTicks) {
        List<Player> targetList = List.copyOf(targets);
        return resolveAndPing(rawAddress).thenApply(result -> {
            if (result.reachable()) {
                scheduleTransfers(null, targetList, result.resolved(), delayTicks);
            }
            return new TransferOutcome(
                    result.reachable(),
                    result.resolved().host(),
                    result.resolved().port(),
                    result.failureReason(),
                    result.reachable() ? targetList.size() : 0);
        });
    }

    private CompletableFuture<PingResult> resolveAndPing(String rawAddress) {
        return CompletableFuture.supplyAsync(() -> resolver.resolve(rawAddress))
                .thenApply(resolved -> {
                    int connectTimeoutMs = plugin.getConfig().getInt("connect-timeout-ms", 3000);
                    int readTimeoutMs = plugin.getConfig().getInt("ping-timeout-ms", 3000);
                    int protocolVersion = Bukkit.getUnsafe().getProtocolVersion();
                    StatusPinger pinger = new StatusPinger(connectTimeoutMs, readTimeoutMs, protocolVersion, debug);

                    // First status ping to a destination sometimes fails transiently (e.g. the
                    // remote server hasn't finished waking up yet), so retry a few times before
                    // reporting unreachable.
                    int maxAttempts = Math.max(1, plugin.getConfig().getInt("ping-retries", 3));
                    String reason = null;
                    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                        try {
                            pinger.ping(resolved);
                            return new PingResult(resolved, null);
                        } catch (Exception e) {
                            reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                            debug.log("Status ping attempt " + attempt + "/" + maxAttempts
                                    + " to " + resolved.host() + ":" + resolved.port() + " failed: " + reason);
                        }
                    }
                    return new PingResult(resolved, reason);
                });
    }

    // Silent for the players being moved: only the CommandSender who ran
    // /safetransfer (or nobody, for API callers) ever receives a chat message.
    // Do not add target.sendMessage(...) calls here - a transferred player
    // should see nothing beyond the vanilla client's own "connecting to a
    // new server" screen.
    private void scheduleTransfers(CommandSender sender, List<Player> targets, ResolvedAddress resolved, int delayTicks) {
        for (int i = 0; i < targets.size(); i++) {
            Player target = targets.get(i);
            // EntityScheduler requires a delay of at least 1 tick.
            long delay = Math.max(1L, (long) delayTicks * i);
            target.getScheduler().runDelayed(plugin, task -> {
                // Use the original hostname (not the SRV-resolved target) so
                // the destination proxy's virtual-host routing still matches -
                // the transfer packet itself carries the resolved port.
                target.transfer(resolved.protocolHost(), resolved.port());
                if (sender != null) {
                    messages.send(sender, "transferred", Placeholder.unparsed("player", target.getName()));
                }
            }, /* retired: entity gone before task ran (e.g. logged off) */ null, delay);
        }
    }
}
