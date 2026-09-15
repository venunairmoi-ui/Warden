package com.venunair.warden.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.venunair.warden.MainActivity
import com.venunair.warden.R
import com.venunair.warden.data.Item
import com.venunair.warden.data.ReminderRule

object NotificationHelper {
    // v2, not "warranty_reminders": NotificationChannel importance can only
    // be set at CREATION — Android deliberately ignores importance changes
    // on an already-existing channel ID (protects a user's own customized
    // settings from being silently overridden). Bumping DEFAULT -> HIGH
    // below requires a fresh channel ID to actually take effect for anyone
    // who already ran an earlier build, this device included.
    const val CHANNEL_ID = "warranty_reminders_v2"
    const val DIGEST_CHANNEL_ID = "weekly_digest"
    const val AUTO_DETECT_CHANNEL_ID = "auto_detect_receipts"
    const val ACTION_MARK_SERVICED = "com.venunair.warden.action.MARK_SERVICED"
    const val ACTION_SNOOZE = "com.venunair.warden.action.SNOOZE"
    const val EXTRA_ITEM_ID = "extra_item_id"
    const val EXTRA_RULE_ID = "extra_rule_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val DIGEST_NOTIFICATION_ID = 99999
    // Auto-detect notification IDs are derived per-image (see
    // AutoDetectWorker) but all fall in this offset range, well clear of
    // both real item IDs (used as reminder notification IDs) and
    // DIGEST_NOTIFICATION_ID, so none of the three can ever collide.
    const val AUTO_DETECT_NOTIFICATION_ID_BASE = 900_000_000
    // AMC service-visit tracking, 2026-08-26: offset so a "service may be
    // due" nudge (showServiceDueReminder) never collides with that same
    // item's own expiry-based notification id (item.id.toInt(), used by
    // showReminder above). Safely clear of AUTO_DETECT's 900M+ range as
    // long as item ids stay well under 100M, which they will for a
    // single-user local database.
    const val SERVICE_DUE_NOTIFICATION_ID_OFFSET = 800_000_000

