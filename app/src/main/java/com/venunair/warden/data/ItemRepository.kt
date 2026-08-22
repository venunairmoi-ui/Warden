package com.venunair.warden.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Single entry point the UI layer talks to. Sprint 1 scope is CRUD only;
 * Sprints 4-6 extend this with OCR-driven pre-fill and auto-detect ingestion
 * without the UI layer needing to change its dependency on this class.
 */
class ItemRepository(
    private val itemDao: ItemDao,
    private val attachmentDao: AttachmentDao,
    private val reminderRuleDao: ReminderRuleDao,
    private val serviceEventDao: ServiceEventDao
) {
    fun observeActiveItems(): Flow<List<Item>> = itemDao.observeActiveItems()

    fun observeItem(id: Long): Flow<Item?> = itemDao.observeItem(id)

    /** One-shot read for callers outside Compose (WorkManager workers) that can't collect a Flow. */
    suspend fun getItem(id: Long): Item? = itemDao.observeItem(id).first()

    fun observeAttachments(itemId: Long): Flow<List<Attachment>> = attachmentDao.observeForItem(itemId)

    /** One-shot read, mirroring getItem() above -- needed by a delete flow
     *  that has to know an item's attachments BEFORE the delete cascades
     *  (Room's ON DELETE CASCADE removes the attachment rows, but not their
     *  backing files on disk; the caller needs the list first to clean
     *  those up via capture.AttachmentStorage.deleteBackingFile). */
    suspend fun getAttachments(itemId: Long): List<Attachment> = attachmentDao.observeForItem(itemId).first()

    fun observeServiceHistory(itemId: Long): Flow<List<ServiceEvent>> = serviceEventDao.observeForItem(itemId)

    /**
     * Reminder rules are seeded only on first insert (item.id == 0). On an
     * EDIT to an existing item, its rules are reset (lastFiredDate/
     * snoozedUntil cleared) rather than left untouched — a fired-state only
     * means something relative to the expiry date it fired against, and
     * that date may have just changed. Also doubles as the escape hatch if
     * a rule ever gets stuck marked "fired" with no notification ever
     * having been seen (e.g. notifications were denied at the time) — open
     * the item, hit Save, and its reminders are live again.
     *
     * insert vs. update, deliberately NOT itemDao.upsert(REPLACE): REPLACE
     * deletes-then-reinserts on a primary-key conflict, which cascades
     * through ReminderRule's FK and silently deletes every rule for the
     * item on EVERY edit, before resetForItem() below even runs against an
     * already-empty set. That was a real, confirmed bug — every "Edit, hit
     * Save" reminder-reset instruction up to this point silently deleted
     * the reminders instead of resetting them. @Update never deletes the
     * row, so the child rules survive.
     *
     * Repair path: this fix stops FUTURE edits from wiping rules, but it
     * can't undo damage already done by the bug above on THIS device before
     * the fix was installed — an item edited earlier in that window can
     * still have zero rows in reminder_rules right now, permanently, with
     * nothing left to reset. resetForItem() returning 0 rows affected is
     * exactly that signal, so re-seed defaults in that case instead of
     * silently leaving the item reminder-less forever.
     */
    suspend fun saveItem(item: Item, reminderOffsets: List<Int> = DEFAULT_REMINDER_OFFSETS): Long {
        val isNew = item.id == 0L
        val id = if (isNew) {
            itemDao.insert(item)
        } else {
            itemDao.update(item)
            item.id
        }
        if (isNew) {
            reminderRuleDao.insertAll(reminderOffsets.map { ReminderRule(itemId = id, daysBeforeExpiry = it) })
        } else {
            val resetCount = reminderRuleDao.resetForItem(id)
            if (resetCount == 0) {
                reminderRuleDao.insertAll(reminderOffsets.map { ReminderRule(itemId = id, daysBeforeExpiry = it) })
            }
        }
        return id
    }

    /** Cascades to that item's attachments and reminder rules in the
     *  database (Attachment/ReminderRule both have ON DELETE CASCADE FKs
     *  on itemId) -- but NOT to attachment backing files on disk, which
     *  Room has no knowledge of. Callers combine this with
     *  getAttachments(item.id) beforehand + AttachmentStorage.deleteBackingFile
     *  per attachment, same division of responsibility as deleteAttachment(). */
    suspend fun deleteItem(item: Item) = itemDao.delete(item)

    suspend fun archiveItem(id: Long) = itemDao.setStatus(id, ItemStatus.ARCHIVED)

    /** Called from the "Mark serviced/renewed" notification action and the Detail screen button. */
    suspend fun markServiced(itemId: Long, newExpiry: LocalDate, note: String? = null) {
        serviceEventDao.insert(ServiceEvent(itemId = itemId, date = LocalDate.now(), note = note))
        itemDao.updateExpiry(itemId, newExpiry)
        // New expiry -> every rule's countdown restarts, otherwise a rule
        // that already fired against the OLD expiry stays silently spent
        // forever against the new one. Same repair-path reasoning as
        // saveItem() above: 0 rows reset means this item has no rules left
        // at all (a casualty of the now-fixed cascade-delete bug), so
        // re-seed defaults rather than leaving it permanently silent.
        val resetCount = reminderRuleDao.resetForItem(itemId)
        if (resetCount == 0) {
            reminderRuleDao.insertAll(DEFAULT_REMINDER_OFFSETS.map { ReminderRule(itemId = itemId, daysBeforeExpiry = it) })
        }
    }

    suspend fun addAttachment(attachment: Attachment): Long = attachmentDao.insert(attachment)

    /**
     * DB row only -- deleting the backing file(s) needs a Context (to reach
     * ContentResolver for the FileProvider content:// Uri), which this
     * repository deliberately never holds a reference to. Callers combine
     * this with capture.AttachmentStorage.deleteBackingFile(context, ...)
     * for both localFileUri and thumbnailUri.
     */
    suspend fun deleteAttachment(attachment: Attachment) = attachmentDao.delete(attachment)

    /**
     * Sprint 5: what ReminderCheckWorker iterates daily. A plain in-memory
     * join (fetch active items + all enabled rules, group by itemId) rather
     * than a Room @Relation query — simplest thing that works at the scale
     * of one person's tracked items, and avoids introducing a new POJO/
     * @Relation just for this.
     */
    suspend fun getActiveItemsWithEnabledRules(): List<Pair<Item, List<ReminderRule>>> {
        val items = itemDao.observeActiveItems().first()
        val rulesByItem = reminderRuleDao.getAllEnabled().groupBy { it.itemId }
        return items.map { item -> item to (rulesByItem[item.id].orEmpty()) }
    }

    suspend fun getRule(ruleId: Long): ReminderRule? = reminderRuleDao.getRule(ruleId)

    suspend fun markRuleFired(rule: ReminderRule, firedOn: LocalDate) {
        reminderRuleDao.update(rule.copy(lastFiredDate = firedOn))
    }

    suspend fun snoozeRule(ruleId: Long, until: LocalDate) {
        reminderRuleDao.getRule(ruleId)?.let { rule ->
            reminderRuleDao.update(rule.copy(lastFiredDate = null, snoozedUntil = until))
        }
    }
}
