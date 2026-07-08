package me.hippodev.safeTransfer.transfer;

import me.hippodev.safeTransfer.resolve.ResolvedAddress;

/**
 * Outcome of resolving + pinging a destination, before any transfer is
 * attempted. {@code failureReason} is null when the ping succeeded.
 */
record PingResult(ResolvedAddress resolved, String failureReason) {

    boolean reachable() {
        return failureReason == null;
    }
}
