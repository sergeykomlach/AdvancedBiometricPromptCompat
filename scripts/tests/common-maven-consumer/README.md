# Independent common release consumer

This project resolves only `dev.skomlach:common` and its published transitive dependencies.
Its exclusive local repository prevents fallback to Maven Central/Maven Local; it has no
project dependencies or dependency substitution. `verifyCommonMinified` uses R8 and the
published consumer rules, without blanket keep rules. Both verification tasks reject
OkHttp/Okio in the resolved dependency graph.
The existing `java-aes-crypto` dependency is resolved from a JitPack repository restricted
to `com.github.tozny`, matching the library's supported Maven consumer setup.

From the library root, stage fresh unsigned release inputs:

```powershell
.\gradlew.bat -I scripts/tests/stage-common-maven-consumer.init.gradle stageCommonMavenConsumerRepository --no-configure-on-demand --console=plain
```

Supply `ANDROID_HOME` or this consumer's ignored `local.properties` with only `sdk.dir`.
Generate short-lived test certificates into a fresh directory outside source control:

```powershell
pwsh -NoProfile -File scripts/tests/common-maven-consumer/new-probe-fixtures.ps1 -Destination C:/temp/common-probe-fixtures
.\gradlew.bat -p scripts/tests/common-maven-consumer '-PcommonVersion=2.4.rc-50' '-PmavenConsumerRepository=C:/path/to/checkout/build/common-maven-consumer/2.4.rc-50' '-PprobeTlsFixtures=C:/temp/common-probe-fixtures' :app:verifyCommonDebug :app:verifyCommonMinified :app:assembleDebugAndroidTest --console=plain
```

Use the actual candidate version/repository. Fixtures are self-signed test keys with the
public test password `probe-only`, never production credentials. The test CA is trusted only
by this consumer's debug configuration. Keys are packaged only in the instrumentation APK;
the common AAR and minified consumer contain neither fixture keys nor a debug trust override.

When iterating at the same unpublished version, copy the verified AAR, sources, Javadoc
and POM to a new local repository directory before testing the changed candidate. This
keeps an earlier immutable-coordinate cache entry from substituting an older AAR.

On an isolated Android QA device, install the debug APK and debug-androidTest APK. The VPN
test deliberately stalls that device's entire Internet route, then closes it in `finally`:

```powershell
adb -s emulator-5560 install -r scripts/tests/common-maven-consumer/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5560 install -r scripts/tests/common-maven-consumer/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5560 shell appops set dev.skomlach.common.releasecheck ACTIVATE_VPN allow
adb -s emulator-5560 shell am instrument -w -e class dev.skomlach.common.releasecheck.NativeInternetTest dev.skomlach.common.releasecheck.test/android.test.InstrumentationTestRunner
```

Tests use real Android routing, TLS and certificate/hostname verification. They cover numeric
HTTPS, numeric PAC proxy fallback with original TLS hostname, rejected trust/hostname,
bounded stalled HTTPS, one completion, a healthy fourth endpoint despite two stalled
requests, and a dual-stack black-hole VPN becoming offline and recovering without process
restart. The VPN test requires a working Internet baseline.
This controlled tunnel does not reproduce an actual overnight OEM/VPN-provider failure.

Install/launch the minified APK separately and confirm `CommonReleaseQA` logs reach
`COMMON_RELEASE_STATE=AVAILABLE; connected=true`. On Android 23–28 also validate the documented
native-DNS recovery limitation on supported devices before claiming device-wide coverage.
