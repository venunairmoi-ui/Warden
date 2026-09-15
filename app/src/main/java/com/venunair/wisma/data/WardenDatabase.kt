package com.venunair.warden.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Item::class, Attachment::class, ReminderRule::class, ServiceEvent::class],
    // 1 -> 2: Item.amcNumber. 2 -> 3: ReminderRule.lastFiredDate/snoozedUntil
    // (Sprint 5). 3 -> 4: Attachment.thumbnailUri (Sprint 2).
    // 4 -> 5: Sprint 6 — data model expansion (see MIGRATION_4_5 below).
    // 5 -> 6: Retaxonomy — Item.subCategory, category enum overhaul (see
    // MIGRATION_5_6 below).
    // 6 -> 7: ItemType reintroduced as Product/Service, Item.visitsIncluded
    // added (see MIGRATION_6_7 below).
    // 7 -> 8: Item.archivedAt added for the Archived items recovery screen
    // (see MIGRATION_7_8 below).
    // 8 -> 9: AMC service-visit tracking -- Item.currentPeriodStart,
    // serviceIntervalMonths, serviceDueNotifiedForDate added (see
    // MIGRATION_8_9 below).
    // 9 -> 10: AMC service-visit tracking follow-up -- Attachment.serviceEventId
    // added so a receipt can be attached when logging a service (see
    // MIGRATION_9_10 below).
    // 10 -> 11: Category-specific fields -- Item.nomineeName (Insurance),
    // serviceProviderContact (AMC), warrantyType (Warranty), planTier
    // (Subscription/Membership), membersCovered (Membership) added (see
    // MIGRATION_10_11 below).
    version = 11,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class WardenDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun reminderRuleDao(): ReminderRuleDao
    abstract fun serviceEventDao(): ServiceEventDao

    companion object {
        @Volatile private var INSTANCE: WardenDatabase? = null

        /**
         * v4 → v5: Sprint 6 data model expansion.
         *
         * Items gain: itemType, serialNumber, modelNumber, retailer,
         * invoiceNumber, location, billingCycle, billingAmount, autoRenew.
         *
         * ServiceEvent gains: cost.
         *
         * All new columns are nullable (or have SQLite defaults) so existing
         * rows upgrade without data loss. itemType defaults to 'PRODUCT'
         * (most existing items are physical products with warranties).
         * autoRenew defaults to 0 (false).
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Item new columns
                db.execSQL("ALTER TABLE items ADD COLUMN itemType TEXT NOT NULL DEFAULT 'PRODUCT'")
                db.execSQL("ALTER TABLE items ADD COLUMN serialNumber TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN modelNumber TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN retailer TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN invoiceNumber TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN location TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN billingCycle TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN billingAmount REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN autoRenew INTEGER NOT NULL DEFAULT 0")

                // ServiceEvent new column
                db.execSQL("ALTER TABLE service_events ADD COLUMN cost REAL DEFAULT NULL")
            }
        }

        /**
         * v5 → v6: Retaxonomy.
         *
         * ItemCategory drops DOCUMENT, VEHICLE, HOME, OFFICE, FINANCIAL and
         * gains INSURANCE, MEMBERSHIP, OTHER (WARRANTY, AMC, SUBSCRIPTION
         * are unchanged names, though AMC's on-screen label changes from
         * "Service Contract" to "AMC" — that's display-only, doesn't touch
         * the stored value). Any existing row whose category is one of the
         * 5 removed values is remapped to OTHER — a real, filterable
         * category, not silently dropped or defaulted into a category it
         * was never actually filed under — so the user can find and
         * manually recategorize each one. The WHERE clause whitelists the
         * 3 survivors rather than blacklisting the 5 removed values,
         * deliberately: any row holding a value that ISN'T one of the 3
         * that carried over unchanged gets caught here, including the
         * removed ones and anything unexpected, rather than only the
         * removed values anyone remembered to list.
         *
         * Item.subCategory is new and nullable — existing rows upgrade
         * with no value, same pattern MIGRATION_4_5 used above.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN subCategory TEXT DEFAULT NULL")
                db.execSQL(
                    "UPDATE items SET category = 'OTHER' " +
                        "WHERE category NOT IN ('WARRANTY', 'AMC', 'SUBSCRIPTION')"
                )
            }
        }

        /**
         * v6 → v7: Product/Service reintroduction.
         *
         * ItemType drops SUBSCRIPTION and SERVICE_CONTRACT, keeps PRODUCT,
         * gains SERVICE. Every existing row's itemType was already one of
         * exactly 3 values written by the now-removed
         * ItemCategory.impliedItemType() (PRODUCT, SUBSCRIPTION, or
         * SERVICE_CONTRACT) -- the first UPDATE maps the two removed
         * values onto SERVICE (an item that was billed or service-shaped
         * stays service-shaped); the second is a whitelist catch-all
         * (mirrors MIGRATION_5_6's pattern) that defaults anything NOT
         * already PRODUCT or SERVICE to PRODUCT, so an unexpected stored
         * value fails safe instead of crashing the enum converter.
         *
         * Item.visitsIncluded is new and nullable, AMC-only in the UI --
         * existing rows (including non-AMC ones) upgrade with no value,
         * same pattern every prior additive migration in this file used.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN visitsIncluded INTEGER DEFAULT NULL")
                db.execSQL(
                    "UPDATE items SET itemType = 'SERVICE' " +
                        "WHERE itemType IN ('SUBSCRIPTION', 'SERVICE_CONTRACT')"
                )
                db.execSQL(
                    "UPDATE items SET itemType = 'PRODUCT' " +
                        "WHERE itemType NOT IN ('PRODUCT', 'SERVICE')"
                )
            }
        }

        /**
         * v7 → v8: Archive recovery.
         *
         * Item.archivedAt is new -- stored as INTEGER (epoch-day), matching
         * how every other LocalDate column here is actually persisted
         * (Converters.localDateToEpochDay), NOT a text date string. Existing
         * ARCHIVED rows have no real archive date on record (the column
         * didn't exist yet when they were archived) -- backfilled to "now"
         * as the least-wrong default. That backfill computes an epoch day
         * from SQLite's UTC clock rather than the device's local calendar
         * day (LocalDate.now() would use), which can be off by one day
         * right around midnight local time; harmless here since this field
         * only ever drives display order on the recovery screen, nothing
         * that feeds a reminder or expiry calculation.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN archivedAt INTEGER DEFAULT NULL")
                db.execSQL(
                    "UPDATE items SET archivedAt = CAST(strftime('%s', 'now') AS INTEGER) / 86400 " +
                        "WHERE status = 'ARCHIVED'"
                )
            }
        }

        /**
         * v8 → v9: AMC service-visit tracking.
         *
         * Feedback, 2026-08-26: "monitor the services and notify the user
         * when a service is due... one or two services remain". This needed
         * an explicit period boundary that didn't exist before -- see
         * Item.currentPeriodStart's doc comment for why the old markServiced()
         * couldn't support it.
         *
         * currentPeriodStart backfills to TODAY's epoch day for every
         * existing AMC item -- deliberately NOT purchaseDate, even though
         * that reads as the more natural "period start" at first glance.
         * An AMC bought years ago has accumulated ServiceEvents across
         * however many past renewal cycles (each old markServiced() call
         * both logged one AND pushed expiryDate forward) -- counting
         * "on/after purchaseDate" would sum ALL of that history as
         * "used this period" the instant this migration runs, e.g.
         * showing "6 of 2 used" for a 3-year-old AMC. Historical
         * ServiceEvents can't be reliably attributed to any period
         * boundary (that's the entire reason this field didn't exist
         * before), so counting starts clean from today rather than
         * guessing -- same "least-wrong default: now" reasoning
         * MIGRATION_7_8 used for archivedAt above.
         *
         * serviceIntervalMonths backfills using the same "period ÷ visit
         * count, evenly spread" rule Add/Edit's own auto-fill uses (see
         * defaultServiceIntervalMonths in AmcServiceTracking.kt) -- only
         * where visitsIncluded is already known; left null otherwise, same
         * pattern every prior additive migration in this file used.
         * serviceDueNotifiedForDate is new, internal-only state and needs no
         * backfill.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN currentPeriodStart INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN serviceIntervalMonths INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN serviceDueNotifiedForDate INTEGER DEFAULT NULL")
                db.execSQL(
                    "UPDATE items SET currentPeriodStart = CAST(strftime('%s','now') AS INTEGER) / 86400 " +
                        "WHERE category = 'AMC'"
                )
                db.execSQL(
                    "UPDATE items SET serviceIntervalMonths = " +
                        "MAX(1, ((expiryDate - currentPeriodStart) / 30) / visitsIncluded) " +
                        "WHERE category = 'AMC' AND visitsIncluded IS NOT NULL AND visitsIncluded > 0"
                )
            }
        }

        /**
         * v9 → v10: AMC service-visit tracking follow-up.
         *
         * Feedback, 2026-08-26: "when logging a service, allow attaching the
         * vendor-provided receipt". Attachment.serviceEventId is new and
         * nullable -- existing attachment rows (all captured the ordinary
         * way, none tied to a service visit) upgrade with no value, same
         * additive pattern every migration in this file uses. The index
         * name matches Room's own default-naming convention for a
         * single-column @Index (index_<table>_<column>), same as
         * index_attachments_itemId already created when this table was
         * first introduced -- keeping that convention here is what lets
         * Room's schema validation recognize this as equivalent to what
         * the entity now declares, rather than flagging a mismatch.
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attachments ADD COLUMN serviceEventId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_serviceEventId ON attachments(serviceEventId)")
            }
        }

        /**
         * v10 → v11: category-specific fields.
         *
         * Feedback, 2026-08-30: "we use the same fields for all the items...
         * each has different features and their respective fields need to
         * be shown". Five new nullable columns, each scoped to exactly one
         * category (see each field's doc comment on [Item]) -- plain ALTER
         * TABLE ADD COLUMN, no new foreign keys, same safe shape as every
         * migration since MIGRATION_9_10's crash-and-fix taught this
         * codebase that lesson. No backfill needed: every existing row
         * simply upgrades with these five as null, which reads correctly
         * as "not recorded yet" for data that was never asked for before.
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN nomineeName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN serviceProviderContact TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN warrantyType TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN planTier TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN membersCovered INTEGER DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): WardenDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WardenDatabase::class.java,
                    "warden.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    // Destructive fallback only for pre-v4 databases (dev-era
                    // data before real migrations existed). Any v4+ database
                    // upgrades through the migration chain above.
                    .fallbackToDestructiveMigrationFrom(1, 2, 3)
                    .build().also { INSTANCE = it }
            }
    }
}
