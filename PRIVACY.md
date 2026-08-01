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

- **Microphone** (`RECORD_AUDIO`) — audio is analysed in memory to classify snoring, coughing
  and talking. Except for the sleep-talk clips described below, audio is never written to disk
  and never transmitted.
- **Motion sensors** (`BODY_SENSORS`, `HIGH_SAMPLING_RATE_SENSORS`) — movement drives sleep-stage
  classification.
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

Somn contains no analytics, no crash reporting, no advertising, and no tracking of any kind. It
does not report usage, errors, or your existence to anyone, including us.

The app deliberately avoids Google Play Services. Earlier versions used Google's ML Kit for QR
scanning, which pulled in `play-services-basement` and Google's Cloud Client Telemetry transport
and silently added the `INTERNET` permission. That is gone — replaced by
[zxing-cpp](https://github.com/zxing-cpp/zxing-cpp) (Apache-2.0). LiteRT's
`com.google.android.play:ai-delivery` transitive is likewise excluded
(`core/audio/build.gradle.kts`). The release build has no Google Play Services artifacts on its
classpath.

### Prebuilt binaries

For transparency, the app ships two things it does not build from source:

- `core/audio/src/main/assets/yamnet.tflite` — Google's YAMNet audio-event classifier, Apache-2.0,
  used on-device for optional snoring/coughing/talking classification. It never sends anything
  anywhere.
  SHA-256: `10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de`
- `com.google.ai.edge.litert:litert` — the LiteRT/TensorFlow Lite runtime, Apache-2.0, from Maven
  Central, that executes the model above.

### Health Connect

Health Connect integration is optional and off by default. On Android 13 and earlier it relies on
Google's separate Health Connect app, which is proprietary; Somn works normally without it. This
is disclosed as a non-free dependency for F-Droid purposes.

## Deleting your data

- Individual sessions: delete from the history screen; clips go with them.
- All recordings: *Settings → Delete all recordings now*.
- Everything: uninstall Somn. Nothing survives it — there is no cloud copy and no Auto Backup.

## Reporting a problem

If you find something in the app that contradicts this document, that is a bug and we want to
know. Open an issue.
