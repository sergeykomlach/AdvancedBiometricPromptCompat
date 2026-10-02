# Common Android utilities

## Internet availability

`NetworkApi.hasInternet()`, `refreshConnectionState()`, `networkState` and legacy
`Connection.NetworkListener` callbacks share one automatically measured Internet verdict.
Reads never wait for DNS or sockets. They are safe to use from the main thread.
After shared monitor initialization, `hasInternet()` reads the cached verdict without
querying Android services; callbacks and watchdogs maintain the route state. Use
`refreshNetworkState()` or
`refreshConnectionState()` when an explicit OS metadata refresh is required.

The native probe uses Android's selected process/default `Network` for both DNS and
sockets, including a process-bound route or VPN. It does not try underlying Wi-Fi or
cellular routes to bypass a VPN. Android's process proxy selector is honored for HTTP
proxies and PAC; CONNECT is followed by TLS for the original endpoint hostname.
Unsupported proxy types or proxy authentication failures do not prove Internet access.
Only Android/JDK networking is used; `common` has no OkHttp dependency.

TLS uses platform certificate trust and HTTPS hostname verification. Probes send no
account credentials, cookies, request body or authentication response, and do not follow
redirects, reuse idle connections or log response content. Any valid HTTP status from
the TLS-authenticated endpoint, including 401 or 503, proves transport reachability.
It does not prove that an application's license/authentication server is available or
that an authentication operation succeeded. Application requests still need their own
timeouts and cancellation.

### Confirmation and timing

- Before the first result, `internetAccess` is `CHECKING` and the Boolean is `false`.
  A consumer needing the initial confirmed result should observe `networkState` rather
  than treat its first Boolean read as a completed network check.
- One successful probe confirms online. Two failed/timed-out probes confirm offline.
  A continuously unusable route confirms offline after a one-second grace period.
- Checks, wake-up and route changes preserve the last confirmed verdict until the next
  confirmation. Brief handovers do not force an offline/online notification pair.
  Identical Boolean results do not repeatedly notify legacy listeners.
- In an interactive foreground application, successful checks repeat after 2.5 seconds.
  Each endpoint has a two-second total deadline, and a whole check has a 2.25-second
  budget including a possible backup delay; the second failure is checked after one
  second. A silent freeze of a stable route is normally detected within about eight
  seconds after the last successful check while the process is scheduled.
- In the background or with the screen off, periodic checks/retries are spaced at least
  sixty seconds apart. An initial verdict and a pending second-failure confirmation are
  still completed promptly. Returning to the foreground with the screen on checks
  immediately and retains the previous verdict during the check.
  Ordinary metadata changes on the same usable background route do not bypass this
  sparse schedule; validation, transport, blocked/lost-route and wake events still react.
- Android Doze, process suspension and termination can delay all checks. These intervals
  are not wall-clock guarantees while Android prevents the process from executing.

### Probe endpoints and restricted networks

Defaults are `https://www.google.com/generate_204` and
`https://www.cloudflare.com/cdn-cgi/trace`. Up to four HTTPS endpoints can be configured:

```kotlin
NetworkApi.setInternetCheckEndpoints(listOf("https://your-public-service.example/health"))
```

Endpoints must have a valid host/port and no credentials, query or fragment. Use a public,
unauthenticated endpoint covered by the application's normal TLS trust configuration.
Configure this once during application setup. The configuration applies to all consumers
of the shared Boolean verdict in that process.

These probes necessarily reveal the device's outbound IP and a connectivity request to
the selected host/proxy. If a network blocks all configured hosts while allowing another
service, the verdict is offline. Restricted deployments should configure a reachable
endpoint; a two-host probe cannot prove reachability of every Internet service.

The library manifest declares `INTERNET` and `ACCESS_NETWORK_STATE`; Android merges
these normal permissions into the consuming application's manifest.

Numeric IPv4/IPv6 hosts and resolved proxy addresses do not use DNS. DNS address
candidates alternate IPv4 and IPv6 before the bounded candidate limit is applied.
HTTP proxy/PAC alternatives keep their configured order and share one request deadline.
A dead first proxy cannot consume the entire deadline; `DIRECT` is used only if the
system selector explicitly allows it. Every alternative uses the same selected `Network`.

Initial, changed-route and changed-configuration checks race all configured endpoints.
On the same route snapshot, the last successful endpoint is tried first. A fast response
cancels backups before opening their sockets; a failure starts them immediately and a
silent stall starts them after 250 ms. Every backup retains its full two-second endpoint
deadline. Cancellation or a failed round clears the preference; an old completion cannot
replace a newer route/configuration's preference. All four configured endpoints can run
concurrently, so two stalled hosts cannot starve a healthy third or fourth. The request
pool/queue are bounded, and idle request workers are released after thirty seconds.

Device dataset refreshes asynchronously wait for the initial confirmed Internet verdict
(up to ten seconds) instead of treating `CHECKING` as a confirmed offline result. Concurrent
refreshes are shared; the seven-day check timestamp advances only after every dataset is
already fresh or its download and cache write succeed. A skipped or failed batch may retry
on the next device-info request; bundled assets remain available during refresh.

### Older Android DNS

Android 29+ uses cancellable `DnsResolver` queries. Android 23–28 has no public equivalent
that reliably aborts `Network.getAllByName()`. Its callers still have a bounded wait.
At most four native lookups run per route and eight in total, so all configured endpoints
can resolve while workers remain reserved for a new
route when an old route's queries remain stuck. Cancellation does not prematurely free
the accounting for a still-running native call. If all eight native calls remain stuck,
new lookups fail closed until a worker returns; unconditional recovery cannot be promised
with the public pre-29 DNS API. Validate dead-DNS recovery on supported old devices.

## Release verification

Unit tests and a sample APK compiled against project dependencies do not verify a Maven
publication. Before publishing a new version, build and inspect its current AAR, sources,
Javadoc and POM, then test an independent Maven consumer, including a minified variant.
Use a fresh version coordinate; an existing locally cached `2.4.rc-49` contains older code.

See `../docs/publishing/README.md` for the release workflow. The root coordinator and
`:common:postRelease` upload the selected library batch, not only `common`. Publishing,
signing and namespace transfer need a separately authorized release operation.
