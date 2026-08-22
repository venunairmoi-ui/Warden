# Warden — Sprint Roadmap

Supersedes the M0–M3 table in the original spec (`warranty-tracker-spec.md`,
section 8) — this is the granular version, expanded to make native PDF
support a first-class part of the plan rather than an add-on. Each sprint is
sized for a weekend or two of solo nights-and-weekends work, and is meant to
leave you with something you can actually run on your phone at the end of it.

Status key: ✅ done · 🚧 next · ⬜ not started

---

## Sprint 0 — Project setup ✅

**Scope:** Gradle/Kotlin/Compose project scaffold, theme, navigation
skeleton, Room database wired up.

**Delivered:** the project in this folder. Builds and launches to an empty
"No items yet" home screen.

## Sprint 1 — Data layer + manual add/edit/list ✅

**Scope:** Room entities (`Item`, `Attachment`, `ReminderRule`,
`ServiceEvent`), DAOs, repository, manual Add/Edit form (text entry only),
Home list sorted by nearest expiry, Item detail screen.

**Delivered:** you can add an item by typing, see it on the home list, open
it, edit it, and the data survives app restarts (local Room DB). This alone
is dogfoodable — the "one place for all of it" problem is already solved
here, just with all-manual entry.

**Acceptance check:** add 3-4 of your real warranties by hand right now.
Does the list, sort order, and detail view feel right before more is built
on top of it?

---

## Sprint 2 — Capture: camera, gallery, and native PDF ✅

**Scope:** this is where PDF support becomes real in the UI (the rendering
engine, `pdf/PdfPageRenderer.kt`, already implemented in Sprint 0 — this
sprint wires it in).

**Delivered:**
- `capture/CameraCaptureScreen.kt` — full-screen CameraX (Preview +
  ImageCapture) capture UI, reached via a `camera_capture` nav route from
  `AddEditItemScreen`. Runtime `CAMERA` permission requested on first tap,
  not at app launch.
- Android Photo Picker (`ActivityResultContracts.PickVisualMedia`, images
  only) and the native PDF document picker
  (`ActivityResultContracts.OpenDocument`, `application/pdf`) — both
  permission-free system pickers, no third-party library.
- `capture/AttachmentStorage.kt` — one shared copy routine all three
  sources funnel through: copies the source into
  `filesDir/attachments/`, returns a FileProvider `content://` Uri.
  Standardized on `content://` everywhere (never a raw `file://` Uri) so
  every read path — `PdfPageRenderer`, the thumbnail decoder — goes
  through `ContentResolver`, which is unambiguously documented to support
  our own FileProvider URIs; removes any doubt a mixed-scheme approach
  would leave.
- `Attachment.thumbnailUri` (new field, DB version 3 → 4): a cached
  `PdfPageRenderer.cacheThumbnail(...)` JPEG for PDF attachments, so a PDF
  *looks* like any other attachment in a list rather than needing
  special-cased re-rendering on every appearance. Image attachments use
  the image itself — no separate thumbnail needed.
- `ui/common/LocalBitmap.kt` — downsampled, off-main-thread bitmap
  decoding for thumbnails (no Coil/Glide dependency; sized for personal
  item counts, not a general-purpose image pipeline).
- `ui/attachment/AttachmentViews.kt` — shared `AttachmentThumbnailRow` /
  `AttachmentViewerDialog`, used by both `AddEditItemScreen` (with delete)
  and `ItemDetailScreen` (view-only).
- New-item capture flow (revised post-launch, see below): attaching a
  photo/PDF before Save is held as a local, in-memory `PendingAttachment`
  — nothing is written to the database until the user actually presses
  Save, at which point the item and every pending attachment are created
  together in one pass (`AddEditItemViewModel.save` takes an explicit `id`
  per call, and its `onSaved` callback hands back the real persisted id so
  pending attachments can be flushed against it).

  *(Original Sprint 2 delivery instead auto-saved a draft item the instant
  the first attachment was added, before Save. Real-device testing showed
  this created a silent, unreviewed database record on attach — fixed to
  the behavior described above shortly after Sprint 3 shipped.)*

**Acceptance check:** from the Add screen, attach a photo via camera, a
photo via gallery, and a PDF (e.g. an emailed AMC contract saved to Files) —
all three should show a thumbnail and be viewable from the item detail screen.

## Sprint 3 — Share-intent capture (images + PDF) ✅

**Scope:** the "I saw a receipt in WhatsApp, tapped Share, done" flow from
the spec.

