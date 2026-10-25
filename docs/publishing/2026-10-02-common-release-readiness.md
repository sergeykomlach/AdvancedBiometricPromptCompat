# common 2.4.rc-50: reviewed release candidate, 2026-10-02

Checkout: `C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat`, branch `main`,
base HEAD `f99872b2115c9aca2dafee04e4862d3f857e15cb`. Candidate: `2.4.rc-50`,
shared library version code `287`. Scope: `common` and its independent Maven consumer.

## Verdict and code review

No remaining actionable code blocker was confirmed by the final scoped source review.
The final candidate passed 60 targeted JVM tests, release artifact inspection, an
independent Maven/R8 consumer, seven Android transport tests and a minified runtime smoke.
These are separate source, build and runtime results; they do not prove every device or
Internet service is reachable. Other publication modules are outside this qualification.

Review covered native DNS/HTTP/TLS, route selection, monitor lifecycle, generation guards,
endpoint preference, cancellation, resource bounds, changed device-cache helpers and
their direct dependencies. The read-only reviewer rechecked the correction below and
reported no remaining Critical/Important/Minor issue. Build gates were run by the root
coordinator separately.

The review found and resolved legacy DNS starvation: on API 23-28, two stuck native
queries on a Network could reject healthy endpoints three and four. Admission now allows
four native lookups per route and eight globally, matching the configured endpoint limit
and reserving a full round for a replacement route. Accounting remains occupied until
the native worker actually returns, even after cancellation. Regression tests exercise
healthy third/fourth lookups, replacement-route admission and the eight-worker bound.

Cleanup clarified the probe plan/sequence guard and executor configuration, removed
repeated preference setup from tests, and updated consumer verification documentation.
The existing route, TLS, atomic-completion and cache-refresh corrections remain included.
No commit, push, signing or publication was performed. Root `postRelease` would publish
the configured library batch, which this common-only report does not qualify.

## Final behavior and performance

- Android callbacks and watchdogs track the effective process/default Network, including
  VPN and process binding. Native DNS and sockets use that same route; probes never select
  an underlying transport to bypass VPN. HTTP/PAC alternatives retain order and require
  original-host TLS verification. DIRECT is used only if the selector permits it.
- Android validation is route metadata; it cannot overwrite a measured failed Internet
  check with a stale positive result. Any authenticated HTTP status confirms reachability.
- Reads of `hasInternet()` after initialization use the cached shared monitor without
  Android-service/DNS/socket I/O. Explicit refresh APIs still refresh OS metadata.
- The last confirmed verdict is retained during wake, handover and ongoing checks.
  One successful round confirms online; two failed rounds confirm offline. Continuously
  unusable routes have a one-second grace period. Boolean listeners suppress duplicates.
- Stable-route checks prefer the last successful endpoint. Backups start immediately on
  failure or after a 250 ms silent stall. A fast success opens only one endpoint request.
  Initial/configuration/route-snapshot changes race all configured endpoints. Old or
  cancelled completions cannot replace a newer preference.
- Each endpoint retains its full two-second deadline. The round budget is 2.25 seconds;
  foreground success checks repeat after 2.5 seconds, with a one-second second-failure
  delay. A stable-route silent freeze is normally detected within about eight seconds
  while the process executes. This is not a wall-clock guarantee through Doze/suspension.
- Background checks/retries use a sixty-second interval. Metadata-only changes on the
  same usable background route preserve that schedule; meaningful route/validation/
  transport changes and wake still trigger checks.
- Four bounded request workers prevent two stalled hosts from starving a healthy third
  or fourth. The queue is bounded at four and idle request workers retire after thirty
  seconds. Legacy DNS is bounded separately at four per route/eight globally.

## Targeted JVM and release artifact gate

From the library root:

