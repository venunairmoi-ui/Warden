package com.venunair.wisma.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun fromEpochDay(value: Long?): LocalDate? = value?.let { LocalDate.ofEpochDay(it) }
    @TypeConverter fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter fun fromEpochMilli(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
    @TypeConverter fun instantToEpochMilli(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter fun fromCategoryName(value: String?): ItemCategory? = value?.let { ItemCategory.valueOf(it) }
    @TypeConverter fun categoryToName(category: ItemCategory?): String? = category?.name

    @TypeConverter fun fromStatusName(value: String?): ItemStatus? = value?.let { ItemStatus.valueOf(it) }
    @TypeConverter fun statusToName(status: ItemStatus?): String? = status?.name

    @TypeConverter fun fromMimeTypeName(value: String?): AttachmentMimeType? =
        value?.let { AttachmentMimeType.valueOf(it) }
    @TypeConverter fun mimeTypeToName(mimeType: AttachmentMimeType?): String? = mimeType?.name

    @TypeConverter fun fromSourceName(value: String?): AttachmentSource? =
        value?.let { AttachmentSource.valueOf(it) }
    @TypeConverter fun sourceToName(source: AttachmentSource?): String? = source?.name

    // Sprint 6: new enum converters

    @TypeConverter fun fromItemTypeName(value: String?): ItemType? = value?.let { ItemType.valueOf(it) }
    @TypeConverter fun itemTypeToName(itemType: ItemType?): String? = itemType?.name

    @TypeConverter fun fromBillingCycleName(value: String?): BillingCycle? = value?.let { BillingCycle.valueOf(it) }
    @TypeConverter fun billingCycleToName(cycle: BillingCycle?): String? = cycle?.name

    // Category-specific fields pass: Warranty-only enum, same TEXT-affinity
    // pattern as every other enum above.
    @TypeConverter fun fromWarrantyTypeName(value: String?): WarrantyType? = value?.let { WarrantyType.valueOf(it) }
    @TypeConverter fun warrantyTypeToName(warrantyType: WarrantyType?): String? = warrantyType?.name
}
