package me.hippodev.safeTransfer.ping;

import me.hippodev.safeTransfer.debug.DebugLogger;
import me.hippodev.safeTransfer.resolve.ResolvedAddress;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Performs a raw Minecraft server-list-ping "status" handshake against a
 * resolved address to confirm the destination is actually reachable and
 * speaking the Minecraft protocol before a transfer is attempted.
 */
public final class StatusPinger {

    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int protocolVersion;
    private final DebugLogger debug;

    /**
     * @param protocolVersion the protocol version to advertise in the handshake.
     *                        Use the running server's real current protocol version
     *                        (e.g. {@code Bukkit.getUnsafe().getProtocolVersion()}),
     *                        not the "-1 = any version" sentinel: some anti-bot /
     *                        DDoS-protection proxies (TCPShield and similar) treat
     *                        -1 as a known scanner/automation fingerprint and silently
     *                        drop the connection right after the handshake.
     */
    public StatusPinger(int connectTimeoutMs, int readTimeoutMs, int protocolVersion, DebugLogger debug) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.protocolVersion = protocolVersion;
        this.debug = debug;
    }

    /**
     * Blocking - call from an async thread. Throws with a descriptive
     * message on any failure (connect refused, timeout, bad protocol reply).
     */
    public void ping(ResolvedAddress address) throws IOException {
        debug.log("Pinging " + address.host() + ":" + address.port()
                + " (protocolHost=" + address.protocolHost() + ", protocolVersion=" + protocolVersion + ")");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address.host(), address.port()), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            debug.log("TCP connect succeeded, sending handshake + status request");

            OutputStream rawOut = socket.getOutputStream();
            InputStream rawIn = socket.getInputStream();

            // Matching vanilla: connect to the (possibly SRV-resolved) target,
            // but the handshake's server-address field carries the original
            // hostname the admin typed - proxies route/validate on that value.
            // Handshake + status request are coalesced into a single write,
            // like a real client, rather than two separate flushes.
            ByteArrayOutputStream combined = new ByteArrayOutputStream();
            writeHandshake(combined, address.protocolHost(), address.port());
            writePacket(combined, new byte[]{0x00}); // status request, empty payload beyond packet id
            rawOut.write(combined.toByteArray());
            rawOut.flush();

            DataInputStream in = new DataInputStream(rawIn);
            readVarInt(in); // packet length
            int packetId = readVarInt(in);
            if (packetId != 0x00) {
                throw new IOException("Unexpected packet id in status response: " + packetId);
            }
            int jsonLength = readVarInt(in);
            byte[] jsonBytes = new byte[jsonLength];
            in.readFully(jsonBytes);
            if (jsonBytes.length == 0) {
                throw new IOException("Empty status response");
            }
            debug.log("Status response received (" + jsonBytes.length + " bytes)");
        } catch (IOException e) {
            debug.log("Ping failed for " + address.host() + ":" + address.port(), e);
            throw e;
        }
    }

    private void writeHandshake(OutputStream out, String host, int port) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(data);

        writeVarInt(dataOut, 0x00); // packet id: handshake
        writeVarInt(dataOut, protocolVersion);
        writeString(dataOut, host);
        dataOut.writeShort(port);
        writeVarInt(dataOut, 1); // next state: status

        writePacket(out, data.toByteArray());
    }

    private void writePacket(OutputStream out, byte[] payload) throws IOException {
        ByteArrayOutputStream length = new ByteArrayOutputStream();
        writeVarInt(new DataOutputStream(length), payload.length);
        out.write(length.toByteArray());
        out.write(payload);
        out.flush();
    }

    private void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;
        while (true) {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) {
                break;
            }
            position += 7;
            if (position >= 32) {
                throw new IOException("VarInt too big");
            }
        }
        return value;
    }
}