```powershell
.\gradlew.bat -I scripts/tests/stage-common-maven-consumer.init.gradle :common:testDebugUnitTest --tests 'dev.skomlach.common.network.InternetProbeTest' --tests 'dev.skomlach.common.network.InternetConnectivityMonitorTest' --tests 'dev.skomlach.common.network.InternetRequestExecutorTest' --tests 'dev.skomlach.common.network.LegacyDnsResolverTest' --tests 'dev.skomlach.common.network.NetworkApiSecurityTest' stageCommonMavenConsumerRepository --no-configure-on-demand --console=plain
```

Passed: `BUILD SUCCESSFUL`, 1m 1s; XML reports contain 60 tests, zero failures/errors.

| Class | Passed |
|---|---:|
| InternetProbeTest | 17 |
| InternetConnectivityMonitorTest | 36 |
| InternetRequestExecutorTest | 3 |
| LegacyDnsResolverTest | 3 |
| NetworkApiSecurityTest | 1 |

This gate builds the current AAR, sources, Dokka/Javadoc and POM and verifies native
classes/sources, documentation entries, the rc-50 coordinate and no OkHttp dependency.
Its task-graph guard forbids signing, Maven Local/remote publication and `postRelease`.
Earlier unchanged route/cache/TLS regression classes had passing scoped evidence; the
whole repository suite was not rerun after this cleanup and is not claimed here.

The verified artifacts were copied with matching SHA-256 into a fresh exclusive consumer
repository to avoid using an earlier cached rc-50 candidate:
`build/common-maven-consumer/2.4.rc-50-cleanup-20261002/dev/skomlach/common/2.4.rc-50`.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| common-2.4.rc-50.aar | 2224405 | 8B820B6602F83E6EDAC220AC50047341F84FDA78023ED4169700A9A2F585FE51 |
| common-2.4.rc-50-sources.jar | 166219 | 665E35EAEEF0CAEE56D4A5922580C73926C9E460B436DD46650AD45BE691DCF1 |
| common-2.4.rc-50-javadoc.jar | 2085719 | 11E8A06DC985D5D5E15B4E8A9B61A01D87137C532B90B1DBA3485FE7FB10B5AE |
| common-2.4.rc-50.pom | 3969 | 083FF852987A1F482477F40336E6AAE7AF1FF3B82C9FC25210BEEC1222E0716B |

## Independent Maven/R8 consumer

```powershell
.\gradlew.bat -p scripts/tests/common-maven-consumer '-PcommonVersion=2.4.rc-50' '-PmavenConsumerRepository=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat/build/common-maven-consumer/2.4.rc-50-cleanup-20261002' '-PprobeTlsFixtures=C:/Users/skoml/StudioProjects_5/rf-dev/.tmp/common-production-ready-20261002/tls-fixtures' :app:verifyCommonDebug :app:verifyCommonMinified :app:assembleDebugAndroidTest --console=plain
```

Passed: `BUILD SUCCESSFUL`, 1m 15s. Debug and minified graphs resolve only
`dev.skomlach:common:2.4.rc-50` from this project and reject OkHttp/Okio. There are no
project dependencies, substitutions or Maven Central/Maven Local fallback for
`dev.skomlach`. JitPack is restricted to the existing `com.github.tozny` dependency.
The minified variant ran R8/resource shrinking with published consumer rules and no
blanket keep rule.

| APK | Bytes | SHA-256 |
|---|---:|---|
| app-debug.apk | 14138502 | 4FB19E421113029B086C23AF8F2EF9825CCE734856815E44C23296C5C58F2D43 |
| app-debug-androidTest.apk | 38055 | F9997E9B002D1F1283ABAEC7DFD8CAF2E3C499ABDA054FFEC7673ED831334FE0 |
| app-minified.apk | 3550009 | D0CF1421BA3CFC53544114274CCC467AC8BA7BB7B9706A937D6A49A051AC9374 |

The debug consumer trusts only the short-lived QA CA in its test configuration. Fixture
keys are confined to the instrumentation APK. The minified APK/merged manifest were
inspected: no fixture keys, test CA, network-security override or VPN test service.
Fixtures are outside source control and are not staged.

## Android runtime evidence

