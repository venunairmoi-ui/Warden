import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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
        versionCode = 35
        versionName = "0.13.1-service-receipts"

        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
