package com.venunair.warden.data

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
}
