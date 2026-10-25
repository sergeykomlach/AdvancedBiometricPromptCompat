# Consumer manifest configuration

Choose each override only when neither your application nor an enabled biometric route needs
the feature. Library defaults retain legacy/OEM compatibility. Add `xmlns:tools="http://schemas.android.com/tools"`
to your application's `<manifest>` when using these overrides, then inspect the merged manifest.

| Declaration | Used by | Condition for removal |
| --- | --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Connectivity checks, translation fallback, dataset downloads | No application/SDK networking or OS network inspection needs these permissions |
| `CAMERA` | Legacy/OEM and software camera authentication routes | No selected route or application feature uses the camera |
| `WAKE_LOCK` | Legacy Facelock | No selected legacy route or other feature acquires a wake lock |
| `POST_NOTIFICATIONS` | Optional under-display authentication notification | Notifications are unused; `setEnabledNotification(true)` is not enabled |
| Boot permissions and `DeviceUnlockedReceiver` | Proactive emulated-lockout cleanup after boot/quickboot | The application accepts cleanup on later library initialization/unlock instead of a manifest boot receiver |

## Offline library

Set `dev.skomlach.common.NETWORK_ENABLED=false` as shown in the [common network policy](../common/README.md#localization-and-network-policy).
This prevents HTTP downloads, online translation and connectivity probes even though the permission declarations remain merged.
Only when the whole application can also omit these permissions, add the following under `<manifest>`:

```xml
<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" tools:node="remove" />
```

The library policy does not disable traffic initiated by another SDK or by application code.
Localhost hook-detection sockets also remain active. Removing `INTERNET` can prevent those
socket checks; retain the permission when that detection capability is required.

## Camera, Facelock and notifications

These are independent overrides under `<manifest>`; include only the lines for unused features:

```xml
<uses-permission android:name="android.permission.CAMERA" tools:node="remove" />
<uses-permission android:name="android.permission.WAKE_LOCK" tools:node="remove" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" tools:node="remove" />
```

Removing a declaration does not remove the associated provider. Keep selected routes and their
permission requirements consistent. Notifications remain disabled by default in the builder.

## Manifest boot receiver

To opt out of process-independent boot handling, remove the receiver under `<application>`:

```xml
<receiver
    android:name="dev.skomlach.biometric.compat.utils.DeviceUnlockedReceiver"
    tools:node="remove" />
```

If no other application component needs them, also remove these under `<manifest>`:

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" tools:node="remove" />
<uses-permission android:name="android.permission.QUICKBOOT_POWERON" tools:node="remove" />
```

The dynamic unlock listener and lazy reboot detection remain in library code. Keep the default
manifest receiver when proactive cleanup or OEM quickboot delivery is required. Changing it
to `exported=false` can prevent delivery from an OEM privileged package with a different UID;
that change requires verification on the affected OEM device.
