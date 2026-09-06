# Privacy

Somn is a sleep tracker. It runs on your phone, stores what it records on your phone, and has
no server. There is no Somn account, no Somn backend, and no analytics.

This document describes exactly what the app holds, what can leave the device, and how to get
rid of any of it. It describes the code in this repository — you can check every claim here
against the source.

## What Somn stores

All of it lives in a Room database and app-private files inside Somn's own storage directory.
Other apps cannot read it.

| Data | Where | Notes |
|------|-------|-------|
| Sleep sessions, stages, scores | Room DB | Core tracking data |
| Biological profile — birth date, sex, life stage, menstrual cycle, pregnancy/postpartum, perimenopause/menopause, ADHD/ASD | Room DB | Used to adjust scoring; never transmitted |
| Habit logs — caffeine, alcohol, exercise, stress | Room DB | |
| Audio events — snoring, coughing, sleep talking | Room DB | Timestamps and classifications |
| Sleep-talk recordings (WAV) | `files/sleep_talk/` | See "Recordings" below |
| Alarms, tags, preferences | Room DB / DataStore | |
| NAS password, backup passphrase | DataStore, AES-256-GCM encrypted with an Android Keystore key | Never stored in plaintext |

## Sensors and permissions

`RECORD_AUDIO`, `BODY_SENSORS` (API 30+) and `POST_NOTIFICATIONS` (Android 13+) are runtime
permissions requested during onboarding. Every one of them can be declined without breaking the
app, and each can be granted or revoked later from Android's app settings.

- **Microphone** (`RECORD_AUDIO`) — audio is analysed in memory to classify snoring, coughing
  and talking. Except for the sleep-talk clips described below, audio is never written to disk
  and never transmitted. Declining it only disables audio-based features; motion tracking is
  unaffected. Note the mic hears the whole room, not just you: if someone shares your bed or
  bedroom, they should know recording is on. Choosing continuous-mic sonar tracking shows a
  one-time reminder of exactly this.
- **Motion sensors** (`BODY_SENSORS`, `HIGH_SAMPLING_RATE_SENSORS`) — movement drives sleep-stage
  classification. `BODY_SENSORS` became a runtime permission on Android 11 (API 30) and is
  requested during onboarding; on Android 14+ it is what lets sleep tracking run as the
  "health" foreground-service type. Without it the health type is dropped — tracking runs under
  whatever types remain (microphone if `RECORD_AUDIO` is granted, otherwise a permission-free
  "special use" type).
- **Notifications** (`POST_NOTIFICATIONS`, Android 13+) — requested at onboarding for bedtime
  reminders, morning briefings and insights. Notifications are produced on-device and never
  leave the phone.
- **Camera** (`CAMERA`) — used only when you scan a QR code, for the alarm dismiss captcha or NAS
  setup. Frames are decoded on-device by zxing-cpp and never stored or sent.
- **Internet** (`INTERNET`) — declared in `core/data` and used by exactly one feature: optional
  NAS backup. Nothing else in the app makes network requests. See below.

## Recordings

When Somn detects sleep talking it saves a short WAV clip so you can listen back in the morning.
These are the most sensitive files the app produces, so:

- They are stored app-privately and are never uploaded anywhere unless you turn on NAS backup.
- They are **deleted automatically after 7 days by default**. Change or disable this under
  *Settings → Sleep-Talk Recordings*.
- *Settings → Delete all recordings now* destroys every clip immediately.
- Deleting a session deletes its clips.
- Deleting a clip removes the audio file, not the event row: the timestamp, type, and loudness
  stay in your history, exactly like a session you kept. Only *Delete everything* removes those too.

## Google Auto Backup: off

Somn opts out of Android's Auto Backup and device-to-device transfer entirely
(`app/src/main/res/xml/data_extraction_rules.xml` and `backup_rules.xml`). Your sleep database
and your recordings are never copied to Google Drive and are not carried over when you set up a
new phone. If you want an off-device copy, use the backup features below — they are yours to
control.

## What can leave the device

Only if you explicitly configure it:

- **Manual export** — CSV or JSON, written to a location you pick. Whatever you do with the file
  afterwards is up to you.
