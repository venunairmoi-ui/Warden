# Warden — Warranty & Renewal Tracker

Personal-use-first Android app: one place for warranties, AMCs, subscriptions,
and documents, with reminders that don't rely on you remembering to open the
app. Built against the spec delivered earlier in this conversation
(`warranty-tracker-spec.md`) — this README covers the code; see
[`SPRINTS.md`](SPRINTS.md) for the build roadmap and current status.

## What's implemented right now

**Sprint 0 + Sprint 1 are done:** the app builds, runs, and gives you a real
(if bare) end-to-end loop — add an item manually, see it on the home list
sorted by nearest expiry, open/edit/delete it. Everything else (capture via
camera/gallery/PDF/share, OCR pre-fill, reminders, auto-detect) is scaffolded
with clear TODOs pointing at the sprint that implements it — see
`SPRINTS.md`.

Also already implemented, ahead of schedule: **`pdf/PdfPageRenderer.kt`** —
native PDF page rendering using Android's built-in `PdfRenderer` (no
third-party PDF library, no added cost). It's not wired into the UI yet
(that's Sprint 2/4), but the core capability you asked for — native PDF
support — is real, working code, not a stub.

## Opening the project

1. Android Studio (current stable — Ladybug/Meerkat-era or newer). Open this
   folder directly; Android Studio will generate the Gradle wrapper files on
   first sync (they're intentionally not checked in — see note below).
2. JDK 17 (Android Studio bundles this; no separate install usually needed).
3. First sync will pull `compileSdk 36` / Android 16 platform tools if you
   don't already have them — accept the SDK Manager prompt.
4. Run on an emulator or device at API 26+.

**Why there's no `gradle-wrapper.jar` in this delivery:** that file is a
binary and isn't something to hand-author. Opening this folder in Android
Studio will offer to generate/sync the wrapper automatically on first import.
If you're setting this up from a plain terminal instead of Android Studio,
run `gradle wrapper --gradle-version 8.9` once you have any Gradle
installed, and it'll create the missing wrapper files.

## Project structure

```
app/src/main/java/com/venunair/warden/
  data/            Room entities, DAOs, database, repository (Sprint 1)
  ui/
    theme/         Compose theme (Sprint 0)
    navigation/     NavHost + routes (Sprint 0)
    home/          Home list screen (Sprint 1)
    additem/       Add/Edit form (Sprint 1; capture buttons added Sprint 2)
    itemdetail/     Item detail screen (Sprint 1; attachments added Sprint 2)
  capture/         Capture-source vocabulary now; camera/picker/share Sprint 2-3
  pdf/             PdfPageRenderer — native PDF rendering (implemented now)
  ocr/             OCR pipeline — Sprint 4
  reminders/       WorkManager/AlarmManager scheduling — Sprint 5
```

`com.venunair.warden` is a placeholder package/application ID — change it
(Android Studio's Refactor > Rename Package handles this safely) before any
Play Store submission, since the applicationId must be unique and permanent
once published.

## Design decisions worth knowing before you extend this

- **No Hilt/Dagger.** Dependency injection is a single manual container in
  `WardenApplication.kt`. Deliberate, not an oversight — a DI framework isn't
  worth the setup/learning tax at this project's size. Revisit only if the
  object graph actually gets unwieldy.
- **No third-party PDF library.** `PdfPageRenderer` uses
  `android.graphics.pdf.PdfRenderer`, part of the Android SDK since API 21.
  PDFs render to a Bitmap and flow through the *same* ML Kit OCR pipeline as
  photos (Sprint 4) rather than a separate PDF-text-extraction path — one
  pipeline for both MIME types, not two.
- **`READ_MEDIA_IMAGES` is declared in the manifest but not requested at
  runtime yet.** Per the spec, it's only requested when the user explicitly
  turns on Auto-detect in Settings (Sprint 6) — never at first launch.
