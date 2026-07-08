# SafeTransfer

An admin-only Paper/Folia plugin that transfers players to another Minecraft
server using the vanilla **transfer packet** (`Player#transfer`) - but only
after confirming the destination is actually up.

## What it does

Running `/safetransfer` doesn't just fire the transfer packet and hope for
the best. For every invocation it:

1. **Resolves** the destination the same way a real Minecraft client does:
   - `host:port` typed explicitly is used as-is.
   - A bare `host` triggers an `SRV` lookup at `_minecraft._tcp.<host>`,
     following `CNAME` chains on either the SRV owner name or the bare host
     (both patterns exist in the wild - see [Address resolution](#address-resolution)).
   - IP literals and `localhost` skip SRV entirely, matching vanilla.
2. **Pings** the resolved address with a real Minecraft status handshake
   (raw TCP, proper packet framing, the server's actual protocol version -
   not a scanner-fingerprint `-1`) to confirm something is actually
   listening and speaking the protocol.
3. **Only if the ping succeeds**, transfers the requested player(s) via
   `Player#transfer`, using the *original* hostname (for proxy virtual-host
   routing) and the *resolved* port.

If the ping fails, the command aborts entirely - nobody is transferred.

## Requirements

- Paper (or a Paper fork, including **Folia**) **1.20.6+** - the earliest
  release with `Player#transfer` (the transfer packet), which this plugin is
  built around. `Core#onEnable` checks for that method at startup and
  disables itself with a clear log message on anything older, since
  `plugin.yml`'s `api-version` field can't express patch-level granularity.
- Java 21 (required by Minecraft/Paper itself from 1.20.5 onward)

## Installation

1. `mvn clean package`
2. Drop `target/SafeTransfer-*.jar` into your server's `plugins/` folder.
3. Restart (or reload) the server. A default `config.yml` is generated on
   first run.

## Commands & permissions

| Command | Aliases | Permission | Default |
|---|---|---|---|
| `/safetransfer <target> <host[:port]> [delayTicks]` | `/st` | `safetransfer.use` | `op` |
| `/streload` | - | `safetransfer.reload` | `op` |

- `<target>` - a player name, or a vanilla entity selector (`@a`, `@a[...]`,
  `@p`, etc.). Only online players are considered; non-player selector
  matches are ignored.
- `<host[:port]>` - the destination. See [Address resolution](#address-resolution).
- `[delayTicks]` *(optional)* - overrides `default-delay-ticks` from config
  for this invocation. Only matters when `<target>` resolves to more than
  one player.
- `/streload` - re-reads `config.yml` (timeouts, delay, debug flag, messages)
  without restarting the server. Separate permission from `safetransfer.use`
  so reload access can be granted independently.

All three arguments tab-complete: player names/`@a` for the target, recently
used addresses (plus `localhost`) for the host, and common tick values for
the delay.

### Example

```
/safetransfer @a play.example.com 40
```

Resolves `play.example.com` (following SRV/CNAME as needed), pings it, and -
if reachable - transfers every online player, 40 ticks (2s) apart.

## Address resolution

SafeTransfer mimics the Notchian client's server-list address resolution,
including two CNAME patterns that show up with real-world DNS/hosting
setups:

1. **SRV-name CNAME**: `_minecraft._tcp.<host>` is itself a `CNAME` to
   another (possibly already-prefixed) SRV owner name.
2. **Apex/bare-host CNAME**: the bare `<host>` is `CNAME`'d to a hosting
   provider's domain, and it's *that* domain's own
   `_minecraft._tcp.<provider-domain>` which carries the real SRV record -
   the underscore name under your own domain is never aliased at all.

Both patterns are tried at every hop (up to 10, to bound pathological
chains), and the original hostname you typed is always preserved separately
for the handshake/transfer - proxies that route by virtual host (BungeeCord,
Velocity, TCPShield, etc.) require that, and will silently drop connections
where the handshake host doesn't match what they expect.

## Configuration

See the heavily-commented [`config.yml`](src/main/resources/config.yml) for
every option - ping timeouts, mass-transfer pacing, the `debug` flag, and
every player-facing message (in [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
format, fully editable).

If a transfer that should work is being reported as unreachable, set
`debug: true` and reproduce - the console will log every DNS hop tried and
the exact failure point instead of just the short chat message.

## Java API

Other plugins can trigger a transfer programmatically through
`SafeTransferAPI`, without going through the command - it's the same
resolve -> ping -> transfer flow, but silent (no chat messages to anyone;
callers get a `TransferOutcome` back instead). Add SafeTransfer as a
`provided`-scope compile dependency, then fetch the service at runtime:

```java
import me.hippodev.safeTransfer.api.SafeTransferAPI;
import me.hippodev.safeTransfer.api.TransferOutcome;

SafeTransferAPI api = Bukkit.getServicesManager().load(SafeTransferAPI.class);
if (api != null) {
    api.transfer(somePlayer, "play.example.com").thenAccept(outcome -> {
        if (outcome.reachable()) {
            // transfer was queued - outcome.host()/outcome.port() show the resolved address
        } else {
            getLogger().warning("Transfer failed: " + outcome.failureReason());
        }
    });

    // Mass transfer, staggered 20 ticks apart:
    api.transfer(Bukkit.getOnlinePlayers(), "play.example.com", 20);
}
```

`SafeTransferAPI` may be `null` if SafeTransfer isn't installed or failed to
enable (e.g. server below 1.20.6) - always null-check before use. All methods
are safe to call from any thread and return immediately.

## Folia support

SafeTransfer uses Paper's region-aware scheduler API throughout
(`GlobalRegionScheduler` for non-entity-bound work, each `Player`'s own
`EntityScheduler` for the actual transfer), so it runs unmodified on both
regular Paper and Folia. `folia-supported: true` is set in `plugin.yml`.

## Building from source

```
mvn clean package
```

Produces a shaded jar at `target/SafeTransfer-<version>.jar`.

## CI (Jenkins)

A [`Jenkinsfile`](Jenkinsfile) is included: it runs `mvn clean package`,
archives the resulting jar as a build artifact, then generates and publishes
Javadoc via the [`javadoc` pipeline step](https://www.jenkins.io/doc/pipeline/steps/javadoc/).
It expects:

- a JDK tool named `JDK 21` and a Maven tool named `Maven 3`, configured
  under **Manage Jenkins → Tools** (rename in the `Jenkinsfile`'s `tools {}`
  block if yours differ)
- the [Javadoc Plugin](https://plugins.jenkins.io/javadoc/) installed, which
  provides the `javadoc` step used to publish `target/site/apidocs`

Point a Jenkins Pipeline (or Multibranch Pipeline) job at this repo and it
will pick the `Jenkinsfile` up automatically.

Builds run at [jenkins.mwtw.net](https://jenkins.mwtw.net/).