- **NAS backup** — off by default. When you enable it and set a recovery passphrase, Somn uploads
  encrypted backups to the self-hosted server you configure. Files are encrypted with AES-256-GCM
  under a key derived from your passphrase, with a fresh random data key per file. Somn refuses to
  sync at all if no passphrase is set, because an upload nobody can decrypt is not a backup.
  Connections use HTTPS; you can turn that off, and the app will warn you, because your NAS
  credentials would then be sent unencrypted.
- **Health Connect** — off by default. If you enable it, Somn reads vitals from and writes sleep
  sessions to Android's Health Connect, on-device. Somn does not send that data anywhere.

Somn never uploads anything on its own initiative.

## No telemetry, and no third-party SDKs that phone home

Somn contains no analytics, no advertising, and no tracking of any kind. It
does not report usage, errors, or your existence to anyone, including us.

Crash reports are the one exception with an asterisk: when the app crashes, a redacted stack
trace is written to app-private storage and stays there. Nothing uploads it — attaching it to
a bug report is a deliberate *Settings → About → Copy latest crash report* action by you.

The app deliberately avoids Google Play Services. Earlier versions used Google's ML Kit for QR
scanning, which pulled in `play-services-basement` and Google's Cloud Client Telemetry transport
and silently added the `INTERNET` permission. That is gone — replaced by
[zxing-cpp](https://github.com/zxing-cpp/zxing-cpp) (Apache-2.0). LiteRT's
`com.google.android.play:ai-delivery` transitive is likewise excluded
(`core/audio/build.gradle.kts`). The release build has no Google Play Services artifacts on its
classpath.

### Prebuilt binaries

For transparency, the app ships two things it does not build from source, plus one model it
downloads at your request:

- YAMNet audio-event classifier — Google's model ([tfhub.dev/google/yamnet/1](https://tfhub.dev/google/yamnet/1),
  Apache-2.0), downloaded on demand for optional snoring/coughing/talking classification and
  verified by SHA-256 before use. It never sends anything anywhere. Not bundled in the APK.
  SHA-256: `10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de`
- `com.google.ai.edge.litert:litert` — the LiteRT/TensorFlow Lite runtime, Apache-2.0, from Maven
  Central, that executes the model above.
- `io.github.zxing-cpp:android` — zxing-cpp's Android port, Apache-2.0, from Maven Central. Used
  for QR decoding in the alarm-dismiss captcha and NAS setup; it replaced Google's ML Kit when
  that was dropped. It ships as a prebuilt AAR containing a native `.so` — a binary we do not
  build from source. IzzyOnDroid accepts prebuilt binaries as-is with no reproducibility
  requirement, but official F-Droid either builds it from source via NDK in their own
  infrastructure or records it as a known limitation.

### Health Connect

Health Connect integration is optional and off by default. On Android 13 and earlier it relies on
Google's separate Health Connect app, which is proprietary; Somn works normally without it. This
is disclosed as a non-free dependency for F-Droid purposes.

## Deleting your data

- Individual sessions: delete from the history screen; clips go with them.
- All recordings: *Settings → Delete all recordings now*.
- Everything: *Settings → Delete everything* wipes all sessions, habits, tags, recordings, and
  settings, and the app restarts as a fresh install. Uninstalling also removes everything —
  there is no cloud copy and no Auto Backup.

Local-only storage protects against network collection, not physical access: someone holding
your unlocked phone — or a forensic image of it — can read what is on it. Your lock screen
is part of this app's privacy story, not separate from it.

## How these claims are kept honest

Documentation drifts from code, so the load-bearing claims above are enforced by CI rather
than by memory. Every push and pull request fails the build if:

- any Google Play Services artifact reappears on the release classpath
- the Auto Backup or device-transfer opt-out is dropped, for either supported API range
- `INTERNET` starts being contributed by any module other than `core:data`

Unit tests cover the retention window, the keep-forever sentinel, the behaviour when the
preference is corrupted, and the connection-scheme selection that governs whether your NAS
credentials are encrypted in transit.

## Reporting a problem

If you find something in the app that contradicts this document, that is a bug and we want to
know. Open an issue.