    /**
     * minSdk is already 26 (O), the same level NotificationChannel was
     * introduced on, so no Build.VERSION.SDK_INT guard is needed — every
     * device this app runs on supports channels.
     *
     * IMPORTANCE_HIGH (not DEFAULT): this app's whole point is a reminder
     * you don't miss — DEFAULT only lands quietly in the shade, HIGH is
     * what actually gets a heads-up popup + sound. New channel also gets
     * the platform's default notification sound automatically; no extra
     * setSound() call needed.
     */
    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Warranty & AMC reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a tracked warranty, AMC, or subscription is due soon."
            }
        )
        // Sprint 8: weekly digest — default importance so it doesn't
        // heads-up every Monday, just sits in the shade.
        nm.createNotificationChannel(
            NotificationChannel(
                DIGEST_CHANNEL_ID,
                "Weekly digest",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A weekly summary of upcoming expirations and renewals."
            }
        )
        // Sprint 9: auto-detect suggestions — LOW, not DEFAULT/HIGH. This is
        // a "we noticed something, want to add it?" suggestion the user
        // opted into, not a time-sensitive alert; it should sit quietly in
        // the shade with no heads-up popup or sound.
        nm.createNotificationChannel(
            NotificationChannel(
                AUTO_DETECT_CHANNEL_ID,
                "Receipt suggestions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Suggests adding a photo that looks like a receipt or warranty card."
            }
        )
    }

    /**
     * Returns whether a notification was actually posted — see the caller
     * in ReminderCheckWorker for why that matters.
     * Sprint 8: [smartSuffix] appends intelligent context (e.g. repair
     * cost warning) to the notification body.
     */
    fun showReminder(context: Context, item: Item, rule: ReminderRule, daysLeft: Long, smartSuffix: String? = null): Boolean {
        // POST_NOTIFICATIONS (API 33+) may have been denied — notify() would
        // otherwise silently no-op (or trip a lint @RequiresPermission
        // check); bail explicitly so this reads as an intentional guard.
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return false

        // One notification per ITEM, not per rule — if two rules for the
        // same item both become due in the same daily check (unlikely given
        // the default 30/7/1 day spacing, but possible with hand-edited
        // rules), the later one replaces the earlier rather than stacking.
        // Acceptable trade-off for v1; revisit if that turns out to matter.
        val notificationId = item.id.toInt()

        val timeText = when {
            daysLeft < 0 -> "Overdue by ${-daysLeft} day${if (-daysLeft == 1L) "" else "s"}"
            daysLeft == 0L -> "Due today"
            else -> "Due in $daysLeft day${if (daysLeft == 1L) "" else "s"}"
        }
        val body = if (smartSuffix != null) "$timeText\n$smartSuffix" else timeText

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_DEEP_LINK_ITEM_ID, item.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // Proper alpha-only bell silhouette — see ic_notification.xml
            // for why the previous system placeholder rendered as an
            // illegible white blob instead of a recognizable icon.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.name)
            .setContentText(body)
            // Long item names or bodies get truncated to one line without
            // this — BigTextStyle lets the shade expand to show it in full,
            // which is the other half of "hard to read" on a long name.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            // Accent color for the small-icon circle / header tint on
            // launchers that colorize by it (stock Android 8+, and several
            // OEM skins) — the Vault Ledger branding pass's vault-green
            // brand accent (WardenPrimaryContainerDark in
            // ui/theme/Color.kt, 2026-09-15; was the Wisma redesign's
            // electric blue), so the notification doesn't fall back to a
            // low-contrast default.
            .setColor(0xFF2F5D50.toInt())
            // Matches the channel's IMPORTANCE_HIGH. On API 26+ (this app's
            // minSdk) the channel is what actually governs heads-up/sound
            // behavior — this is only a fallback for pre-channel Android,
            // kept consistent rather than left mismatched.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            // Two action buttons share one row of limited width; longer
            // labels ("Mark serviced" / "Snooze 7 days") were reported
            // clipped on OxygenOS. Shorter labels here don't change what
            // fires -- ReminderActionReceiver keys off the Intent action
            // string (ACTION_MARK_SERVICED / ACTION_SNOOZE), never the
            // visible button text.
            .addAction(0, "Serviced", actionPendingIntent(context, ACTION_MARK_SERVICED, item.id, rule.id, notificationId))
            .addAction(0, "Snooze 7d", actionPendingIntent(context, ACTION_SNOOZE, item.id, rule.id, notificationId))
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }

    /**
     * AMC-only, feedback 2026-08-26: "notify the user when a service is
     * due, or inform them that a service may be due... so they can take
     * appropriate action". Distinct from [showReminder] -- this fires off
     * the computed next-expected-service date (see computeAmcServiceStatus),
     * not a days-before-expiry ReminderRule, and deliberately uses
     * PRIORITY_DEFAULT rather than HIGH: it's a "may be due" estimate
     * based on evenly-spreading the contract's visit count, not a firm
     * deadline the vendor promised, so it shouldn't heads-up as urgently
     * as an actual expiry warning.
     */
    fun showServiceDueReminder(context: Context, item: Item, remaining: Int): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return false

        val notificationId = SERVICE_DUE_NOTIFICATION_ID_OFFSET + item.id.toInt()
        val body = "You may be due for a service — $remaining service${if (remaining != 1) "s" else ""} left this AMC period"

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_DEEP_LINK_ITEM_ID, item.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.name)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(0xFF2F5D50.toInt())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }

    /**
     * Sprint 8: Weekly digest notification — a single summary of items
     * expiring within the next 30 days and active subscriptions.
     */
    fun showDigest(context: Context, expiringCount: Int, subscriptionCount: Int, totalAtRisk: String): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return false

        if (expiringCount == 0 && subscriptionCount == 0) return false

        val title = "Weekly summary"
        val lines = mutableListOf<String>()
        if (expiringCount > 0) {
            lines.add("$expiringCount item${if (expiringCount != 1) "s" else ""} expiring soon ($totalAtRisk at risk)")
        }
        if (subscriptionCount > 0) {
            lines.add("$subscriptionCount active subscription${if (subscriptionCount != 1) "s" else ""}")
        }
        val body = lines.joinToString("\n")

        val contentIntent = PendingIntent.getActivity(
            context,
            DIGEST_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DIGEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(0xFF2F5D50.toInt())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(DIGEST_NOTIFICATION_ID, notification)
        return true
    }

    /**
     * Sprint 9: auto-detect — "this photo looks like a receipt/warranty
     * card, want to add it?" Tapping opens MainActivity via the same
     * EXTRA_SHARE_* hand-off ShareReceiverActivity already uses (see
     * MainActivity.updatePendingShare / WardenNavHost's PendingShare), with
     * source = "AUTO_DETECT" so AddEditItemScreen records the attachment's
     * provenance correctly (AttachmentSource.AUTO_DETECT) instead of
     * SHARE. [notificationId] is derived per-image by the caller — see
     * AUTO_DETECT_NOTIFICATION_ID_BASE — so multiple detections stack
     * instead of one replacing another.
     */
    fun showAutoDetectSuggestion(context: Context, imageUri: String, notificationId: Int): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return false

        // POST_NOTIFICATIONS being granted is only the app-level switch --
        // a user (or an OEM's own notification management) can separately
        // disable just THIS channel while every other Warden notification
        // keeps working fine, from Settings > Apps > Warden > Notifications.
        // NotificationManagerCompat.notify() doesn't throw or signal this in
        // any way when that happens -- it silently no-ops -- so without this
        // check, this function's return value could report "posted" for a
        // notification nobody will ever see.
        val channelDisabled = NotificationManagerCompat.from(context)
            .getNotificationChannel(AUTO_DETECT_CHANNEL_ID)
            ?.importance == NotificationManager.IMPORTANCE_NONE
        if (channelDisabled) return false

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_SHARE_URI, imageUri)
                putExtra(MainActivity.EXTRA_SHARE_MIME_TYPE, "IMAGE")
                putExtra(MainActivity.EXTRA_SHARE_SOURCE, "AUTO_DETECT")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AUTO_DETECT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Looks like a receipt")
            .setContentText("Add it to Wisma?")
            .setColor(0xFF2F5D50.toInt())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }

    private fun actionPendingIntent(
        context: Context,
        action: String,
        itemId: Long,
        ruleId: Long,
        notificationId: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_RULE_ID, ruleId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        // Distinct request codes per (item, action) so the Mark-serviced and
        // Snooze PendingIntents for the same notification don't collide —
        // FLAG_UPDATE_CURRENT keys off the request code, and both actions
        // share the same underlying Intent shape otherwise.
        val requestCode = notificationId * 2 + if (action == ACTION_MARK_SERVICED) 0 else 1
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