**Delivered:**
- `capture/ShareReceiverActivity.kt` — translucent, no-UI activity
  registered for `ACTION_SEND` (`image/*` and `application/pdf`). Its only
  job is to grab the shared content while the read grant is still valid
  (a WhatsApp/Gmail share Uri isn't guaranteed to outlive the request) and
  copy it into app storage via the same `AttachmentStorage.copyToAppStorage`
  every other capture source uses — no Room/repository work happens here,
  deliberately, so attachment-saving logic still lives in exactly one place
  (`AddEditItemScreen.handleNewAttachment`).
- Hand-off from there into the app follows the exact same pattern Sprint 5
  already established for notification-tap deep links: `MainActivity` picks
  up `EXTRA_SHARE_URI` / `_MIME_TYPE` / `_DISPLAY_NAME`, exposes them as a
  nonced `PendingShare` state, and `WardenNavHost` navigates to a fresh
  AddItem screen and posts that data onto the new destination's own
  `SavedStateHandle` (the mirror image of how `CameraCaptureScreen` posts
  its result *back* to the caller).
- The one real behavior difference from every other attach path: a shared
  file can arrive before Name or Expiry are set, so `AddEditItemScreen`
  pre-fills Name from the shared file's own filename (via
  `ContentResolver` `DISPLAY_NAME`, falling back to "Shared item") and
  Expiry to a clearly-placeholder `+1 year` (consistent with the "+1 year"
  convention this app already uses for renewals, and far enough out it
  can't trigger a false reminder before you get back to correct it) —
  rather than blocking the share on typing those first.

**Acceptance check:** from WhatsApp or Gmail, share an image AND a PDF to
Warden. Both should land you on a pre-populated Add screen (even before
Sprint 4's OCR, at minimum the file is attached and ready) — review/correct
the pre-filled Name and Expiry, then Save as normal.

## Sprint 4 — OCR pre-fill (unified for images and PDFs) ✅

**Scope:** ML Kit Text Recognition v2, on-device, wired into the confirm
screen.

**Delivered:**
- `ocr/AttachmentOcr.kt` — a suspend wrapper around ML Kit's
  `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`. This
  project's `text-recognition:16.0.1` artifact is the BUNDLED variant (model
  ships inside the APK, no first-run download over the network) — confirmed
  against ML Kit's own API reference rather than assumed, same discipline
  the ShareReceiverActivity nested-comment near-miss established earlier in
  this project.
- For an image attachment: OCR runs directly on the downsampled bitmap
  (`ui/common/LocalBitmap.kt`'s new `decodeBitmapForOcr`, sized for text
  legibility rather than list-thumbnail size). For a PDF attachment: page 1
  renders via `PdfPageRenderer.renderPage(...)` and that SAME bitmap feeds
  both OCR and the cached-thumbnail JPEG (`PdfPageRenderer.saveAsJpeg`,
  split out of `cacheThumbnail` so the page only renders once) — no separate
  code path for PDFs at this layer.
- `ocr/ReceiptFieldParser.kt` — a regex-based parser proposing Vendor
  (matched against a short known-brand list), Purchase date (day-first
  formats), and Cost (₹/Rs./INR-prefixed amounts, preferring a line labeled
  "total"). Deliberately does NOT propose Name — no reliable text-pattern
  signal exists for "what is this item called" on a generic receipt, and an
  early version's low-confidence "first text line" fallback for both Name
  and Vendor produced too many confidently-wrong guesses (GSTINs, invoice
  headers) against realistic Indian invoice layouts to keep. Precision over
  recall throughout this file, on purpose.
- No separate confirm screen. `AddEditItemScreen.handleNewAttachment` (the
  one function every capture path already funnels through) runs OCR + the
  parser on every attach and pre-fills Vendor/Purchase date/Cost only if
  they're still blank — the same screen already IS the review-before-commit
  step, per the pending-attachment redesign shipped alongside Sprint 3's
  feedback. A small spinner next to "Attachments" covers the now-longer
  attach time; a Toast flags when something got auto-filled so it's not
  silently confusing where a value came from.

**Deviation from the original scope note above:** no dedicated confirm UI
was built — see the "no separate confirm screen" bullet for why that's a
deliberate call, not a shortfall. The "wrong silent guess is worse than a
5-second confirm" principle the original note called out is preserved
exactly, just enforced by the existing Add/Edit form rather than a second
screen.

**Acceptance check:** attach a real receipt (photo) and a real PDF AMC
contract. For each, check what fraction of fields came pre-filled correctly
vs. needed manual correction — this number is also one of the go/no-go
signals in spec section 9, and the first real signal for whether
`ocr/ReceiptFieldParser.kt`'s known-vendor list and date/amount patterns
need extending.

## Sprint 5 — Reminders + notification actions ✅

**Scope:** turn the `ReminderRule` rows that already get created on every
saved item (Sprint 1) into actual notifications.

**Delivered:**
- `reminders/ReminderCheckWorker` — daily `WorkManager` periodic job
  (`ReminderScheduler`, registered from `WardenApplication.onCreate()`).
  Fires a rule the first time `daysLeft <= daysBeforeExpiry`, gated by a new
  `lastFiredDate` on `ReminderRule` so it fires once, not every day the item
  stays inside the window — "at or past the threshold", not "exactly on the
  threshold day", so a missed run (phone off, Doze) can't silently skip it.
- `NotificationChannel` created on every app start (idempotent);
  `POST_NOTIFICATIONS` requested once at first launch (`MainActivity`,
  Android 13+ only).
- Notification tap → deep-links into `ItemDetailScreen` (`MainActivity` is
  now `launchMode="singleTop"` with an `onNewIntent` handler so this works
  whether the app is cold-launched or already open).
- Two notification actions, no app-open required: **Mark serviced/renewed**
  (pushes expiry forward 1 year from its current date as a default — correct
  for the common AMC/subscription annual-renewal case, wrong for a one-off
  repair, correctable via Edit afterward) and **Snooze 7 days** (re-arms
  just that one rule via a new `snoozedUntil` field, doesn't touch the item's
  actual expiry). Both run through a `ReminderActionReceiver` ->
  `ReminderActionWorker` hop rather than doing DB work directly in the
  receiver, since a `BroadcastReceiver.onReceive()` only has a short
  guaranteed lifetime.
- Detail screen also has its own **Mark serviced / renewed** button (with a
  confirm dialog showing the computed date before applying) for when you
  open an item directly rather than via a notification. Snooze is
  deliberately NOT duplicated there — it only makes sense tied to one
  specific already-fired reminder, which context doesn't exist from the
  Detail screen.

**Acceptance check:** set a test item's expiry to tomorrow, force the
worker to run (Android Studio lets you trigger WorkManager jobs manually for
testing), confirm the notification fires and both actions work without
opening the app.

## Sprint 6 — Auto-detect (opt-in) + digest notification ⬜

**Scope:** the two retention-design features from the spec's research
(section on low-frequency-use apps).

- Settings screen with an Auto-detect toggle. Turning it on triggers the
  `READ_MEDIA_IMAGES` runtime permission request (already declared in the
  manifest) — **only here, never at first launch** — with a one-screen
  explanation of why, to satisfy Play Store's restricted-permission
  disclosure requirement before this ever ships publicly.
- A periodic `WorkManager` job scans `MediaStore` for new images since the
  last check, runs a cheap heuristic on OCR'd text (currency symbols,
  words like "warranty"/"invoice"/"AMC") to decide whether to surface a
  low-priority "Add this to Warden?" notification. Never auto-saves.
