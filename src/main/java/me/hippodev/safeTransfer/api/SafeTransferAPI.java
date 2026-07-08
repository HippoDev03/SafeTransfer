package me.hippodev.safeTransfer.api;

import me.hippodev.safeTransfer.transfer.TransferService;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for other plugins to trigger a SafeTransfer transfer
 * programmatically - same resolve (SRV/CNAME-aware) -> status ping ->
 * transfer flow as the {@code /safetransfer} command, but silent: no chat
 * messages are sent to anyone, callers get a {@link TransferOutcome} instead.
 * <p>
 * Obtain an instance via Bukkit's services manager:
 * <pre>{@code
 * SafeTransferAPI api = Bukkit.getServicesManager().load(SafeTransferAPI.class);
 * if (api != null) {
 *     api.transfer(player, "play.example.com").thenAccept(outcome -> {
 *         if (!outcome.reachable()) {
 *             // handle failure, e.g. outcome.failureReason()
 *         }
 *     });
 * }
 * }</pre>
 * All methods are safe to call from any thread and do not block.
 */
public interface SafeTransferAPI {

    /**
     * Resolves and pings {@code address}; if reachable, transfers every
     * given player, staggered by {@code delayTicks} between each. If the
     * destination fails the status ping, no one is transferred.
     *
     * @param targets    players to transfer if the destination is reachable
     * @param address    "host" or "host:port", resolved the same way the
     *                   {@code /safetransfer} command resolves it (SRV/CNAME aware)
     * @param delayTicks ticks between each player's transfer when {@code targets}
     *                   has more than one player; ignored for a single player
     * @return a future completing with the ping outcome once it's known -
     *         does not wait for the (possibly staggered) transfers themselves
     */
    CompletableFuture<TransferOutcome> transfer(Collection<? extends Player> targets, String address, int delayTicks);

    /**
     * Convenience overload for a single player, transferred immediately
     * (no stagger delay) if the destination is reachable.
     */
    default CompletableFuture<TransferOutcome> transfer(Player target, String address) {
        return transfer(List.of(target), address, 0);
    }

    /** Internal: used by {@code Core} to construct and register the implementation. */
    static SafeTransferAPI create(TransferService transferService) {
        return new SafeTransferAPIImpl(transferService);
    }
}
