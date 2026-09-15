package com.venunair.wisma.data

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
        // AMC service-visit tracking, 2026-08-26: only AMC items carry
        // currentPeriodStart/serviceIntervalMonths -- see applyAmcPeriodTracking.
        val toSave = if (item.category == ItemCategory.AMC) applyAmcPeriodTracking(item, isNew) else item
        val id = if (isNew) {
            itemDao.insert(toSave)
        } else {
            itemDao.update(toSave)
            toSave.id
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

    /**
     * AMC-only period bookkeeping, run on every save (feedback, 2026-08-26).
     *
     * New item: seeds currentPeriodStart (purchaseDate, else today) and
     * auto-fills serviceIntervalMonths if the caller left it blank -- same
     * blank-fields-only convention OCR auto-fill uses elsewhere, since
     * serviceIntervalMonths is explicitly user-configurable afterward.
     *
     * Existing item whose expiryDate was just extended: that's a renewal
     * ("reset automatically" was the requested behaviour over a confirm-
     * first prompt) -- currentPeriodStart moves to today, which IS the
     * entire reset (computeAmcServiceStatus counts ServiceEvents from
     * there, so nothing else needs clearing). A blank serviceIntervalMonths
     * is re-seeded against the new period; one the user already set is
     * left untouched.
     *
     * Existing item, no renewal: currentPeriodStart carries over from the
     * stored row unchanged -- it's internal/derived, never threaded from
     * the Add/Edit form, so without this it would silently revert to null
     * (the Item constructor's default) on every ordinary edit.
     */
    private suspend fun applyAmcPeriodTracking(item: Item, isNew: Boolean): Item {
        val periodStart: LocalDate?
        val isRenewal: Boolean
        if (isNew) {
            periodStart = item.purchaseDate ?: LocalDate.now()
            isRenewal = false
        } else {
            val existing = itemDao.observeItem(item.id).first() ?: return item
            isRenewal = item.expiryDate.isAfter(existing.expiryDate)
            periodStart = if (isRenewal) LocalDate.now() else existing.currentPeriodStart
        }
        val interval = item.serviceIntervalMonths ?: run {
            if (isNew || isRenewal) {
                val basis = periodStart ?: LocalDate.now()
                item.visitsIncluded?.let { defaultServiceIntervalMonths(basis, item.expiryDate, it) }
            } else {
                null
            }
        }
        return item.copy(currentPeriodStart = periodStart, serviceIntervalMonths = interval)
    }

    /** Cascades to that item's attachments and reminder rules in the
     *  database (Attachment/ReminderRule both have ON DELETE CASCADE FKs
     *  on itemId) -- but NOT to attachment backing files on disk, which
     *  Room has no knowledge of. Callers combine this with
     *  getAttachments(item.id) beforehand + AttachmentStorage.deleteBackingFile
     *  per attachment, same division of responsibility as deleteAttachment(). */
    suspend fun deleteItem(item: Item) = itemDao.delete(item)

    suspend fun archiveItem(id: Long) = itemDao.archive(id, LocalDate.now())

    // Sprint 8: undo for swipe-to-archive (the 5-second snackbar). Also the
    // Restore action on the Archived items recovery screen (2026-08-26) --
    // same call either way, since restoring IS undoing an archive.
    suspend fun unarchiveItem(id: Long) = itemDao.unarchive(id)

    /** Feedback, 2026-08-26: powers the Archived items recovery screen. */
    fun observeArchivedItems(): Flow<List<Item>> = itemDao.observeArchivedItems()

    /** Called from the "Mark serviced/renewed" notification action and the Detail screen button. */
    // Feedback, 2026-08-25: serviceDate now caller-supplied (defaults to
    // today, same as before) -- ItemDetailScreen's "Mark serviced" dialog
    // added a date picker for it instead of the event always being logged
    // as happening today, which was wrong for a user recording a service
    // after the fact.
    suspend fun markServiced(itemId: Long, newExpiry: LocalDate, serviceDate: LocalDate = LocalDate.now(), note: String? = null) {
        serviceEventDao.insert(ServiceEvent(itemId = itemId, date = serviceDate, note = note))
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

    /**
     * AMC-only: logs a service visit WITHOUT touching expiryDate or
     * currentPeriodStart -- deliberately separate from markServiced(),
     * which is a renewal action. Feedback, 2026-08-26: "a counter to
     * record the number of services" is exactly this -- a plain
     * ServiceEvent, counted against the current period by
     * computeAmcServiceStatus. Reminder rules are untouched too: expiry
     * didn't change, so the expiry countdown shouldn't restart.
     *
     * Returns the new ServiceEvent's id (feedback follow-up, 2026-08-26:
     * "allow attaching the vendor-provided receipt" -- the caller needs
     * this id to link an Attachment.serviceEventId to the visit just
     * logged, since the receipt can only be inserted once the row it
     * attaches to actually exists).
     */
    suspend fun logAmcService(itemId: Long, serviceDate: LocalDate, note: String? = null, cost: Double? = null): Long =
        serviceEventDao.insert(ServiceEvent(itemId = itemId, date = serviceDate, note = note, cost = cost))

    /** AMC service-visit tracking follow-up, 2026-08-26 -- see Attachment.serviceEventId's doc comment. */
    fun observeAttachmentsForServiceEvent(serviceEventId: Long): Flow<List<Attachment>> =
        attachmentDao.observeForServiceEvent(serviceEventId)

    /** AMC service-visit tracking, 2026-08-26 -- see Item.serviceDueNotifiedForDate. */
    suspend fun markServiceDueNotified(itemId: Long, forDate: LocalDate) {
        itemDao.updateServiceDueNotifiedForDate(itemId, forDate)
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

    // ── Sprint 8: Search ────────────────────────────────────────────

    /** Combined search across item name/vendor and attachment OCR text. */
    suspend fun searchItems(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val trimmed = query.trim()

        // 1. Items matching by name or vendor
        val nameMatches = itemDao.searchByNameOrVendor(trimmed)
        val nameMatchIds = nameMatches.map { it.id }.toSet()

        // 2. Items matching by OCR text in their attachments
        val ocrItemIds = attachmentDao.searchItemIdsByOcrText(trimmed)
        val ocrOnlyIds = ocrItemIds.filter { it !in nameMatchIds }
        val ocrItems = if (ocrOnlyIds.isNotEmpty()) itemDao.getByIds(ocrOnlyIds) else emptyList()

        // Build results — name/vendor matches first, then OCR-only matches
        val results = mutableListOf<SearchResult>()
        for (item in nameMatches) {
            val hasOcr = item.id in ocrItemIds
            val ocrSnippet = if (hasOcr) attachmentDao.getOcrSnippetForItem(item.id, trimmed) else null
            val thumbnail = if (hasOcr) attachmentDao.getFirstThumbnailForItem(item.id) else null
            results.add(SearchResult(item, MatchType.NAME_OR_VENDOR, ocrSnippet?.extractSnippet(trimmed), thumbnail))
        }
        for (item in ocrItems) {
            val ocrSnippet = attachmentDao.getOcrSnippetForItem(item.id, trimmed)
            val thumbnail = attachmentDao.getFirstThumbnailForItem(item.id)
            results.add(SearchResult(item, MatchType.OCR_TEXT, ocrSnippet?.extractSnippet(trimmed), thumbnail))
        }
        return results
    }

    /** Sprint 8: get reminder offsets for the edit form. */
    suspend fun getReminderOffsetsForItem(itemId: Long): List<Int> =
        reminderRuleDao.getForItem(itemId).map { it.daysBeforeExpiry }

    /** Sprint 8: replace all reminder rules for an item with new offsets. */
    suspend fun replaceReminderRules(itemId: Long, offsets: List<Int>) {
        reminderRuleDao.deleteForItem(itemId)
        if (offsets.isNotEmpty()) {
            reminderRuleDao.insertAll(offsets.map { ReminderRule(itemId = itemId, daysBeforeExpiry = it) })
        }
    }

    /** Sprint 8: total repair/service cost for an item (used by smart notifications). */
    suspend fun getServiceCostForItem(itemId: Long): Double =
        serviceEventDao.getTotalCostForItem(itemId)

    /**
     * Phase 2 (privacy): "Delete all my data" on the Privacy screen. DB
     * rows only -- callers fetch getAllAttachmentsForDeletion() first, call
     * this, then clean up each attachment's backing file via
     * capture.AttachmentStorage.deleteBackingFile(context, ...), same
     * context-free division of responsibility as deleteItem()/
     * deleteAttachment() above. Deliberately scoped to user content
     * (items, attachments, reminder rules, service events -- everything
     * cascades off `items` via their ON DELETE CASCADE FKs) and NOT to
     * SettingsRepository's DataStore prefs (theme/region/reminder
     * defaults): those are app configuration, not personal data.
     */
    suspend fun getAllAttachmentsForDeletion(): List<Attachment> = attachmentDao.getAll()

    suspend fun deleteAllItems() = itemDao.deleteAll()
}

// ── Sprint 8: Search result types ──────────────────────────────────

enum class MatchType { NAME_OR_VENDOR, OCR_TEXT }

data class SearchResult(
    val item: Item,
    val matchType: MatchType,
    val ocrSnippet: String? = null,
    val attachmentThumbnail: String? = null
)

/** Extract a ~80-char window around the first occurrence of [query] in this text. */
private fun String.extractSnippet(query: String, windowSize: Int = 80): String {
    val lowerThis = this.lowercase()
    val lowerQuery = query.lowercase()
    val idx = lowerThis.indexOf(lowerQuery)
    if (idx < 0) return this.take(windowSize)
    val start = (idx - windowSize / 2).coerceAtLeast(0)
    val end = (idx + query.length + windowSize / 2).coerceAtMost(this.length)
    val snippet = this.substring(start, end).replace('\n', ' ')
    return (if (start > 0) "…" else "") + snippet + (if (end < this.length) "…" else "")
}
