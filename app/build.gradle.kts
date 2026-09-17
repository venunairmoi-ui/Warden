import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing, added 2026-08-27 for beta distribution to test users
// ahead of Play Store go-live. Loaded from a local, git-ignored
// keystore.properties at the project root (see .gitignore) -- the actual
// keystore file and its passwords are never hardcoded here or checked
// into version control. Guarded so the project still configures cleanly
// on a machine that doesn't have keystore.properties (a fresh clone
// before the keystore file has been copied over) -- the release build
// type simply comes out unsigned in that case rather than failing the
// whole Gradle sync.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    // Renamed 2026-09-15 (0.15.12): com.venunair.warden -> com.venunair.wisma
    // -- the placeholder package/namespace, finalized once "Wisma" (not
    // "Warden") was confirmed as the actual app name, so the id now
    // matches the brand instead of a name that was only ever a working
    // title. Permanent once published, per the note this comment used to
    // carry -- there is no test/beta install of this applicationId out in
    // the world yet, so this was the right and only time to do it.
    // No IDE available in this environment for Refactor > Rename Package,
    // so this was done as a manual, whole-tree find/replace of
    // "com.venunair.warden" -> "com.venunair.wisma" (package declarations,
    // imports, the two NotificationHelper action-string constants) plus
    // moving app/src/main/java/com/venunair/warden/ -> .../wisma/ and
    // app/schemas/com.venunair.warden.data.WardenDatabase/ -> the matching
    // wisma path -- verified with a full compileDebugKotlin afterward.
    namespace = "com.venunair.wisma"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.venunair.wisma"
        minSdk = 26 // Android 8.0 — needed for NotificationChannel (Sprint 5) without a compat shim
        targetSdk = 36
        // Bumped with each meaningful fix/feature from here on -- previously
        // stuck at 1/"0.1.0-sprint1" since Sprint 0, which made "did you
        // rebuild with the latest code" impossible to self-check. Check
        // Settings > Apps > Wisma > App details (or long-press the icon >
        // App info) to see versionName on-device before re-testing a fix.
        // 0.9.0-sprint10: Wisma rebrand (name/icon/palette) + full UI
        // redesign against the DESIGN.md token spec and the 4 Stitch
        // mockups -- Dashboard, Add/Edit Item, Item Detail, a new brand
        // Splash screen, and Settings/Onboarding/Search restyled to match.
        // 0.9.1-sprint10-feedback: post-review fixes -- darker Hero card,
        // detected-attachment-field review/apply, dedup'd status text,
        // removed debug sample-data seeding, renamed scan action,
        // clickable/consistent At risk + Monthly subscriptions cards with
        // an Active category breakdown, Home/Office dropped from the
        // category chips, green Active/Comfortable urgency colour, and
        // date-editable service/renewal tracking.
        // 0.9.2-sprint10-overview: new Overview landing screen (total count,
        // Active/Due soon/Expired tiles, split At risk/Monthly subscriptions
        // card) replaces My Products as the post-onboarding start screen;
        // My Products is now a filtered drill-down screen reached by tapping
        // an Overview tile, with its own back button and dynamic title.
        // 0.9.3-sprint10-overview-fix: build review caught the 3-across
        // tile row not matching the mockup -- Active/Due soon/Expired are
        // now three separate full-width stacked cards (dot + label, like
        // the mockup), sized to still fit one screen without scrolling.
        // 0.10.0-retaxonomy: ItemCategory replaced -- Warranty, Insurance,
        // AMC (was "Service Contract" on-screen), Subscription, Membership,
        // plus OTHER as a migration-only catch-all for items whose old
        // category (Document/Vehicle/Home/Office/Financial) no longer
        // exists. Each category now has its own fixed subcategory list
        // (new Item.subCategory field, Room v5 -> v6). Existing items in a
        // removed category are moved to OTHER on upgrade, not lost.
        // 0.10.1-retaxonomy-itemtype: Item Type dropdown removed from
        // Add/Edit -- it had become a second field asking almost the same
        // question Category now answers, and the two could (and once
        // already did, in sample data) drift out of sync. It's now always
        // derived from category. "Monthly subscriptions" tracking (Overview
        // money card, digest notification, renewal-approaching grouping)
        // now keys off any item with a billing amount attached, not just
        // ones typed Subscription -- so a billed AMC or Insurance item
        // counts too. No schema change.
        // 0.11.0-product-service: Item Type reintroduced as an independent
        // Product/Service field (not a repeat of the earlier mistake --
        // Category answers "what domain", this answers "is there a
        // physical thing here", which genuinely differ within a category:
        // a workmanship warranty, a housekeeping AMC, a subscription box).
        // Defaults from category, always user-editable, drives the Product
        // details section. Insurance's Cost/reference-number fields now
        // read "Sum insured / coverage amount" and "Policy number"; AMC's
        // reference number reads "Contract number" and gains one new field,
        // "Visits included per year" (the contract's entitlement -- usage
        // tracking deliberately not built, see Item.visitsIncluded doc
        // comment for why). Room v6 -> v7.
        // 0.11.1-overview-active-fix: bug fix -- Overview's Active tile (and
        // My Products' Active/Due soon/Expired quick filters) used to fold
        // groupByUrgency's "Renewal approaching" bucket into Active. That
        // bucket is any billed, auto-renewing item due within 30 days,
        // including today -- so a subscription/AMC/etc. due today or
        // tomorrow counted as Active and was invisible under Due soon
        // entirely. All four now classify by plain day-math alone, same
        // convention the money-at-risk card already used, independent of
        // billing/auto-renew status. No schema change.
        // 0.11.2-due-today-and-red: two fixes -- (1) "due today" now
        // classifies as Due soon everywhere (Overview tile, quick filters,
        // item accent bar/status pill), not Expired; the footer/Hero-card
        // text already said "Due today" while the color/pill said
        // "EXPIRED", which disagreed. (2) Expired's color no longer reuses
        // colorScheme.error -- its dark-theme tone is deliberately
        // desaturated for M3's validation-error role and read as faded
        // orange on a dark device; OVERDUE now has its own dedicated red
        // pair (WardenOverdue/WardenOverdueDark), same pattern the Due
        // soon/Active tiers already used. No schema change.
        // 0.11.3-ocr-more-fields: OCR pass extended, feedback 2026-08-26 --
        // vendor detection was whitelist-only (a fixed ~30-brand list), so
        // it missed real invoices that print their seller plainly. Added a
        // labeled-line pass ("Sold by:"/"Seller:"/"Billed by:"/etc.), tried
        // before the whitelist, same technique serial/model already used.
        // Also taught OCR three Product-details fields it never looked for
        // at all: retailer (own label pass + a small known-retailer-chain
        // list), invoiceNumber, and the AMC/Insurance/Warranty reference
        // number (feeds Item.amcNumber). All three follow attach-time
        // blank-fields-only auto-fill same as vendor/date/cost/serial/model,
        // and all three now also show in the attachment viewer's "Detected
        // from scan" review/apply block. No schema change.
        // 0.12.0-archive-recovery: reported 2026-08-26 -- archiving an item
        // was effectively one-way once the 5-second "Undo" snackbar was
        // missed: observeActiveItems/searchByNameOrVendor both filter on
        // status != ARCHIVED, and nothing else in the app showed an
        // archived item at all. New "Archived items" screen, reachable from
        // Settings > Data, lists every archived item (most recently
        // archived first, new Item.archivedAt field) with Restore and
        // Delete permanently actions. Room v7 -> v8: Item.archivedAt added
        // (INTEGER/epoch-day, matching every other date column); existing
        // ARCHIVED rows backfilled to today since their real archive date
        // was never recorded.
        // 0.13.0-amc-service-tracking: reported 2026-08-26 -- "an AMC
        // consists of a set of services... it is difficult to keep track
        // of these since companies rarely call back, the user must
        // initiate." Splits AMC service-visit logging away from renewal,
        // which the old single "Mark serviced" action conflated (every
        // visit silently pushed expiryDate forward, so a 2-visit/year AMC
        // logged the normal way added 2 years of expiry in one contract
        // year). Renewal now happens only via Edit (extending expiryDate),
        // auto-detected and reset automatically -- everything else
        // (logging a visit, the visits-used counter, due-service nudges)
        // is untouched by it. New Item fields: currentPeriodStart (the
        // period boundary services are counted against), serviceIntervalMonths
        // (period ÷ visits, evenly spread -- editable), serviceDueNotifiedForDate
        // (internal, self-resetting notification-dedup state). AMC item
        // detail screen shows "X of Y services used" + next expected date,
        // with a new "Log a service" action. Two new nudges on top of the
        // existing daily reminder worker: a "may be due for a service" one
        // off the computed next-expected date, and an AMC expiry reminder
        // now also mentions unused included services. Room v8 -> v9.
        // 0.13.1-service-receipts: reported 2026-08-26 -- "when logging a
        // service, allow attaching the vendor-provided receipt." Log a
        // service now offers Photo (gallery) / PDF capture before Confirm;
        // OCR runs on it same as any other attachment (searchable later),
        // but its detected fields aren't auto-filled anywhere -- there's no
        // form to fill in this dialog. The receipt links to that specific
        // ServiceEvent (new Attachment.serviceEventId, nullable, itemId
        // stays set too so item-delete cleanup and item-wide search are
        // unaffected) and shows as a "Receipt" button on that visit's row
        // in Service history, opening the same viewer the item's own
        // Invoice section already uses. Live camera capture isn't included
        // in this pass -- it uses a dedicated full-screen route elsewhere
        // in this app that doesn't fit inside a modal dialog without a
        // larger navigation change. Room v9 -> v10.
        // 0.14.0-category-fields: reported 2026-08-30 -- every category
        // showed identical generic fields regardless of what it actually
        // was. Added category-specific fields: Nominee (Insurance),
        // Service provider contact (AMC), Warranty type (Warranty),
        // Plan/tier (Subscription + Membership), Members covered
        // (Membership). Also gave the shared Billing/reference-number/cost
        // fields category-aware labels (Premium / Premium frequency for
        // Insurance, Membership fee for Membership, etc). Room v10 -> v11.
        // 0.14.1-product-fields-fix: reported 2026-09-01 -- "when I select
        // Insurance, product details still appear (serial number, model
        // number, retailer, invoice number)". itemType (Product/Service)
        // was only ever defaulted once from the initially-selected category
        // and never re-derived on switching -- switching from Warranty
        // (defaults to Product) to Insurance left it stuck at Product.
        // Insurance and Membership are never physical products in this
        // app's field set, so the Product/Service choice is now hidden and
        // forced to Service for both. Version bumped (not just a comment,
        // this time) specifically so Settings > About actually shows a
        // different string than 0.14.0 did -- there was otherwise no way
        // to confirm from the phone alone which build was actually
        // installed.
        versionCode = 37
        versionName = "0.14.1-product-fields-fix"

        // 0.14.2-scan-cta-reorder: reported 2026-09-01 -- "a new user may
        // enter information, then discover, when trying to attach, that
        // the system could have extracted that data automatically."
        // Attachments (Take photo/Choose from gallery/Attach PDF + the
        // thumbnail row) moved from the very bottom of the Add/Edit form
        // to a new highlighted "Scan instead of typing" card at the very
        // top, above every text field -- so the auto-fill option is the
        // first thing a new user sees, not something found after already
        // typing everything by hand. OCR still only fills fields that are
        // still blank at scan time either way (parseReceiptFields), so
        // nothing about the underlying auto-fill logic changed -- this is
        // purely a discoverability/ordering fix.
        versionCode = 38
        versionName = "0.14.2-scan-cta-reorder"

        // 0.14.3-scan-cta-shorten: reported 2026-09-01 -- "the list is too
        // large... shorten the text so the added information is more
        // visible." Dropped the separate explanatory sentence under the
        // card's title entirely; the title itself now states the action
        // ("Scan / pick / attach instead of typing") with the three icons
        // directly beneath it, so the card takes noticeably less vertical
        // space before the rest of the form's fields come into view.
        versionCode = 39
        versionName = "0.14.3-scan-cta-shorten"

        // 0.15.0-region-settings: 2026-09-01 -- "Concentrate only on
        // English speaking countries... I don't want to disturb what we
        // are doing for India... the current app is not in any way
        // affected." Adds a Settings > Region picker (India / United
        // States / United Kingdom / Canada / Australia) that controls only
        // currency symbol/grouping and date order -- see data/Region.kt's
        // doc comment for the full design rationale. India stays first and
        // is the default for every existing and new install; the INDIA
        // branch in ui/common/CurrencyFormat.kt and DateFormat.kt is the
        // exact same Locale("en","IN")/"dd-MM-yyyy" that was hardcoded
        // before this setting existed, so nobody who doesn't open Settings
        // sees any change at all. Stored in DataStore (SettingsRepository),
        // NOT Room -- no database migration needed for this feature, unlike
        // every field added earlier this sprint. Scan/attach auto-fill
        // (OCR) is NOT region-aware yet and remains tuned for Indian
        // receipts (₹/Rs/INR, day-first dates) regardless of this setting
        // -- a disclosed, deliberate scope limit for this pass, not a bug.
        versionCode = 40
        versionName = "0.15.0-region-settings"

        // 0.15.1-recurring-costs-breakdown: 2026-09-01 -- "I have a monthly
        // subscription card on the first screen which also includes AMC.
        // This will confuse users. Shows total value for each item
        // separately." Root cause: Item.isRecurringPayment is deliberately
        // category-agnostic (any category with a billing cycle + amount
        // set counts), but the Dashboard card was plainly labelled "Monthly
        // subscriptions" with no hint that a billed AMC/Insurance/
        // Membership item could be inside that number too. Renamed the
        // card (and its matching filtered-list title) to "Recurring
        // costs", and replaced the old bare item-count subtitle with a
        // per-category breakdown (e.g. "Subscription ₹499 · AMC ₹300"),
        // sorted highest first -- the total is unchanged, but what it's
        // made of is now visible in the same glance instead of requiring
        // a tap-through. No new screen, no new dependency -- see
        // HomeViewModel.HomeSummary.recurringByCategory and
        // OverviewScreen's recurringBreakdownText for the implementation.
        versionCode = 41
        versionName = "0.15.1-recurring-costs-breakdown"

        // 0.15.2-build-fix: 2026-09-01 -- 0.15.1 failed to compile.
        // recurringBreakdownText() called the @Composable toCurrencyString()
        // inside joinToString(" · ") { ... }'s transform lambda -- that
        // parameter is a *nullable* function type, which Kotlin can't
        // actually inline even though joinToString itself is `inline`, so a
        // Composable call inside it is rejected ("@Composable invocations
        // can only happen from the context of a @Composable function").
        // Fixed by building the per-category strings with map() first
        // (whose transform parameter is non-nullable and genuinely
        // inlined, so the Composable call is fine there) and only then
        // joining the resulting List<String> with a plain, lambda-free
        // joinToString(" · "). No behaviour change -- same text, same
        // formatting -- purely a fix to get 0.15.1's feature to compile.
        versionCode = 42
        versionName = "0.15.2-build-fix"

        // 0.15.3-cost-label-clarity: 2026-09-01 -- "When I add an item
        // under AMC, I see a cost and a billing amount per cycle. What is
        // the cost? If that is the purchase price, label it as such."
        // costLabel() used to return generic "Cost" for every category
        // except Insurance, with no hint of what it meant -- confusing
        // wherever a category ALSO shows the Billing section (AMC,
        // Subscription, Membership, Other all do). This field genuinely
        // IS a purchase price everywhere it isn't Insurance/Membership --
        // ItemDetailScreen's own TCO section already calls this exact
        // field "Purchase price"; the Add/Edit form's label was simply out
        // of sync with that. Now: Warranty/Subscription/AMC/Other ->
        // "Purchase price", Membership -> "Enrollment / joining fee"
        // (nobody "purchases" a membership), Insurance unchanged ("Sum
        // insured / coverage amount", already distinct from "Premium
        // paid"). Same underlying `cost` column throughout -- relabel
        // only, no migration, no new field. Also: "display the recurring
        // cost per month" -- the Dashboard's Recurring-costs card and its
        // per-category breakdown now both show "/mo" explicitly (label
        // renamed to "Monthly recurring costs"), since the figures were
        // already monthly-normalised but nothing on screen said so.
        versionCode = 43
        versionName = "0.15.3-cost-label-clarity"

        // 0.15.4-debug-menu-in-release: 2026-09-01 -- "The check reminders
        // and Add test records are both missing. It needs to be built
        // back." Both were gated behind BuildConfig.DEBUG, which is false
        // in the signed release APK the user (and testers) actually
        // install -- so the whole "⋮" debug menu on the Overview screen
        // never rendered outside a debug-variant build, not just these two
        // items. Decision (asked, not assumed): show the menu
        // unconditionally in every build, release included, rather than a
        // hidden unlock gesture or a second debug-signed APK -- these are
        // internal testing tools, not a security boundary. Also restores
        // "Add test records" (was "Load sample data", removed 2026-08-25,
        // function kept dormant in SampleData.kt for exactly this
        // reconnect-later case) -- seeds realistic items via the same
        // ItemRepository.saveItem() path every real Add uses, spanning
        // every category and urgency band, useful for exercising the
        // reminder/urgency/billing logic without hand-typing test data.
        versionCode = 44
        versionName = "0.15.4-debug-menu-in-release"

        // 0.15.5-real-app-icon: 2026-09-13 -- Sprint 10's "replace the
        // placeholder adaptive icon" item. Foreground raster
        // (mipmap-*/ic_launcher_foreground.png) regenerated from the
        // user-provided "WISMA icon.png" (shield/wrench/document/person
        // mark), cropped to its own white rounded-card bounds (drop-shadow
        // halo trimmed off) and scaled to 72% of the 108dp adaptive-icon
        // canvas. Background vector recolored from the electric-blue
        // placeholder fill to plain white to match that card's own color,
        // so the launcher mask edge (circle/squircle/rounded-square, varies
        // by device) stays invisible either way. Manifest/AndroidManifest.xml
        // icon reference (@mipmap/ic_launcher) is unchanged -- only the
        // underlying assets moved.
        versionCode = 45
        versionName = "0.15.5-real-app-icon"

        // 0.15.6-dashboard-splash-icon-fix: 2026-09-13 -- "I can see the old
        // logo on the dashboard." 0.15.5 only re-wired the launcher icon;
        // the Overview screen's TopAppBar lockup and the in-app Splash
        // screen use two separate, tintable line-art drawables
        // (ic_wisma_mark / ic_wisma_splash_mark, ColorFilter.tint'd to
        // onPrimary/onPrimaryContainer per theme) that were untouched and
        // still showed the old placeholder shield outline. Regenerated both
        // from the same "WISMA icon.png" source: the shield+checkmark tile
        // isolated from its 2x2 grid, then keyed to a white-on-transparent
        // silhouette (white shield fill + a checkmark-shaped cutout) via a
        // whiteness threshold -- the same luminance-as-alpha technique the
        // 2026-08-25 pass used -- rather than dropping in the new icon's
        // full-color art directly, which a flat SrcIn tint would've just
        // squashed into a solid color block anyway. No Kotlin changes --
        // both screens' existing ColorFilter.tint calls work unmodified
        // against the new art.
        versionCode = 46
        versionName = "0.15.6-dashboard-splash-icon-fix"

        // 0.15.7-one-splash: 2026-09-13 -- "why is there two launch
        // screens." Every cold start showed the platform SplashScreen API's
        // own branded moment (real app icon on brand-blue, held >=200ms --
        // MainActivity.installSplashScreen + themes.xml's
        // Theme.Warden.Splash) immediately followed by this app's own
        // separate Compose SplashScreen (gradient + shield glyph +
        // wordmark + tagline, held 600ms) -- two brand screens back to
        // back, each with a different treatment of the icon. Removed the
        // Compose screen from the nav graph entirely (WardenNavHost always
        // starts at Home/Onboarding now; deep-link/share hand-offs are
        // unaffected, they already skipped it) and deleted
        // ui/splash/SplashScreen.kt + its dedicated ic_wisma_splash_mark
        // asset as dead code. One splash now -- the system one -- and it
        // already shows the real app icon from 0.15.5's launcher fix, no
        // new asset needed.
        versionCode = 47
        versionName = "0.15.7-one-splash"

        // 0.15.8-ui-commercial-phase1: 2026-09-15 -- Phase 1 of the
        // "Warden UI and Commercial-Readiness Plan" doc's action plan
        // (consultant assessment verified against the actual code first;
        // most of the doc's UI-polish complaints turned out already fixed,
        // these were the confirmed remaining gaps):
        // - Product cards and the item detail screen gained a Archive/Edit
        //   overflow menu, so swipe is no longer the only way to archive
        //   (detail screen previously had no archive path at all).
        // - Add/Edit form's Name and Expiry fields now show their own
        //   inline error text instead of one generic banner at the bottom.
        // - Added toRelativeDueString() ("Due in 11 months", "Expired 3
        //   days ago") next to the exact date on the Expiry field; product
        //   cards roll "334 DAYS REMAINING" over to "11 MONTHS REMAINING"
        //   past 60 days out.
        // - Externalized ~78 hardcoded strings in HomeScreen.kt/
        //   AddEditItemScreen.kt into strings.xml (29 new entries, ~25
        //   pre-existing ones that were sitting unused).
        // Deliberately NOT touched: the debug/testing overflow menu (Check
        // reminders now / Run scan now / Add test records) -- the doc
        // recommended hiding it from release builds, but that's a reversal
        // of an explicit prior decision (see 0.15.4's comment above,
        // "asked, not assumed"), so it was left showing in every build per
        // the user's call when re-asked this session.
        versionCode = 48
        versionName = "0.15.8-ui-commercial-phase1"

        // 0.15.9-vault-ledger: 2026-09-15 -- Phase 2 of the UI/commercial-
        // readiness plan. Four independent pieces of work:
        // - isMinifyEnabled and lint's checkReleaseBuilds re-enabled
        //   (see their own updated comments below/above); verified with
        //   two full signed assembleRelease builds, including one after
        //   every other change in this entry.
        // - New Privacy screen (Settings -> Privacy): where data lives,
        //   plain-language reasons for each permission, and a working
        //   "Delete all my data" control (ItemRepository.deleteAllItems()
        //   + getAllAttachmentsForDeletion(), cascading via the existing
        //   ON DELETE CASCADE FKs).
        // - Accessibility baseline pass: fixed 3 touch targets that had
        //   drifted under Android's 48dp minimum (one introduced this
        //   session in the card overflow menu, two pre-existing), added
        //   1 missing TalkBack label (AttachmentThumbnail's tap target),
        //   checked for color-only status indicators (found none -- the
        //   app already consistently pairs color with a text label).
        //   Deliberately code-level only, not a substitute for an actual
        //   on-device TalkBack pass.
        // - "Vault Ledger" branding direction (approved this session,
        //   after a published mockup comparing it against the old Wisma
        //   fintech look): ui/theme/Color.kt's whole palette replaced
        //   (electric-blue -> vault green primary, brass secondary, steel
        //   tertiary, warm graphite/stone neutrals instead of near-black/
        //   cream); ui/theme/Type.kt's Sora/Geist/Geist Mono trio replaced
        //   with Fraunces/Archivo/IBM Plex Mono (old font files deleted);
        //   OverviewScreen's top-bar wordmark "WISMA" -> "Warden", plus
        //   every other user-facing "Wisma" string (app_name included --
        //   this is what's shown under the launcher icon and in Android
        //   Settings > Apps) and the two remaining hardcoded old-blue hex
        //   values (NotificationHelper's accent color, colors.xml's splash
        //   background) brought in line with the new primaryContainer.
        //   NOT done as part of this: the mockup's card-status "stamp"
        //   treatment (cosmetic card-anatomy change, kept for a later
        //   pass) and the launcher icon glyph itself (still the existing
        //   shield/wrench/document/person mark -- reads fine under the
        //   new direction, wasn't part of what changed here).
        versionCode = 49
        versionName = "0.15.9-vault-ledger"

        // 0.15.10-drive-backup: 2026-09-15 -- Google Drive backup/restore,
        // the app's first network feature. New backup/DriveBackupManager.kt
        // + Settings' Backup section wired to real Back up now/Restore
        // buttons (was a "coming soon" placeholder). Uses the Authorization
        // API (com.google.android.gms.auth.api.identity), NOT the older
        // GoogleSignInClient -- that class was REMOVED from play-services-
        // auth entirely as of the version this app pulls; an earlier draft
        // written from memory against the old API didn't compile
        // ("unresolved reference: GoogleSignIn"), caught before it shipped.
        // Scope: DriveScopes.DRIVE_APPDATA only (this app's hidden Drive
        // folder, invisible in the user's normal Drive, one rolling backup
        // not a version history). No server/web OAuth client ID needed in
        // code -- only the Android OAuth client (package + signing SHA-1,
        // registered directly in Google Cloud Console) required for the
        // scope-authorization flow this app actually uses.
        // Restore is staged, not applied live: a downloaded backup is
        // unzipped to a staging directory, and the actual database-file
        // swap happens in WardenApplication.onCreate() (via
        // DriveBackupManager.applyPendingRestoreIfAny(), called before
        // `database` is ever touched) after the user restarts the app --
        // swapping Room's live warden.db file out from under an
        // already-open connection was judged too easy to get subtly wrong
        // without a device to verify against.
        // Also: added the INTERNET permission (first ever in this app),
        // updated PrivacyScreen.kt/settings_tagline's "stays on your
        // device" claims to say "unless you turn on Drive backup" instead
        // of going stale, and fixed two Gradle packaging conflicts (a
        // duplicate META-INF/INDEX.LIST between two transitive Google auth
        // jars, and Apache HttpClient getting pulled in transitively
        // despite this app using NetHttpTransport exclusively -- excluded
        // project-wide via configurations.all).
        // Verified: full signed assembleRelease build succeeds with R8
        // minification on. NOT verified: no device available in this
        // development environment to actually run the sign-in consent
        // flow, a real backup, or a real restore -- this whole feature
        // needs an on-device test before being trusted, more so than
        // anything else shipped so far this project (first feature
        // touching a network call, external OAuth consent, and a
        // cross-restart file swap all at once).
        versionCode = 50
        versionName = "0.15.10-drive-backup"

        // 0.15.11-keep-wisma-name: 2026-09-15 -- reverted the "Warden"
        // wordmark from the Vault Ledger branding pass back to "Wisma",
        // same day. Reason: checked for trade-name conflicts before
        // committing to the new name and found several existing Android
        // apps already called "Warden" (WardenGPS, Warden: Security &
        // Privacy, WardenCam, plus an unrelated open-source Warden app-
        // management utility) -- real crowding/trademark risk for an
        // unverified name, not worth taking on when "Wisma" was already
        // the known quantity. Only the name reverted -- the vault-green/
        // brass/steel palette and Fraunces/Archivo/IBM Plex Mono
        // typography from that same pass stayed as-is, since those were
        // never the risk. Reverted: app_name and every other user-facing
        // string that said "Warden" (strings.xml, OverviewScreen's top-bar
        // wordmark, PrivacyScreen's body copy, NotificationHelper's
        // auto-detect notification text, DriveBackupManager's Drive API
        // application-name header). NOT reverted (code identifiers, not
        // brand text, not worth a mechanical rename): WardenApplication,
        // WardenDatabase, WardenTheme, Theme.Warden style names,
        // com.venunair.warden package/applicationId itself.
        versionCode = 52
        versionName = "0.15.12-applicationid-rename"

        // 0.15.13-pdf-password-support: 2026-09-15 -- reported same day,
        // a second real insurance PDF this time needing a password to
        // open. android.graphics.pdf.PdfRenderer (every other PDF path in
        // this app -- see PdfPageRenderer.kt) simply cannot open an
        // encrypted PDF: no password parameter exists before API 35
        // (Android 15), which is effectively this app's whole real
        // userbase given minSdk 26, and it used to fail silently
        // (runCatching {}.getOrNull() at the handleNewAttachment call
        // site) -- no thumbnail, no OCR fields, no indication why.
        // Confirmed opening the PDF in another viewer first doesn't help:
        // a Share intent hands this app the same still-encrypted bytes
        // regardless of what unlocked it for viewing elsewhere.
        // New: PdfDecryptor.kt (pulls in PdfBox-Android, com.tom-roush:
        // pdfbox-android -- Apache 2.0, free, ~few MB APK size increase --
        // reversing PdfPageRenderer.kt's original "$0-new-dependency-cost"
        // decision, now that a real document that constraint can't handle
        // has actually shown up). Used for exactly one job: given a
        // user-supplied password, open the encrypted PDF, strip its
        // security, and save a plain decrypted copy into this app's normal
        // attachment storage -- from that point on it flows through the
        // unchanged native PdfPageRenderer pipeline (thumbnail, OCR, the
        // in-app viewer) like any other PDF. The password itself is used
        // only transiently to open the document; it's never written to
        // disk, the database, or logs. handleNewAttachment now checks
        // PdfDecryptor.isPasswordProtected (narrowly, via the specific
        // SecurityException PdfRenderer throws for this exact case) before
        // the OCR pipeline runs at all, and puts up a password AlertDialog
        // (Unlock/Cancel, inline "incorrect password" retry) instead of
        // silently producing an empty attachment.
        // Known gap, not fixed this pass: if the app is backgrounded or
        // navigated away from while this dialog is still open/unresolved,
        // the already-copied encrypted local file isn't cleaned up (a
        // storage leak, not a correctness bug -- it's just an orphaned
        // file, never referenced by any Attachment/PendingAttachment row).
        versionCode = 53
        versionName = "0.15.13-pdf-password-support"

        // 0.15.14-tablet-adaptive-layout: 2026-09-15 -- Phase 3 item,
        // shipped earlier the same day, committed here now. Two-pane
        // list-detail layout for tablets/foldables wide enough to cross
        // the EXPANDED breakpoint (840dp), built on Google's Material3
        // Adaptive library rather than hand-rolling pane arrangement.
        // New: ui/home/ProductsAdaptiveScreen.kt wraps the EXISTING
        // HomeScreen (list pane) and ItemDetailScreen (detail pane)
        // composables unchanged, rewiring only their navigation callbacks
        // to a local ListDetailPaneScaffoldNavigator instead of the outer
        // NavController. WardenNavHost's Products destination now branches
        // on currentWindowAdaptiveInfo().windowSizeClass.
        // isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND): below
        // that, the exact pre-existing single-pane code runs byte-for-byte
        // unchanged (zero regression risk to the already device-verified
        // phone experience); at or above it, routes to
        // ProductsAdaptiveScreen. Editing still goes through the outer
        // NavHost as a full screen on both paths, not folded into the pane
        // scaffold's own navigator.
        // API note: despite some docs describing a
        // "NavigableListDetailPaneScaffold" convenience composable, no
        // such function exists in the pinned 1.2.0 release (adaptive/
        // adaptive-layout/adaptive-navigation deliberately NOT bumped to
        // 1.3.0, which requires compileSdk 37 + AGP 9.1.0 -- a bigger
        // toolchain jump than taking on blind) -- confirmed by extracting
        // the real AAR/sources jars from Google's Maven repo rather than
        // trusting a docs summary a second time this session.
        // ListDetailPaneScaffold + rememberListDetailPaneScaffoldNavigator
        // composed by hand instead, including manual back-handling.
        // Verified: full compileDebugKotlin + signed assembleRelease (R8
        // on) both succeed. Device-tested the COMPACT/phone path (open My
        // Products, open an item, back) and confirmed it behaves exactly
        // as before -- expected, since that code path is unchanged, but
        // confirmed rather than assumed.
        // NOT verified: the actual two-pane EXPANDED-width behavior itself
        // -- no tablet, foldable, or emulator available in this
        // environment. Check on a resizable emulator or real large-screen
        // hardware before trusting this in production.
        versionCode = 54
        versionName = "0.15.14-tablet-adaptive-layout"

        // 0.16.0-billing-scaffold: 2026-09-16 -- freemium licensing model
        // (see project memory "warden-android-playstore-licensing"): OCR
        // free for a 30-day trial then Premium-gated, Backup Premium-only
        // from day one. This entry adds the actual Play Billing purchase
        // plumbing for the one-time "Premium unlock" non-consumable
        // product: new billing/BillingManager.kt (BillingClient v9.1.0,
        // wraps connection/query/purchase/acknowledge/restore behind a
        // small suspend-friendly API), wired up in WardenApplication
        // (process-lifetime instance, startConnection() called from
        // onCreate like every other manual-DI singleton there) and
        // threaded through WardenNavHost into SettingsScreen's Premium
        // section, which now shows an "Upgrade to Premium" button when
        // premiumUnlocked is false.
        // NOT LIVE YET: the product id BillingManager.PREMIUM_UNLOCK_
        // PRODUCT_ID ("premium_unlock") doesn't exist in Play Console --
        // account is still under identity verification, and an in-app
        // product can't be created before that clears. Until then,
        // tapping Upgrade queries Play, gets an empty product list back
        // (not an error), and shows "Premium isn't available yet" rather
        // than crashing or hanging. Once the real product is created with
        // this exact id, this should work end-to-end with no code
        // changes. NOT verified on-device at all (no device available in
        // this environment) -- the whole purchase flow, including the
        // Play-side UI it launches, needs a real test once the product
        // exists.
        versionCode = 55
        versionName = "0.16.0-billing-scaffold"

        // 0.16.1-expired-label-fix: 2026-09-16 -- reported from a Play
        // Store screenshot review: the "Expired" quick-filter screen (from
        // Overview's EXPIRED tile) showed its item under a section header
        // reading "Expiring soon". Root cause: HomeViewModel.QuickFilter.
        // EXPIRED carried its filtered results through GroupedItems.
        // expiringSoon -- the same field QuickFilter.DUE_SOON reuses --
        // purely to piggyback on HomeScreen's existing rendering path, and
        // HomeScreen hardcodes that field's header to "Expiring soon"
        // regardless of which quick filter actually populated it. New
        // dedicated GroupedItems.expired field + its own "Expired" section
        // header in HomeScreen, tinted with the same ItemUrgency.OVERDUE
        // color the item's own accent bar/status pill already uses (not
        // colorScheme.error). HomeViewModel.selectAllVisible's "select all"
        // union updated to include the new field too, so bulk-select still
        // works when viewing the Expired quick filter. The main unfiltered
        // "My Products" list's own "Expiring soon" bucket (which
        // deliberately mixes overdue + due-soon items, per groupByUrgency's
        // own comment) is untouched -- this fix is scoped to the quick-
        // filter screens only. NOT verified on-device (no device available
        // in this environment).
        versionCode = 56
        versionName = "0.16.1-expired-label-fix"

        // 0.16.2-audit-fixes: 2026-09-16 -- three fixes from a static code
        // audit run ahead of Play Store submission (no device available in
        // this environment, so this was a code read, not a live test run):
        // (1) Cost/Billing amount fields had zero validation -- a negative
        //     number parsed fine as a Double and flowed unguarded into
        //     HomeViewModel's moneyAtRisk/totalRecurringMonthly sums,
        //     silently corrupting both the headline totals and the
        //     per-category breakdown (which drops negative categories via
        //     filterValues { it > 0.0 } while the headline sum still counts
        //     them). Both fields now validate (KeyboardType.Decimal, an
        //     inline error matching the existing Name-field pattern,
        //     invalid non-blank input blocks Save) -- blank still means
        //     "no cost given", only a genuinely invalid or negative
        //     non-blank entry is rejected.
        // (2) Item name had no length cap anywhere, and neither HomeScreen's
        //     product-card Text nor ItemDetailScreen's HeroCard title had
        //     maxLines/overflow -- a long pasted name wrapped across many
        //     lines and blew out card height. Added a 100-char cap on the
        //     Name field itself plus maxLines+ellipsis on both display
        //     sites.
        // (3) android:allowBackup="true" had no fullBackupContent/
        //     dataExtractionRules, so Android's OS-level Auto Backup could
        //     silently include the Room database and every attachment in
        //     the user's standard Android cloud backup regardless of
        //     whether they ever turned on Wisma's own in-app Drive backup
        //     toggle -- contradicting the published privacy policy's
        //     "stays on this device unless you turn on backup yourself"
        //     claim as actually shipped. New backup_rules.xml (pre-API-31)
        //     + data_extraction_rules.xml (API 31+) exclude exactly what
        //     DriveBackupManager's own backup already covers under the
        //     user's explicit control: warden.db, attachments/, and the
        //     DataStore settings file.
        // Verified: full signed compileDebugKotlin + assembleDebug succeed
        // (including a resource-compile round-trip on the two new XML
        // files, which caught an invalid "--" inside an XML comment before
        // this landed). NOT verified on-device.
        versionCode = 57
        versionName = "0.16.2-audit-fixes"

        // 0.16.3-premature-validation-fix: 2026-09-16 -- found via real
        // on-device E2E testing (a physical device, not an emulator --
        // no AVD existed and this was faster/more representative anyway),
        // NOT from static reading: the Add Item screen showed a red "Name
        // is required" error immediately on open, before any user
        // interaction. Reproduced 3 times independently, including a
        // deliberate re-verification after first suspecting a testing
        // artifact (a stray tap or a stale composable instance) -- ruled
        // both out by confirming a genuine fresh navigation via a UI dump
        // each time.
        // Root cause: Compose's onFocusChanged fires once on a field's
        // INITIAL composition, reporting the baseline "not focused" state
        // -- not only on a real focus-then-unfocus transition. The naive
        // `if (!it.isFocused) touched = true` pattern (Name, and this
        // same session's own Cost/Billing-amount fields from the
        // 0.16.2 audit fixes) fires on that spurious first callback,
        // before the user ever touches the field. Invisible on Cost/
        // Billing amount today only because blank is valid for those
        // optional fields; Name is required, so isBlank() alone was
        // enough to surface it immediately.
        // Fixed by gating on "was this field ever actually focused"
        // first (new nameHasBeenFocused/costHasBeenFocused/
        // billingAmountHasBeenFocused, one per field) -- touched only
        // flips true on a real focus-that-then-unfocuses transition, not
        // on the initial state report.
        // Verified live on-device (Oppo/OnePlus CPH2573, Android 16):
        // fresh Add Item opens now show a clean Name field on two
        // independently-confirmed fresh navigations; the 100-char Name
        // cap and Cost's negative-number Save-blocking (both from 0.16.2)
        // were also confirmed working for real on this same device, not
        // just at compile time. No crashes in logcat throughout.
        versionCode = 58
        versionName = "0.16.3-premature-validation-fix"

        // 0.16.4-qa-agent-fixes: 2026-09-16 -- two bugs found by a
        // dispatched QA subagent running a real, live E2E pass on the
        // physical device (comprehensive technical test, orchestrated
        // after the earlier fixes above):
        // (1) Overview's "Monthly recurring costs" card clipped real,
        //     valid text -- e.g. "$5,273.92/mo" cut to "$5,273.92/..."
        //     at the value's old hardcoded maxLines=1, and a realistic
        //     4-category breakdown cut to "...· S..." at the old
        //     subtitleMaxLines=2. Confirmed via UI dump (full text existed
        //     in the accessibility tree) vs. the visibly ellipsized
        //     screenshot. First fix attempt (raise subtitleMaxLines to 4)
        //     was caught and reverted before shipping -- confirmed live
        //     on-device that it violated OverviewScreen's own "fits the
        //     screen without a scroll" design, producing a taller card
        //     that rendered underneath the floating Add button with no
        //     way to reach the hidden text. Real fix has three parts:
        //     MoneySplitHalf's value line gained its own configurable
        //     valueMaxLines (2, for this card only -- a currency string
        //     is short and bounded, safe to grow); recurringBreakdownText
        //     now bounds the DATA shown (top 2 categories by amount plus
        //     a short "+N more" suffix) instead of hoping an unbounded
        //     joined string fits a fixed line count; and, since even that
        //     doesn't fully guarantee every real device/data-size
        //     combination fits above the FAB, OverviewScreen's whole
        //     Column is now wrapped in verticalScroll -- the only
        //     guarantee that actually holds, replacing the disproven
        //     "structurally fits, no scroll needed" claim.
        // (2) The "Expired" quick-filter header fix from 0.16.1 turned out
        //     incomplete: it only fixed the QuickFilter.EXPIRED branch
        //     (Overview's EXPIRED tile) -- the general My Products list
        //     (no filter) and the AT_RISK/SUBSCRIPTIONS quick filters all
        //     go through groupByUrgency(), which still lumped an
        //     already-expired item into the SAME expiringSoon bucket as
        //     genuinely due-soon items ("Expired or expiring within 30
        //     days", per the comment this replaces) -- the exact same
        //     mislabeling bug, just reachable from a different screen.
        //     groupByUrgency now splits negative-daysLeft items into their
        //     own expired bucket too, same as QuickFilter.EXPIRED already
        //     did. Required a matching fix in OverviewScreen.kt's own
        //     tile-counting (`allItems = grouped.expiringSoon + ... `)
        //     to add `+ grouped.expired`, since that line would otherwise
        //     have silently lost every expired item from the sum the
        //     moment groupByUrgency stopped putting them in expiringSoon --
        //     caught by re-reading that consumer before shipping, not by
        //     a test run.
        // Verified: full compileDebugKotlin + assembleDebug succeed (one
        // self-inflicted mistake caught by the same build: an edit
        // accidentally dropped the `subtitle = recurringBreakdownText(...)`
        // argument entirely, caught immediately by a real compile error
        // rather than shipped). Both the bounded-breakdown text and the
        // scroll-clears-the-FAB behavior were confirmed live on-device
        // (Oppo/OnePlus CPH2573) by actually scrolling the card and
        // reading the result, not just by reasoning about the code.
        versionCode = 59
        versionName = "0.16.4-qa-agent-fixes"

        // 2026-09-17: fixes from the chaos/end-user-persona QA pass (agent 2,
        // re-dispatched after the prior session paused mid-run -- see
        // versionCode 59's own log entry above for what agent 1 already
        // covered). Real bugs found via live on-device chaos testing, fixed
        // and re-verified live on the same device (Oppo/OnePlus CPH2573):
        // 1. Rapid-tapping a nav-triggering button (the Home Add FAB
        //    especially) pushed multiple destination instances onto the
        //    back stack -- every navController.navigate() call site was
        //    bare, with no duplicate-destination guard. Fixed with a
        //    navigateSafe() wrapper (WardenNavHost.kt) that only navigates
        //    when the current back stack entry is RESUMED -- the official
        //    Compose Navigation pattern for exactly this, so a tap landing
        //    mid-transition is naturally swallowed. Verified: 5 rapid FAB
        //    taps now take exactly 1 back-press to return to Home (was 3).
        // 2. Every free-text field except Name had no length cap --
        //    AddEditItemScreen.kt's Vendor, Location, Serial/Model number,
        //    Retailer, Invoice number, Service provider contact, plan tier,
        //    and nominee name could all grow unbounded, breaking the form's
        //    layout (live-reproduced on Vendor: ~180 chars pushed every
        //    field below off-screen). New MAX_TEXT_FIELD_LENGTH (200) /
        //    MAX_NOTES_LENGTH (2000) constants, same .take()-on-input
        //    pattern as the existing MAX_NAME_LENGTH fix. Verified: 400
        //    chars into Vendor now stops at exactly 200 on-device.
        // 3. AMC's Visits included / Service interval and Membership's
        //    Members covered had zero numeric validation -- a negative
        //    number saved and displayed verbatim, non-numeric text
        //    silently vanished via toIntOrNull() with no feedback. Fixed
        //    with the same blank-is-fine-but-invalid-blocks-save shape as
        //    Cost/Billing amount already use, gated on attemptedSave only
        //    (not a per-field touched/focused dance, since these are
        //    secondary fields).
        // 4. Date pickers had no chronological sanity check -- an expiry
        //    date before the purchase date saved fine. Added a
        //    dateOrderInvalid check (only once both dates are set) that
        //    blocks Save with an inline error on the Expiry field.
        // 5. Search results didn't refresh after a delete until the query
        //    was retyped -- HomeViewModel's search only re-ran on query-TEXT
        //    change (distinctUntilChanged on the query alone), not on the
        //    underlying item list changing. Now combined with the already-
        //    observed `allItems` StateFlow so any data change re-runs the
        //    current search too.
        // 6. An extremely long search query wrapped the "No results for..."
        //    message across many lines. Now truncated for display only (the
        //    real query still drives the actual search).
        // NOT fixed -- investigated and disproven, not a real app bug: the
        // QA agent also reported the item detail screen hanging
        // indefinitely on a chaos-input item ("ChaosMembership") that
        // supposedly couldn't be deleted afterward. Pulling the actual
        // on-device database found no such row at all (all 3 of the
        // agent's test items were in fact deleted -- sqlite_sequence's
        // high-water mark confirms it), and logcat shows ColorOS's own
        // memory-management process freezer (OsenseKillAction,
        // freeze_dur=1500ms) firing on essentially every app-switch back
        // into Wisma throughout the whole session -- routine OEM behavior
        // on this phone, not specific to that item. Far more likely
        // explanation for a ~1.5s unresponsive window than an app defect;
        // not chased further.
        // A second minor/cosmetic finding (search-result subtitle
        // concatenating vendor + members-covered with no separator) could
        // not be located in any actual rendering code path (ProductCard
        // renders vendor on its own separate Text, and members-covered
        // isn't shown on cards at all) -- left unfixed pending a real
        // reproduction, since the offending chaos item no longer exists to
        // re-check against.
        // Verified: full compileDebugKotlin + assembleDebug succeed;
        // FAB-rapid-tap and Vendor-length-cap fixes confirmed live on the
        // same physical device via uiautomator dumps, not just re-reading
        // the code.
        versionCode = 60
        versionName = "0.16.5-chaos-qa-fixes"

        // 2026-09-17 (user feedback, dashboard/UX pass): two asks. (1) "on
        // the dashboard the monthly recurring costs shows a value, but
        // clicking through shows a lot of cards without the individual
        // recurring cost of each" -- HomeScreen.kt's ProductCard never
        // showed a per-item billing amount at all, only ever one tap deeper
        // on the item's own detail screen. Added a "$X/mo" line under
        // vendor, same monthly-normalisation (billingAmount *
        // billingCycle.toMonthlyFactor()) as OverviewScreen's own headline
        // total, guarded by > 0.0 so a ONE_TIME-billed item never shows a
        // misleading "$0.00/mo". (2) "the last card feels cluttered... At
        // Risk can be a card by itself and Monthly recurring can be
        // another card... smaller font size for the amounts" --
        // OverviewScreen.kt's MoneySplitCard/MoneySplitHalf (one card split
        // by a vertical divider) replaced with MoneyCard, two independent
        // full-width stacked cards using the same surfaceContainerLow/
        // outlineVariant/shapes.large convention as OverviewStatCard, with
        // the headline amount's font size dropped 20sp -> 16sp now that
        // each card has a full line to itself.
        // Verified live on-device: the Recurring-costs filtered list now
        // shows each item's own monthly amount directly on its card
        // (Whirlpool AMC $291.67/mo, Netflix $649.00/mo, etc.) without
        // needing to open it; the Overview screen's two money cards render
        // as separate full-width cards, both clear of the floating Add
        // button, at the smaller font size.
        versionCode = 61
        versionName = "0.16.6-dashboard-declutter"

        // 2026-09-17 (follow-up to the above dashboard pass): "centre the
        // contents. For the last card keep only the label and the amount --
        // remove the Insurance/Subscription labels below. Also for the add
        // button just show the plus sign inside the circle." Three changes:
        // (1) MoneyCard's Column now centers every line (icon+label, value,
        // subtitle) instead of left-aligning, matching OverviewTotalHeader/
        // OverviewStatCard's own centered convention. (2) MoneyCard's
        // subtitle param is now nullable; the Monthly recurring costs call
        // site passes null, dropping the per-category breakdown
        // (recurringBreakdownText) entirely -- that function and its
        // MAX_RECURRING_CATEGORIES_SHOWN constant are now dead code and
        // removed. At risk this month keeps its "N items expiring" line,
        // not asked to change. (3) OverviewScreen's floatingActionButton is
        // a plain circular FloatingActionButton (icon only, real
        // contentDescription for accessibility now that there's no visible
        // label) instead of ExtendedFloatingActionButton's icon+"Add" text.
        // Verified live on-device.
        versionCode = 62
        versionName = "0.16.7-dashboard-centering"

        // 2026-09-17 (follow-up): "the gaps between cards are not even --
        // move the last two cards up to match the difference between the
        // top three cards. Move all the cards up so the Add button doesn't
        // overlap the cards." Two issues, one root layout fix each:
        // (1) The three OverviewStatCards sat in their own nested Column
        // with spacedBy(8.dp) while the outer Column (governing header-to-
        // stats, stats-to-money-cards, and between the two money cards)
        // used spacedBy(16.dp) -- so the stat trio read visibly tighter
        // than everything else. Unified to 8.dp everywhere and removed the
        // now-redundant nested Column.
        // (2) That same dashboard-declutter pass (smaller amount font, no
        // breakdown line, centered content) had already shrunk total
        // content enough that, combined with the old bottom=96.dp trailing
        // space, the whole Column's content fit within the viewport with
        // nothing left to scroll -- so the Monthly recurring costs card
        // rendered at its natural (short) position with the FAB statically
        // parked on its corner, un-scrollable-away. Bumped the trailing
        // padding to 140.dp, which reliably pushes total content past
        // viewport height so a swipe can always clear the real last card
        // of the FAB. Confirmed live on-device: even gaps throughout, and
        // scrolling now reveals the recurring-costs card fully clear of
        // the Add button with room to spare.
        versionCode = 63
        versionName = "0.16.8-dashboard-spacing-fix"

        // 2026-09-17 (feedback): two more changes.
        // (1) "For AMC, Membership and Subscription make the Billing cycle
        // and Billing amount mandatory. For Insurance make premium
        // frequency and premium paid mandatory." Insurance's "Premium
        // frequency"/"Premium paid" are the exact same billingCycle/
        // billingAmount fields, just relabelled (billingCycleLabel/
        // billingAmountLabel) -- one requirement check covers both.
        // AddEditItemScreen.kt gained billingRequiredForCategory (AMC,
        // MEMBERSHIP, SUBSCRIPTION, INSURANCE) plus billingCycleMissing/
        // billingAmountMissing, blocking Save with an inline "This field is
        // required." on whichever is empty, and a " *" appended to the
        // label the same way Name/Expiry already mark themselves required.
        // Warranty (no Billing section at all) and Other (genuinely
        // optional) are unaffected. Verified live on-device: AMC with both
        // fields blank shows both errors and Save is blocked; filling
        // either clears its own error independently.
        // (2) "The attached file cannot be opened normally -- you can see
        // a snapshot but it cannot be expanded... make the thumbnail
        // smaller but on clicking allow it to open normally in a PDF
        // viewer or the relevant application." AttachmentThumbnail shrunk
        // 88dp -> 64dp (delete-button/badge scaled to match). The viewer
        // dialog's preview was always a static, non-zoomable render (page
        // 1 only for a PDF) -- added an "Open" action (button + tapping
        // the preview itself) that hands the attachment to a real external
        // app via ACTION_VIEW, so a multi-page PDF can actually be paged
        // through and zoomed, not just glanced at. Attachment.localFileUri
        // is already a FileProvider content:// Uri with grantUriPermissions
        // already enabled in the manifest, so this needed no new
        // permission plumbing. Verified live on-device: tapping Open
        // launched the real Android share/open-with chooser (Photos, Files
        // by Google, etc.) for an attached image.
        versionCode = 64
        versionName = "0.16.9-billing-required-and-attachment-open"

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Re-enabled 2026-09-15 (Phase 2 of the UI/commercial-readiness
            // plan): dependency set is all mainstream AndroidX/Google
            // libraries (Room, WorkManager, CameraX, ML Kit, Coil) that
            // ship their own consumer ProGuard rules, and there's no
            // custom reflection-heavy networking stack (this app has none
            // -- everything is local-only), which keeps R8 risk low.
            // Verified with a full signed assembleRelease before this
            // landed; still worth an on-device smoke test (scan/OCR,
            // reminders, camera, notifications) before any real store
            // submission, since a clean build doesn't catch every
            // possible runtime stripping issue.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Only signed when keystore.properties exists locally -- see
            // its comment above. Without a signingConfig here, assembleRelease
            // still succeeds but produces an unsigned, uninstallable APK,
            // which is the whole reason this needed wiring up rather than
            // just running assembleRelease as-is.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true  // Sprint 6: needed for BuildConfig.DEBUG gating of dev buttons
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Phase 2: google-api-client-android pulls in google-auth-
            // library-oauth2-http and google-auth-library-credentials
            // transitively, both of which bundle an identical (and
            // functionally unused at runtime on Android) META-INF/
            // INDEX.LIST -- a benign packaging duplicate, not a real
            // conflict, so excluding it is the correct fix rather than
            // trying to pick one jar's copy over the other's. Same
            // reasoning for DEPENDENCIES below -- both come from Apache
            // HttpClient jars that shouldn't even be on the classpath
            // (see the configurations.all exclude further down), but
            // still leave this file behind even once excluded from the
            // dependency graph in some resolution paths.
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    // Re-enabled 2026-09-15 (Phase 2): the 2026-09-01 Windows file-lock
    // failure on lintVitalAnalyzeRelease's cache jar didn't reproduce when
    // re-tested against a real assembleRelease -- see that build's own log
    // if it starts flaking again on this machine. Since this app is now
    // actually headed toward Play Store submission, worth having this
    // back rather than silently skipping fatal lint issues.
    lint {
        checkReleaseBuilds = true
    }
}

