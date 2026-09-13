# Warden — Sprint 6–10 Plan

Replaces the placeholder Sprint 6–7 in `SPRINTS.md`. Integrates the
feasibility-filtered feature expansion from the Concept document with the
UI polish the app needs before Play Store submission.

**Constraints:** on-device only, no cloud APIs, no paid dependencies,
one-time purchase model. Every feature below is buildable with what's
already in the project or free platform APIs.

**Excluded (and why):**

| Concept section | Feature | Reason excluded |
|---|---|---|
| 10 | "Do I still need this?" usage tracking | Android doesn't expose other apps' usage data |
| 14 | Warranty eligibility checker | Needs cloud LLM to understand T&C documents |
| 23 | Natural-language AI assistant | Needs cloud LLM; good filter/search UI covers the practical cases |
| 15 | Family sharing | Needs auth, cloud DB, sync protocol — architectural pivot |
| 17–19 | Marketplace, renewal comparison, extension recs | Needs vendor relationships, pricing data, payment infra |
| 21 | Product replacement intelligence | Needs real-time market pricing data |

---

## Sprint 6 — Visual identity + data model expansion

The app needs to stop looking like a scaffold before features are layered
on top. This sprint does both the visual foundation and the data model
widening because both are prerequisites for everything that follows.

### Visual polish

- **Typography (`ui/theme/Type.kt`)**: bundle a free sans-serif (Inter,
  DM Sans, or Plus Jakarta Sans) for display/headline/title roles. Roboto
  stays for body and label. This is the single highest-impact change — it
  immediately distinguishes the app from every default-Roboto project.

- **TopAppBar styling**: apply `TopAppBarDefaults.topAppBarColors()` with
  brand primary across all three screens. Title text uses the new headline
  font.

- **Dev buttons gated**: wrap the Biotech (sample data) and
  NotificationsActive (reminder check) `IconButton`s in
  `if (BuildConfig.DEBUG)` blocks. Zero user-visible change in release
  builds.

- **Navigation transitions**: replace `NavHost` with `AnimatedNavHost`
  (or the navigation-compose 2.8+ `enterTransition`/`exitTransition`
  lambdas). Forward navigation slides in from the right; back slides out
  to the right. Detail → Edit crossfades. Currently every screen change
  is an instant swap.