Isolated headless read-only `Medium_Tablet` AVD: Pixel Tablet, API 35/Android 15, x86_64,
`emulator-5560`. It was shut down after verification without saving a snapshot.

```powershell
adb -s emulator-5560 install -r scripts/tests/common-maven-consumer/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5560 install -r scripts/tests/common-maven-consumer/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5560 shell appops set dev.skomlach.common.releasecheck ACTIVATE_VPN allow
adb -s emulator-5560 shell am instrument -w -e class dev.skomlach.common.releasecheck.NativeInternetTest dev.skomlach.common.releasecheck.test/android.test.InstrumentationTestRunner
```

Passed: `OK (7 tests)`, 15.484s. Real Android checks cover trusted numeric HTTPS,
wrong-hostname/untrusted-certificate rejection, bounded stalled HTTPS with one completion,
numeric PAC fallback retaining the original TLS hostname, a healthy fourth endpoint
despite two stalled requests, and a stalled dual-stack VPN becoming offline/recovering.

The VPN retained an OS transport but became offline in **4200 ms** and recovered **1512 ms**
after tunnel closure. Both events used PID **6114**, without process restart. The harness
asserts the real default VPN Network disappears before recovery.

```powershell
adb -s emulator-5560 install -r scripts/tests/common-maven-consumer/app/build/outputs/apk/minified/app-minified.apk
adb -s emulator-5560 shell am start -W -n dev.skomlach.common.releasecheck/dev.skomlach.common.releasecheck.MainActivity
adb -s emulator-5560 shell dumpsys package dev.skomlach.common.releasecheck
adb -s emulator-5560 logcat -d --pid=6316 -v threadtime -s CommonReleaseQA AndroidRuntime
```

The installed minified rc-50 package was not debuggable. Cold launch PID **6316** reported
`AVAILABLE; connected=true` from **13:35:07.601 through 13:35:37.941 UTC**, with no fatal
AndroidRuntime entry in that PID capture. Initial CHECKING was followed by confirmation.
A PowerShell array `-notmatch` assertion initially misclassified nonmatching log lines;
the corrected assertion passed against the saved capture without rerunning the app.

Raw logs/hashes: `C:/Users/skoml/StudioProjects_5/rf-dev/.tmp/common-network-performance-20261002/`,
files prefixed `cleanup-`. Older captures in that directory are separate candidate runs.

## Changes and verification limits

The cleanup/optimization delta touches `common/README.md`, network probe/monitor/API/
executor/legacy DNS sources, their four test classes, consumer Android tests/README,
`stage-common-maven-consumer.init.gradle` and this report. The wider staged candidate
includes the preceding route/cache/native-TLS implementation and version/manifest changes.
Affected production module: `common`; verification module: the independent consumer app.

- API 23-28 native DNS/TLS, physical cellular/Ethernet/Bluetooth, production enterprise
  PAC/proxy authentication, Private DNS, prolonged Doze and overnight OEM/VPN-provider
  failures were not device-tested. Source/model tests and a controlled API 35 tunnel
  cannot establish those runtime claims.
- Public pre-29 DNS cannot reliably abort a stuck native lookup. If all eight workers
  remain stuck, later lookups fail closed until one returns. Capacity is finite.
- There was no battery/traffic benchmark. Fewer requests in the fast stable-route unit
  scenario and bounded workers are verified behavior, not a measured battery saving.
- Configured public HTTPS reachability does not establish availability of every service.
  RoboForm license/unlock requests still need their own deadlines/cancellation; that
  application flow was not changed or device-tested here.
- Full repository tests, all publication modules and remote publication were not run.
  Existing Gradle/API-23 TLS/Dokka deprecation warnings did not fail the scoped gates.

## Final index checks

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --cached --check
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --name-only -- common build.gradle scripts/tests/common-maven-consumer scripts/tests/stage-common-maven-consumer.init.gradle docs/publishing/2026-10-02-common-release-readiness.md
```

Task paths are synchronized into the index. The cached whitespace gate passed, and the
task-scoped working-tree diff is empty. Unrelated dirty/untracked files remain untouched.
