# Glance on Galaxy Watch / Wear OS

Owner target: Galaxy Watch7, Wear OS 6, One UI Watch 8.0. This is a companion
to the phone app, not a separate LibreLinkUp client. The owner's requested scope
adds a watch app, glucose/trend complication and optional watch notification to
the original phone-only baseline.

## Data and privacy

The phone remains the only account/session and polling owner. The watch receives
the current formatted glucose value, selected unit, source arrow/description,
measurement timestamp and a small status projection. No login credentials,
tokens, names, account IDs, connection IDs or history are sent. Phone Settings → Devices
controls **Share watch readings** and **Watch notification**, both initially off. Watch
sharing is separate from lock-screen values and Widget values: enabling it
permits visible readings on the watch face, including ambient mode.

Phone and watch use the same package and signing certificate. Play services
Data Layer transfers the projection through the paired Android/Wear OS link.
It can use Google's end-to-end encrypted cloud relay when Bluetooth is absent;
this is not a direct-Bluetooth-only promise. The watch has no LibreLinkUp account
and cannot continue fetching independently when the phone stops monitoring.

Stop, sign-out, person changes and sharing-off enqueue a content-free replacement
on the same DataItem. A single serial publisher orders it after an in-flight old
update. The watch rejects older revisions. Revocation cannot reach a disconnected
or powered-off watch immediately. A five-minute transport lease bounds cached
reading display; old measurement timestamps independently remove current-value
presentation at five minutes. Neither phone receipt time nor a duplicate source
response resets measurement freshness. Turning the watch sharing grant off uses
a checked write and, if necessary, removal of its dedicated grant file. If all
storage operations fail, rendering is blocked in the current process and the UI
directs the owner to turn off or remove Glance on the watch before restarting.

## Install the companion

Current source is 0.32.0 / code 37. See [release and upgrades](release.md) and
[APK downloads](../apks/README.md) for available signed builds and source.
The watch APK must be signed with the same certificate as the installed phone app.
For an existing Studio/debug phone, build the `wear` debug variant on the same
computer with the same key; a personal-signed watch cannot share with a differently
signed Studio phone. Do not uninstall the phone merely to align these signers. There
is no Play Store listing or automatic store installation in this personal build.
### Existing Android Studio phone installation (recommended for the owner)

Use the **same computer and checkout** that install Glance on your phone, so
Studio signs both apps with the same debug key. Installing only the phone app
does not automatically install this personal companion on the watch.

1. Keep the watch paired to the phone in Galaxy Wearable. Put the computer and
   watch on the same Wi-Fi network.
2. On the watch, open **Settings → About watch → Software information** and tap
   **Software version** seven times to enable Developer options. Menu wording
   can vary by watch software.
3. Open **Settings → Developer options**. Enable **ADB debugging** if offered,
   then **Wireless debugging → Pair new device**.
4. In Studio, open **Pair Devices Using Wi-Fi**, choose pairing by code, and
   enter the address/port and code shown by the watch. If Studio does not find
   it, use `adb pair` / `adb connect` as below. The connection port is on the
   main Wireless debugging page and can differ from the pairing port.
5. Select **Glance watch** beside Run, select the watch as the target, then Run.
   If Studio asks for a module, select **wear**, debug variant. If the shared
   configuration is absent, create an Android App run configuration for `wear`
   with default activity (or `com.khaleel.glance.wear.MainActivity`).
6. Open Glance on the watch. On the phone, enable **Settings → Devices → Share
   watch readings** and start monitoring. Keep both devices paired/connected.
7. Edit a watch face with a text complication slot and choose **Glance glucose**
   to see the value on the face. The app does not add this automatically.

Optional: enable **Watch notification** on the phone and enable notifications
inside the watch app, granting its notification permission. Turn wireless
debugging off after installation. Never uninstall the phone to resolve a signer
mismatch; use the same computer/key instead.

### APK / command-line installation