- A separate weekly/monthly digest `WorkManager` job: "3 items expiring
  this month, ₹X in active coverage" — the retention hook from the spec.

**Acceptance check:** take a screenshot of a receipt, confirm a prompt
appears within a few minutes without you opening the app. Confirm the
digest notification fires on schedule.

## Sprint 7 — Polish + Play Store prep ⬜

**Only start this once the spec section 9 validation criteria say "go."**

- Home-screen widget (next 1-2 upcoming expiries).
- Google Drive backup/restore (app-scoped folder — no server).
- Real app icon (replace the placeholder vector in `res/drawable/ic_launcher_*`).
- Settings polish, onboarding, Play Store listing assets, privacy policy
  page (required once `READ_MEDIA_IMAGES` is a live, requestable permission).
- Rename the `com.venunair.warden` placeholder package/applicationId if desired.

---

## Why PDFs aren't a separate track

Every sprint above treats `application/pdf` as another `Attachment.mimeType`
value flowing through the same pipeline as `IMAGE`, not a parallel feature
set: same picker pattern (Sprint 2), same share intent-filter (Sprint 3),
same OCR call via `PdfPageRenderer` bridging PDF → Bitmap (Sprint 4). That's
a deliberate design choice from `pdf/PdfPageRenderer.kt`'s doc comment — it
keeps the amount of PDF-specific code to one small, self-contained file
instead of spreading "if PDF then..." branches through five sprints.
