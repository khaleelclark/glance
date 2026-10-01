# Glance glucose widget and Android Auto

Requested reference: the owner's iOS 26 Gluroo CarPlay widget next to Maps and
music, showing a large number, source arrow, change, source time and small graph.
No Gluroo logo or image assets are copied. Implemented with stable Jetpack
Glance 1.2.0 as a passive Android AppWidget.

## Platform boundary

Google's [car-widget design guide](https://developer.android.com/design/ui/cars/guides/flows/widgets)
(updated May 12, 2026) directs developers to adapt mobile widgets with responsive
sizes, themes and no manual scrolling. Its
[May 19 announcement](https://developer.android.com/blog/posts/whats-new-in-android-for-cars-unifying-platforms-and-unlocking-premium-experiences)
promises mobile widgets for the next Android Auto design during 2026. These
documents do not prove this feature is enabled on this user's phone/car.
Do not advertise an ordinary AppWidgetHost screenshot as Android Auto evidence.

Glance registers a regular AppWidget provider and adds no fake navigation,
messaging or media category. It does not modify Maps, use overlays, write glucose
readings to a calendar, add a backend or request calendar access. On older
dashboards, the weather/media/navigation tiles are system-controlled; Glance
cannot replace them with this AppWidget. Actual car widget discovery and any
sideloading restrictions remain device-only checks.

## Setup

1. Install the personal-signed APK over the prior build. Existing account and
   notification choices are preserved.
2. In Glance Settings, enable **Widget values**, which explicitly permits
   glucose/history display on phone/car widget surfaces even while locked.
3. Start monitoring, then add **Glance glucose** from the phone's widget picker.
   Resize it for the graph. Smaller sizes keep the value, unit and source time.
4. On a connected, parked car, inspect Android Auto Settings for its widget
   picker. If available, choose **Glance glucose** there. Google controls placement
   and availability; matching CarPlay's exact dashboard arrangement is not promised.
5. Compare the widget's source measurement timestamp and value with Glance.
   Check delayed/offline and hidden states before relying on the display.

## Data and lifecycle

The app observes the existing controller StateFlow; widgets never initiate a
network fetch or start monitoring. Main-screen settings changes update placed
widgets. Data stays in the existing encrypted stores, not in a plaintext widget
snapshot. Intents carry no account or glucose data. Host-rendered views necessarily
contain the values only after explicit opt-in. Hidden mode contains no patient
name, value, measurement time or chart. Sign-out resets the opt-in.

Every render recomputes freshness from source time and rejects a current reading
belonging to another person. Graph history covers the four hours ending at that
measurement, only same-person numeric samples, and excludes future/special
samples. Current corrections win at their own timestamp; other latest-received
corrections win in history. Gaps over fifteen minutes are left unjoined. Delta
uses the latest eligible earlier source sample within fifteen minutes, with the
actual elapsed interval and selected units. Unknown trends are not invented.

Ongoing monitoring drives updates. The host's 30-minute refresh is housekeeping,
not a CGM polling schedule. Android may delay updates or retain a cached view
when a process is suspended/force-stopped. The source measurement time is always
present, and the widget makes no autonomous freshness guarantee during suspension.
It does not display a static Live/connected badge.

Rendering uses the stable `GlanceAppWidget.compose` snapshot API, with a mutex
serializing compositions for each bound ID, then sends the resulting RemoteViews
to AppWidgetManager. The ordinary AppWidgetProvider handles update/resize events
with bounded `goAsync` work. It avoids Glance's default 45-second SessionWorker
on each CGM update. It has no widget actions requiring a live composition. Each
render compares state, privacy, account and units before publication; a transition
during rendering discards and redacts the preceding snapshot. Privacy opt-out and
stop actions also synchronously clear the host view. Failures leave a generic
redacted card. Battery impact remains unmeasured on the physical phone.

[Snapshot API](https://developer.android.com/reference/kotlin/androidx/glance/appwidget/package-summary#compose(androidx.glance.appwidget.GlanceAppWidget,android.content.Context,androidx.glance.GlanceId,android.os.Bundle,androidx.compose.ui.unit.DpSize,kotlin.Any))
and the pinned 1.2.0 sources were checked for lifecycle behavior.

## Verification

Focused unit tests cover privacy, selected identity, source corrections, graph
window/gaps, unit conversion, actual delta interval, source freshness, stopped and
connection states. Emulator checks use a debug-only real Android AppWidgetHost
at different sizes, themes and font scales. Debug fixtures have visible DEMO
labels and are absent from release manifests/classes. The evidence bundle and
release manifest record the delivered source and final observed results.

On 2026-10-01, the API 36.0 Google APIs x86_64 emulator passed the fixture
content checks and the actual production provider/controller checks: hidden
mode removed all patient text/chart, unit changes updated the value, stopping
removed the current presentation, and account clearing replaced it with Sign in.
The host listener lifecycle was corrected before these final checks. No actual
Android Auto display was used.

- [Large synthetic card](evidence/api36-widget-0.17-fresh-large.png)
- [Dark mmol/L at 1.5× font](evidence/api36-widget-0.17-dark-mmol-largefont.png)
- [Compact mmol/L at 1.5× font](evidence/api36-widget-0.17-small-mmol-largefont.png)
- [Hidden card](evidence/api36-widget-0.17-hidden.png)
- [Production provider with synthetic monitored source](evidence/api36-widget-0.17-integration-active.png)

Actual Android Auto hosting, simultaneous Maps/music/widget placement, Samsung
scaling, real account parity, overnight scheduling and battery remain unverified.