For sideloading, enable Developer options and Wireless debugging on the watch,
use the watch's pairing code and pairing address with `adb pair`, then its
separate debugging address/port with `adb connect`. For example:

```sh
adb pair WATCH_IP:PAIRING_PORT
adb connect WATCH_IP:DEBUG_PORT
adb -s WATCH_IP:DEBUG_PORT install -r apks/0.32.0/glance-0.32.0-watch.apk
```

This command installs the personal-signed watch APK from the repository.
If you downloaded it separately, use that file's actual path instead. This is
for a matching personal-signed phone installation. For a Studio/debug phone,
build `./gradlew :wear:assembleDebug` on its original computer and install
`wear/build/outputs/apk/debug/wear-debug.apk` instead. Mixing those signing keys
prevents Data Layer sharing.
Use the addresses and ports actually shown on the watch; they can differ. The
pairing code is entered locally into adb, not pasted into Chat. Disable wireless
debugging when finished. Existing phone installation and LibreLinkUp settings
are preserved by the same-key phone upgrade.

Open Glance on the watch. On the phone, enable **Share watch readings** in Settings → Devices and
start monitoring. The watch app shows the value/unit, source arrow, and reading
time, or an explicit stopped/waiting/hidden/old-data state. A long-expired sync
shows an instruction to open the phone rather than a cached glucose number.

## Complication and notifications

Edit a watch face with a text complication slot, choose **Glance glucose**, and
save. Both short and long text slots are supported; the watch face controls the
rendered layout and may omit optional title fields. Tap the complication to open
Glance. No progress gauge is invented for glucose. Source LOW/HIGH and unknown
trend are represented honestly.

Complication requests follow Wear OS's five-minute average guidance. A timeline
and expiry remove a current-looking number when the reading or phone lease
expires, even while Glance is not executing. A complication can briefly show a
status while awaiting its next permitted update; the watch app reads the newest
received projection when open. Actual Samsung face update/expiry behavior needs
the owner check. This is not a guaranteed one-minute complication update.

The AndroidX persistence-policy setter is restricted to its library; Glance uses
public validity ranges and timelines instead. The watch face may cache complication
data, so the five-minute display bound depends on the face honoring those ranges.
This is a display-expiry policy, not a promise to erase the face's private cache.

For the silent watch card, enable **Watch notification** on the phone and enable
notifications inside the watch app, granting the watch's notification permission
when requested. It updates one local silent card, with an absolute measurement
time and bounded expiry. It is not an alarm or a guarantee of permanent
undismissability. The existing threshold alerts retain their phone channel and
Galaxy Wearable bridging choices; there is no blanket notification bridge
disable and no second watch alert engine or fetch worker.

## Build and limits

Build and test both modules with:

```sh
./gradlew :app:testDebugUnitTest :wear:testDebugUnitTest \
  :app:assembleDebug :wear:assembleDebug
```

The watch debug output is `wear/build/outputs/apk/debug/wear-debug.apk`.
Use that path in the `adb install -r` command for a same-key Studio/debug pair. `python3 scripts/sign_release.py`
does clean unit/lint/release verification and signs both packages using the
existing key outside the repository. Source and signing metadata are in the
release manifests. Physical Watch7 pairing, face rendering, wrist/ambient
presentation, disconnected timing and battery measurements remain separate
qualification checks. See [watch-validation.md](watch-validation.md).

Official references checked 2026-10-21:

- [Data Layer transport and package/signature security](https://developer.android.com/training/wearables/data/overview)
- [Pair and connect a watch over Wi-Fi](https://developer.android.com/training/wearables/get-started/debug-wifi)
- [Complication service, timelines and push updates](https://developer.android.com/training/wearables/complications/exposing-data)
- [Native and bridged watch notifications](https://developer.android.com/training/wearables/notifications)
- [Ongoing/non-clearable phone cards are not bridged](https://developer.android.com/training/wearables/notifications/bridger)