// Room schema export — writes a JSON schema file per database version into
// app/schemas/ so Room can verify migrations at test time (and so the schema
// history is version-controlled). Room 2.6.x reads this via KSP argument.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Kotlin's `android.kotlinOptions {}` DSL is deprecated (kotl.in/u1r8ln) in favor
// of this top-level `compilerOptions` block, which is a separate, non-Android
// extension the Kotlin Gradle plugin adds — hence it lives outside `android {}`,
// not nested inside it.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Phase 2: the Google Drive backup libraries (google-api-client-android /
// google-api-services-drive / google-auth-library-*) transitively pull in
// Apache HttpClient (org.apache.httpcomponents:httpclient/httpcore) as one
// of several supported HTTP transport options -- unused dead weight here,
// since DriveBackupManager builds its Drive service on NetHttpTransport
// exclusively. The per-dependency exclude on google-api-client-android
// alone wasn't enough (a different transitive path -- google-auth-
// library-oauth2-http -- pulls it in too), so this excludes it project-
// wide instead of chasing every path individually. Also avoids the
// META-INF/DEPENDENCIES packaging clash between httpclient's and
// httpcore's own copies of that file (see the packaging{} block above).
configurations.all {
    exclude(group = "org.apache.httpcomponents", module = "httpclient")
    exclude(group = "org.apache.httpcomponents", module = "httpcore")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Sprint 5: local reminder scheduling
    implementation(libs.work.runtime.ktx)

    // Sprint 2: camera capture
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Sprint 4: on-device OCR (image + rendered PDF pages)
    implementation(libs.mlkit.text.recognition)

    // Sprint 9 bugfix: BitmapFactory never applies a JPEG's EXIF orientation
    // tag on its own -- needed to un-rotate camera photos before OCR (see
    // ui/common/LocalBitmap.kt's decodeSampledBitmap)
    implementation(libs.exifinterface)

    // Sprint 8: thumbnail display in search results
    implementation(libs.coil.compose)

    // Sprint 9: settings persistence (reminder defaults, digest frequency,
    // auto-detect toggle, theme mode, onboarding-completed flag)
    implementation(libs.datastore.preferences)

    // Sprint 9: branded splash screen (SplashScreen API, backported to API 26)
    implementation(libs.core.splashscreen)

    implementation(libs.kotlinx.coroutines.android)

    // Phase 2 (2026-09-15): Google Drive backup/restore. GoogleSignInClient
    // (not the newer Credential Manager APIs) deliberately -- well-
    // documented and stable for "sign in + request a Drive scope" is
    // exactly this app's whole need here, and there's no device available
    // from this development environment to shake out subtler bugs a
    // bleeding-edge API might hide. google-api-client-android +
    // google-api-services-drive are Google's own generated Drive v3 REST
    // client; google-http-client-gson is the JSON transport they need.
    implementation(libs.play.services.auth)
    // Apache HttpClient, transitively pulled by these, is excluded
    // project-wide instead of per-dependency -- see the
    // configurations.all block above for why.
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.google.http.client.gson)

    // Phase 3 (2026-09-15): tablet/foldable two-pane list-detail layout.
    // No tablet or foldable device available to test on in this
    // environment -- built against Google's documented breakpoints and
    // the real, decompiled 1.3.0 API surface (there is no
    // "NavigableListDetailPaneScaffold" convenience composable in this
    // stable version despite some docs describing one; ListDetailPaneScaffold
    // + rememberListDetailPaneScaffoldNavigator are composed by hand
    // instead -- see ui/home/ProductsAdaptiveScreen.kt), not verified on
    // real large-screen hardware.
    implementation(libs.adaptive)
    implementation(libs.adaptive.layout)
    implementation(libs.adaptive.navigation)

    // Password-protected PDF support -- see PdfDecryptor.kt and
    // libs.versions.toml's pdfbox-android entry for why this is the one
    // place in the app pulling in a third-party PDF library, reversing
    // PdfPageRenderer.kt's original "$0-new-dependency-cost" decision now
    // that a real user hit a document the native renderer categorically
    // cannot open (android.graphics.pdf.PdfRenderer has no password
    // parameter before API 35, and minSdk here is 26).
    implementation(libs.pdfbox.android)

    // Freemium "Premium unlock" one-time purchase scaffolding -- see
    // billing/BillingManager.kt. The in-app product itself doesn't exist
    // in Play Console yet (account still under identity verification), so
    // this compiles and runs today but has nothing real to sell until
    // that product is created.
    implementation(libs.billing)
}
