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
    version = 5,
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

        fun getInstance(context: Context): WardenDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WardenDatabase::class.java,
                    "warden.db"
                )
                    .addMigrations(MIGRATION_4_5)
                    // Destructive fallback only for pre-v4 databases (dev-era
                    // data before real migrations existed). Any v4+ database
                    // upgrades through the migration chain above.
                    .fallbackToDestructiveMigrationFrom(1, 2, 3)
                    .build().also { INSTANCE = it }
            }
    }
}
