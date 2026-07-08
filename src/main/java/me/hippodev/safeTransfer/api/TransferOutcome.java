package me.hippodev.safeTransfer.api;

/**
 * Result of a {@link SafeTransferAPI#transfer} call's pre-transfer status
 * ping. This describes only the resolve+ping step - the actual (possibly
 * delay-staggered) per-player transfers happen afterward and are not
 * individually reported back through this object.
 *
 * @param reachable       whether the destination passed the status ping
 * @param host            the resolved connect host (may differ from what was
 *                         typed, if an SRV/CNAME chain was followed)
 * @param port             the resolved connect port
 * @param failureReason    null if {@code reachable} is true; otherwise a short
 *                         description of why the ping failed
 * @param queuedPlayers    number of players scheduled for transfer (0 if not reachable)
 */
public record TransferOutcome(boolean reachable, String host, int port, String failureReason, int queuedPlayers) {
}
