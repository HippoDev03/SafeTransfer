package me.hippodev.safeTransfer.resolve;

/**
 * @param host         the actual host to open the TCP connection to (may be an
 *                      SRV-resolved target, distinct from what the admin typed)
 * @param port          the actual port to connect to (may be SRV-resolved)
 * @param protocolHost the hostname to put in the Minecraft handshake's
 *                      "server address" field and to hand to {@code Player#transfer} -
 *                      always the original, un-redirected hostname the admin typed,
 *                      matching vanilla client behavior. Proxies (BungeeCord/Velocity,
 *                      anti-DDoS layers like TCPShield) route/validate on this value and
 *                      will silently drop connections whose handshake host doesn't match it.
 */
public record ResolvedAddress(String host, int port, String protocolHost) {
}
