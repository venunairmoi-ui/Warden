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
    // Placeholder — rename before Play Store submission (applicationId must be
    // unique and permanent once published). Android Studio's Refactor > Rename
    // Package handles this safely across the whole project.
    namespace = "com.venunair.warden"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.venunair.warden"
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
            isMinifyEnabled = false
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
        }
    }

    // Added 2026-09-01, beta distribution: AGP runs a "vital" lint pass
    // (lintVitalAnalyzeRelease) automatically before every release build,
    // which exists to block Play Store submissions with fatal lint issues.
    // We're not submitting to the Play Store from this machine, and on
    // Windows this task has been failing outright with a file-lock error
    // on its own lint-cache jar (another process -- Windows Defender, a
    // leftover Gradle daemon, or Android Studio's indexer -- holding the
    // file open), unrelated to anything in this codebase. Disabling it for
    // release builds removes that whole class of flaky Windows build
    // failures; re-enable (or just run `gradlew lint` manually any time)
    // once this is actually headed to the Play Store, where it's worth
    // having back.
    lint {
        checkReleaseBuilds = false
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
}