- **Empty state**: replace the bare "No items yet" text with a composed
  illustration — large muted shield icon (the app's identity) with the
  text beneath it. Small effort, large perceived-quality difference.

- **Category labels**: human-readable display names ("Warranty",
  "Service Contract", "Subscription", "Document") instead of raw enum
  `.name` values ("WARRANTY", "AMC"). A `displayName` extension property
  on `ItemCategory`.

- **String externalization**: move all user-facing strings into
  `strings.xml`. Not for localization yet — for consistency and to stop
  scattering literals across composables.

### Data model expansion

- **`Item` entity new fields** (Room migration v4 → v5):
  `serialNumber: String?`, `modelNumber: String?`, `retailer: String?`,
  `invoiceNumber: String?`, `itemType: ItemType` (enum: `PRODUCT`,
  `SUBSCRIPTION`, `SERVICE_CONTRACT`), `location: String?`,
  `billingCycle: BillingCycle?` (enum: `MONTHLY`, `QUARTERLY`,
  `HALF_YEARLY`, `ANNUAL`, `ONE_TIME`), `billingAmount: Double?`,
  `autoRenew: Boolean`.

- **`ServiceEvent` new field**: `cost: Double?` — needed for TCO
  calculation in Sprint 7. Currently only `date` and `note` are tracked.

- **`ItemCategory` expansion**: add `VEHICLE`, `HOME`, `OFFICE`,
  `FINANCIAL` to the existing `WARRANTY`, `AMC`, `SUBSCRIPTION`,
  `DOCUMENT`. Each maps to a distinct Material icon in `CategoryIcons.kt`.

- **Real Room `Migration(4, 5)`**: no more `fallbackToDestructiveMigration()`.
  The app is approaching real-user territory; wiping the DB on upgrade is
  no longer acceptable. Enable `exportSchema = true` and wire the Room
  Gradle plugin for JSON schema export.

- **Add/Edit form redesign**: conditional field sections based on
  `itemType`. "Product" shows serial/model/retailer. "Subscription" shows
  billing cycle/amount/auto-renew. "Service Contract" shows
  visits-included/used/coverage. All three share the core fields (name,
  vendor, dates, cost, attachments, notes). Section headers visually
  break the current single-column wall of `OutlinedTextField`s.

- **OCR parser update**: attempt `serialNumber`/`modelNumber` extraction
  from invoice text. Pattern-based, same precision-over-recall rule —
  only when a label like "S/N:", "Serial No", "Model:" precedes a
  plausible value.

**Acceptance:** add one product (with serial/model), one subscription
(with billing cycle/amount), one AMC-type service contract. All three
render correctly with the new typography, styled TopAppBar, and
transition animations. Dev buttons invisible in a release build. Empty
state shows the shield illustration.

---

## Sprint 7 — Dashboard + insights

### Home screen redesign

Replace the flat expiry-sorted `LazyColumn` with urgency-grouped
sections, each with a sticky header:

- 🔴 **Expiring soon** — items with ≤30 days remaining (warranties,
  AMCs, insurance)
- 🟠 **Renewal approaching** — subscriptions with auto-renew and ≤30
  days to renewal
- 🟢 **Active** — everything else, sub-grouped by category

### Summary cards (top of home screen)

- **"Money at risk this month: ₹X"** — sum of `cost` for items expiring
  within 30 days. The emotional hook from Concept section 2.
- **"Monthly subscriptions: ₹X"** — sum of all subscription-type items'
  `billingAmount`, normalized to monthly. For annual subscriptions,
  divide by 12. Annualized total shown on tap/expand.

Both cards use the brand `secondaryContainer` surface with gold accent
to stand apart from the item list below.

### Filters

- **Category filter chips**: horizontal scrollable row of `FilterChip`s
  below the summary cards. Tap to toggle; multiple categories selectable.
- **Location filter**: dropdown or chip when 2+ distinct locations exist
  across items.

### Item detail enhancements

- **Total cost of ownership**: new card section on `ItemDetailScreen`.
  Purchase price + Σ `ServiceEvent.cost` + AMC costs. Displays total and
  per-year average (if purchase date is known). Pure arithmetic on
  existing data.
- **Service history timeline**: `ServiceEvent` records rendered as a
  vertical timeline with date, note, and cost. Newest first. Uses the
  `ServiceEventDao` query that already exists.

### Micro-interactions

- **List item entrance animation**: `AnimatedVisibility` with
  `fadeIn + slideInVertically` on each `ItemRow` as it enters the
  viewport. Staggered by index for initial load.
- **Loading shimmer**: replace the "Loading…" text on
  `ItemDetailScreen` with a shimmer placeholder matching the card
  layout. `Modifier.shimmer()` from a lightweight inline implementation
  (no library needed — a `Brush.linearGradient` animated across the
  composable).

**Acceptance:** home screen shows grouped items with summary cards.
Filter by category, then by location. Detail screen shows TCO and
service timeline for an item with 2+ service events and costs. List
items animate on initial load.

---

## Sprint 8 — Search + smart notifications

### Document search

- Search bar at the top of the home screen (or a dedicated search
  route). Queries across `Item.name`, `Item.vendor`,
  `Attachment.rawOcrText`.
- At personal-item scale (tens to low hundreds of items), `LIKE
  '%query%'` on Room is fast enough — no full-text-search index needed.
- Results show the matching item; if the match is in OCR text, show the
  attachment thumbnail and the matching text snippet.

### Smart notifications

- **Configurable reminder intervals**: extend `ReminderRule` creation on
  save. Currently hardcoded to `DEFAULT_REMINDER_OFFSETS = listOf(30, 7,
  1)`. Add a user-selectable set (checkboxes: 90 / 60 / 30 / 14 / 7 / 1
  days) per item on the Add/Edit form, with defaults from Settings.
- **Intelligent notification text**: when `ReminderCheckWorker` fires a
  notification, check whether the item has `ServiceEvent` costs. If
  total repairs exceed 20% of purchase price, append: "You've spent ₹X
  on repairs — consider extending coverage before expiry." Conditional
  logic on existing data, no AI.
- **Digest notification**: a weekly (default) or monthly `WorkManager`
  periodic job. Content: "X items expiring this month • ₹Y in active
  coverage • Z subscriptions renewing." The retention hook — gives users
  a reason to come back even when nothing is urgent.

### Claim info view

- On `ItemDetailScreen`, a "Warranty claim info" button. Opens a
  bottom sheet or card assembling: product name, serial number, model,
  purchase date, warranty status, retailer, all attachments (invoice,
  warranty card). "Copy summary" button renders a plain-text block to
  clipboard. "Share" sends it via `ACTION_SEND` (text/plain). This is
  Concept section 13 scoped to what's actually buildable — information
  gathering, not process automation.

### Additional polish

- **Snackbar migration**: replace `Toast` calls with `Snackbar` via
  `SnackbarHostState` on each screen's `Scaffold`. Snackbars are
  theme-aware, support actions ("Undo"), and are the Material3
  recommended pattern.
- **Swipe-to-archive**: `SwipeToDismissBox` on home list items. Swipe
  right → archive (sets `ItemStatus.ARCHIVED`). Snackbar with "Undo"
  action for 5 seconds.
- **Unsaved changes guard**: on Add/Edit screen, intercept back
  navigation with `BackHandler` when the form has unsaved changes.
  Show a confirm dialog.

**Acceptance:** search "LG" and see all LG items with OCR matches
highlighted. A reminder for an item with significant repair costs shows
the cost context in the notification text. Digest notification fires on
schedule. Claim info summary copies to clipboard correctly. Swipe an
item to archive, undo within 5 seconds.

---

## Sprint 9 — Auto-detect + settings + onboarding

### Settings screen

New `SettingsScreen` route, accessible from a gear icon on the home
TopAppBar:

- **Reminder defaults**: which intervals are pre-selected for new items
  (checkboxes: 90 / 60 / 30 / 14 / 7 / 1)
- **Digest frequency**: weekly / monthly / off
- **Auto-detect toggle**: on/off (default off), with explanation text
- **Theme**: system / light / dark
- **Backup**: manual trigger (Sprint 10), last-backup date display
- **About**: version, privacy policy link, contact

Stored in `DataStore<Preferences>` (Jetpack DataStore), not
SharedPreferences.

### Auto-detect

Already scoped in SPRINTS.md Sprint 6 — moved here because the Settings
screen is its prerequisite:

- `WorkManager` periodic job scans `MediaStore` for new images since
  last check timestamp.
- Runs OCR via the existing ML Kit pipeline. Applies a keyword
  heuristic: if the text contains ₹/Rs/INR + any of
  (warranty/invoice/AMC/receipt/total/serial), fire a low-priority
  notification: "Looks like a receipt. Add to Warden?"
- Tap opens Add screen with the image pre-attached and OCR pre-filled.
  Never auto-saves.
- `READ_MEDIA_IMAGES` permission requested only when the toggle is
  turned on, with a one-screen explanation of why — satisfies Play
  Store's restricted-permission disclosure requirement.

### Onboarding

3-screen first-launch flow (shown once, completion stored in
DataStore):

1. **"One place for everything you own."** — shield icon + warranty
   card illustration
2. **"Snap it, we'll read it."** — camera/OCR illustration
3. **"Never miss an expiry."** — notification bell illustration

Each screen: headline in the new display font, one line of body text,
bottom-aligned "Next" / "Get started" button. Skip link on every screen.

### Splash screen

`SplashScreen` API (Android 12+, backported via
`core-splashscreen`): shield icon on teal background, 200ms minimum
hold. Replaces the current bare `Theme.Warden` XML background.

**Acceptance:** Settings screen opens, all toggles persist across app
restart. Auto-detect: take a photo of a receipt, confirm notification
appears within the next scan cycle without opening the app. Onboarding
appears on first launch only. Splash screen shows the brand icon.

---

## Sprint 10 — Play Store release

### Google Drive backup/restore

- App-scoped Google Drive folder (no server, no separate sign-in
  beyond the Google account already on the device).
- **Backup**: export Room DB file + entire `attachments/` directory
  into a single zip, upload to Drive. One-button trigger from Settings.
- **Restore**: download zip from Drive, unzip, replace local DB and
  attachments directory. One-button trigger from Settings with a
  confirm dialog ("this replaces all current data").
- Last-backup timestamp displayed in Settings.
- Dependency: `com.google.android.gms:play-services-auth` +
  `com.google.api-client:google-api-client-android` +
  `com.google.apis:google-api-services-drive` (free, no API key cost
  for app-scoped access).

### App icon

Replace the placeholder adaptive icon. Foreground: shield glyph
(refined from the current placeholder). Background: teal `#2E5E4E`.
Round, squircle, and legacy variants. This is real brand artwork, not a
vector-asset placeholder.

### Play Store listing

- **Screenshots**: 5–8 device-frame screenshots covering: home
  dashboard with urgency groups, OCR scan flow, item detail with TCO,
  notification with intelligent text, settings, search.
- **Feature graphic**: 1024×500 banner with shield icon, tagline
  ("Track warranties. Scan receipts. Never miss an expiry."), brand
  palette.
- **Description**: lead with the Indian-market differentiator — AMC
  tracking, ₹ support, works with Reliance Digital/Croma invoices. No
  cloud dependency, no subscription, your data stays on your phone.
- **Category**: Tools or Productivity.
- **Content rating**: IARC questionnaire (no sensitive content → likely
  "Everyone").

### Privacy policy

Static page hosted on GitHub Pages (free). Covers:

- All data stays on-device unless you choose to back up to your own
  Google Drive
- No analytics, no tracking, no ads
- Camera permission: used only to photograph receipts, images never
  leave the device
- READ_MEDIA_IMAGES (if auto-detect is enabled): scans for receipt-like
  images on-device only, never uploaded
- No third-party data sharing

### Final hardening

- **Room schema export**: flip `exportSchema = true`, verify all
  migrations from v4 → final are correct and tested.
- **Dark theme QA pass**: verify every screen in dark mode. The
  `DarkColors` scheme exists but may not have been tested end-to-end
  with the new UI from Sprints 6–9.
- **Edge-to-edge inset audit**: verify no content is hidden behind
  system bars on devices with gesture navigation, notch/punch-hole
  cameras, and different display ratios.
- **ProGuard/R8**: enable `isMinifyEnabled = true` in the release
  build type. Verify the app works after minification (ML Kit and Room
  need keep rules). This shrinks the APK meaningfully.
- **Version**: bump to `1.0.0`, `versionCode` to a clean number.

**Acceptance:** install from Play Store on a clean device. Complete
onboarding, add an item via OCR, receive a reminder, backup to Drive,
restore on a second device. Dark mode looks correct. APK size is
reasonable (<30 MB target, ML Kit bundled model is the main contributor).

---

## Sprint 11 — WhatsApp PDF auto-detect (shelved)

**Status: scoped 2026-08-25, decided against the same day.** Decision:
stick with manual Share-to-Warden for WhatsApp PDFs (already works,
Sprint 3) rather than building the SAF folder-grant pipeline below —
the one-time folder-picker friction and added permission/disclosure
surface weren't judged worth it over an alternative that already ships
today. Not removed from the plan — kept as a record in case that
judgment changes (e.g., if manual-share turns out to have poor real-
world adoption once more people are using auto-detect). Left out of
the Summary table below since it's not scheduled to be built.

Scoped 2026-08-25, prompted by the auto-detect false-positive fixes in
Sprint 9: most invoices in India arrive as a WhatsApp *document* (PDF),
not a photo. Sprint 9's auto-detect only scans `MediaStore.Images`, so
it never sees these regardless of any WhatsApp setting — a real,
separate gap from anything auto-detect currently handles. Deliberately
sequenced AFTER Sprint 10, not folded into it: this is new feature
surface (a new permission flow, a new disclosure requirement, a second
background OCR pipeline), and Sprint 10 is a tight, already-scoped
release-hardening sprint — bolting new-feature risk onto it risks
delaying the actual Play Store ship for something not required for a
v1.0 release. Manual sharing of a WhatsApp PDF to Warden already works
today (Sprint 3's ShareReceiverActivity, unchanged) — this sprint is
specifically about closing the *silent, no-tap-required* gap that
photo auto-detect already has and PDFs don't.

### Why not MANAGE_EXTERNAL_STORAGE

Checked directly against current Android docs and Play Console policy
before scoping this (not assumed):

- Android's granular media permissions (`READ_MEDIA_IMAGES` and
  friends) never cover non-media files — Google's own shared-storage
  guidance says to use the Storage Access Framework for PDFs/documents,
  full stop.
- Play Console's "All files access" policy explicitly disqualifies
  "file selection" use cases where SAF is sufficient — scanning one
  folder for PDFs is exactly that. Even if it were approved, asking a
  warranty tracker for "access, modify, and delete ALL files" is a
  disproportionate trust ask this app has consistently avoided
  (READ_MEDIA_IMAGES itself is only requested when the user explicitly
  opts in, per spec section 7).

### Recommended approach: Storage Access Framework folder grant

- Settings: a new "Also scan WhatsApp documents" sub-toggle under
  Auto-detect (off by default, disabled until a folder is granted) with
  a "Choose folder" button launching `ACTION_OPEN_DOCUMENT_TREE`. The
  user navigates to WhatsApp's own Documents folder once; Android does
  not let an app pre-navigate the system picker to a guaranteed path
  (it varies by install history and by regular WhatsApp vs. WhatsApp
  Business — `com.whatsapp` vs `com.whatsapp.w4b`, each its own grant),
  so onboarding copy needs to say roughly where to look.
- On grant: `takePersistableUriPermission` on the returned tree Uri,
  persisted in DataStore (mirrors `autoDetectSuggestedFingerprints`'
  storage pattern).
- New periodic step enumerating PDFs under the granted tree via
  `DocumentFile`, filtered by `lastModified` since a stored watermark —
  the same incremental-scan shape `AutoDetectWorker` already uses for
  MediaStore. Renders each PDF's first page with the EXISTING
  `pdf/PdfPageRenderer.kt` (already built and field-tested via the
  manual PDF-share path since Sprint 3 — this is genuine reuse, not new
  OCR work), then runs the same OCR + keyword/currency heuristic + date
  -collision fix + content-fingerprint dedup Sprint 9 already built.
  Matches feed the SAME persisted suggestions queue
  (`AutoDetectSuggestionsScreen`) rather than a separate UI — that
  screen's data layer is already format-agnostic; it needs a
  PDF-first-page thumbnail source alongside the existing image-uri one.
- Defensive re-check every run: a persisted grant can be silently
  revoked (OEM storage cleanup, WhatsApp reinstalled into a fresh
  folder instance, user revokes it from system Settings). On failure,
  surface "reconnect your WhatsApp folder" in Settings rather than
  quietly going dark — same posture `AutoDetectWorker` already takes
  toward `READ_MEDIA_IMAGES` being revoked after the fact.
- Privacy policy + in-app disclosure copy (both the Settings
  explanation text and the Sprint 10 GitHub Pages privacy policy) need
  a new paragraph for this — same spirit as the existing
  `READ_MEDIA_IMAGES` disclosure, not a new class of concern.

### Alternatives considered and rejected

- **`MANAGE_EXTERNAL_STORAGE`** — rejected per the Play policy check
  above.
- **Manual share only, no new work** — real option: zero new
  permission surface, zero new risk, and it already works today. Loses
  the "automatic, no-tap" behavior that's the actual ask here (photo
  auto-detect already has this; PDFs currently don't). Worth keeping in
  mind as the fallback if SAF's one-time folder-picker friction turns
  out to be a bigger adoption blocker in practice than expected.
- **WhatsApp Business Cloud API webhook** — irrelevant to this app's
  use case: only applies when the *sender* runs a WhatsApp Business API
  integration with a webhook the receiving user controls, not to
  invoices arriving in an ordinary personal or business chat.

### Risks / unknowns (not yet resolved — flagging rather than guessing)

- Not tested on a real device — no Android runtime is available in the
  environment this was scoped in. Needs on-device validation of the
  SAF grant + `DocumentFile` enumeration flow before this is
  buildable-with-confidence, same caveat that applied to every Sprint 9
  auto-detect fix before real-device testing caught real bugs.
- Persisted URI grants are capped at 128 per app — irrelevant at 1-2
  folders, noted only for completeness.
- Sizing: comparable to Sprint 9's original Auto-detect build, not a
  small add-on — new permission flow, new disclosure text, new
  background pipeline, new Settings state, even though the OCR core is
  reused.

**Decision (2026-08-25):** stick with manual Share-to-Warden for
WhatsApp PDFs. Not building the SAF pipeline above for now.

---

## Summary

| Sprint | Core theme | Key deliverables |
|---|---|---|
| 6 | Foundation | Custom typography, nav transitions, data model expansion, real Room migration, form redesign |
| 7 | Dashboard | Urgency-grouped home, summary cards, filters, TCO, service timeline, list animations |
| 8 | Search + notifications | Document search, configurable reminders, intelligent notifications, claim info, swipe-to-archive |
| 9 | Auto-detect + settings | Settings screen, MediaStore auto-detect, onboarding, splash screen |
| 10 | Ship it | Google Drive backup, real app icon, Play Store listing, privacy policy, final QA |

(Sprint 11, WhatsApp PDF auto-detect, was scoped and shelved 2026-08-25
— manual Share-to-Warden covers this case today; see the Sprint 11
section above for the full record and why.)

Each sprint leaves a usable, improved app — you can stop at any point
and have something better than what you started with, consistent with
the project's "something you can run on your phone at the end of each
sprint" principle.
