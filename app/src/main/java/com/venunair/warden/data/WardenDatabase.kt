package com.venunair.warden.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Item::class, Attachment::class, ReminderRule::class, ServiceEvent::class],
    // 1 -> 2: Item.amcNumber. 2 -> 3: ReminderRule.lastFiredDate/snoozedUntil
    // (Sprint 5). 3 -> 4: Attachment.thumbnailUri (Sprint 2). No real
    // Migrations written for any of these — see fallbackToDestructiveMigration()
    // below for why that's fine right now but must change before anyone
    // else's data is on the line.
    version = 4,
    // false for now — schema export matters once you're writing real Room
    // Migrations against user data (Sprint 7+ territory). Flip to true and
    // apply the Room Gradle plugin with a schemaLocation if/when you get
    // there; forcing it on now just produces a KSP warning for no benefit.
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WardenDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun reminderRuleDao(): ReminderRuleDao
    abstract fun serviceEventDao(): ServiceEventDao

    companion object {
        @Volatile private var INSTANCE: WardenDatabase? = null

        fun getInstance(context: Context): WardenDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WardenDatabase::class.java,
                    "warden.db"
                )
                    // TEMPORARY, dev-time only: the schema is still moving
                    // sprint to sprint (this file's version 1->2 bump is the
                    // second example) and the only data on the line right now
                    // is your own test items. Wiping and recreating on a
                    // version mismatch is the right tradeoff until real user
                    // data exists — at that point this MUST be replaced with
                    // an actual Migration(from, to) or upgraders will lose
                    // their tracked warranties/AMCs, which is precisely the
                    // failure mode this app exists to prevent. Flag this line
                    // again before Sprint 7 / Play Store prep.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
