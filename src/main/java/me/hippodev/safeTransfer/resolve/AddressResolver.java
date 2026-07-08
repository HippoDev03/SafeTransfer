package me.hippodev.safeTransfer.resolve;

import me.hippodev.safeTransfer.debug.DebugLogger;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Resolves a "host" or "host:port" the way a vanilla client resolves a
 * multiplayer server-list entry: if no port was typed, look up the
 * {@code _minecraft._tcp.<host>} SRV record and use its target/port instead.
 * CNAME resolution needs no extra code - normal DNS/socket connect follows
 * CNAME chains transparently.
 * <p>
 * Matching vanilla behavior, the SRV lookup is only attempted for real
 * hostnames - IP literals (and "localhost") are used as-is, since Mojang's
 * client never issues a {@code _minecraft._tcp.} query for those either.
 */
public final class AddressResolver {

    private static final Pattern IPV4_LITERAL = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");

    private final Logger logger;
    private final DebugLogger debug;

    public AddressResolver(Logger logger, DebugLogger debug) {
        this.logger = logger;
        this.debug = debug;
    }

    /**
     * @param input raw admin-supplied address, e.g. "play.example.com" or "play.example.com:25566"
     */
    public ResolvedAddress resolve(String input) {
        String host;
        Integer explicitPort = null;

        int colon = input.lastIndexOf(':');
        if (colon > 0 && colon < input.length() - 1 && input.chars().skip(colon + 1).allMatch(Character::isDigit)) {
            host = input.substring(0, colon);
            explicitPort = Integer.parseInt(input.substring(colon + 1));
        } else {
            host = input;
        }

        if (explicitPort != null) {
            debug.log("Explicit port given, skipping SRV lookup: " + host + ":" + explicitPort);
            return new ResolvedAddress(host, explicitPort, host);
        }

        if (isIpLiteral(host) || host.equalsIgnoreCase("localhost")) {
            debug.log("Host is an IP literal/localhost, skipping SRV lookup: " + host);
            return new ResolvedAddress(host, 25565, host);
        }

        debug.log("No explicit port - starting SRV lookup for " + host);
        ResolvedAddress srv = lookupSrv(host);
        if (srv != null) {
            logger.info("Resolved SRV record for " + host + " -> " + srv.host() + ":" + srv.port());
            return srv;
        }

        debug.log("No SRV record found for " + host + ", falling back to default port 25565");
        return new ResolvedAddress(host, 25565, host);
    }

    private boolean isIpLiteral(String host) {
        return IPV4_LITERAL.matcher(host).matches() || host.contains(":");
    }

    private static final String SRV_PREFIX = "_minecraft._tcp.";
    private static final int MAX_CNAME_HOPS = 10;

    private ResolvedAddress lookupSrv(String host) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "3000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");

        try {
            DirContext ctx = new InitialDirContext(env);

            // The Sun DNS provider only returns records that exactly match the
            // requested type for the requested name - it does NOT transparently
            // follow a CNAME sitting on "_minecraft._tcp.<host>" itself the way
            // A/AAAA lookups do, and querying both SRV and CNAME in one call
            // can silently drop one of them on some resolvers. So walk the
            // chain ourselves, at each hop querying SRV alone first and only
            // falling back to a separate CNAME query if that came back empty.
            //
            // Two distinct CNAME patterns exist in the wild:
            //  1) "_minecraft._tcp.<host>" itself is CNAME'd to another
            //     (possibly already-prefixed) SRV owner name.
            //  2) the bare "<host>" is CNAME'd (e.g. to a hosting provider's
            //     domain), and it's THAT domain's own "_minecraft._tcp.<..>"
            //     which actually carries the SRV record - the underscore
            //     name itself is never aliased at all.
            // Track both the current SRV query name and the current bare
            // host so both patterns get tried at every hop.
            String bareHost = host;
            String queryName = SRV_PREFIX + bareHost;

            for (int hop = 0; hop < MAX_CNAME_HOPS; hop++) {
                debug.log("SRV hop " + hop + ": querying SRV " + queryName);
                Attribute srv = queryAttribute(ctx, queryName, "SRV");
                if (srv != null && srv.size() > 0) {
                    // format: priority weight port target
                    String[] parts = ((String) srv.get(0)).split(" ");
                    int port = Integer.parseInt(parts[2]);
                    String target = stripTrailingDot(parts[3]);
                    debug.log("SRV hop " + hop + ": found SRV " + queryName + " -> " + target + ":" + port);
                    // "host" here is the original, un-redirected hostname the
                    // admin typed - always what goes in the handshake's
                    // server-address field / Player#transfer, per vanilla
                    // client behavior, regardless of how many CNAME/SRV hops
                    // it took to find the actual connect target.
                    return new ResolvedAddress(target, port, host);
                }

                Attribute prefixedCname = queryAttribute(ctx, queryName, "CNAME");
                if (prefixedCname != null && prefixedCname.size() > 0) {
                    String target = stripTrailingDot((String) prefixedCname.get(0));
                    boolean alreadyPrefixed = target.regionMatches(true, 0, SRV_PREFIX, 0, SRV_PREFIX.length());
                    bareHost = alreadyPrefixed ? target.substring(SRV_PREFIX.length()) : target;
                    queryName = alreadyPrefixed ? target : SRV_PREFIX + target;
                    debug.log("SRV hop " + hop + ": CNAME on SRV name -> following to " + queryName);
                    continue;
                }

                // Pattern 2 fallback: no SRV/CNAME at the underscore name -
                // check whether the bare host itself is CNAME'd elsewhere.
                Attribute apexCname = queryAttribute(ctx, bareHost, "CNAME");
                if (apexCname != null && apexCname.size() > 0) {
                    bareHost = stripTrailingDot((String) apexCname.get(0));
                    queryName = SRV_PREFIX + bareHost;
                    debug.log("SRV hop " + hop + ": CNAME on bare host -> following to " + bareHost);
                    continue;
                }

                debug.log("SRV hop " + hop + ": no SRV or CNAME found at " + queryName + " or " + bareHost + ", giving up");
                return null;
            }
            debug.log("Reached max CNAME hops (" + MAX_CNAME_HOPS + ") without finding SRV");
            return null;
        } catch (NamingException e) {
            debug.log("SRV lookup failed for " + host, e);
            return null;
        }
    }

    /**
     * Queries a single DNS record type in isolation, so a missing SRV (or
     * missing CNAME) at a given name can't cause the other type's lookup to
     * fail or be skipped.
     */
    private Attribute queryAttribute(DirContext ctx, String name, String type) {
        try {
            Attributes attributes = ctx.getAttributes(name, new String[]{type});
            return attributes.get(type);
        } catch (NamingException e) {
            return null;
        }
    }

    private String stripTrailingDot(String name) {
        return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
    }
}
