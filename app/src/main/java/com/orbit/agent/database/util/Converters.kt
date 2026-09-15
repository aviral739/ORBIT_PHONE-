package com.orbit.agent.database.util

import androidx.room.TypeConverter

/** Room type converters — extend as new value types are introduced. */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String? =
        value?.joinToString(separator = "|")

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        value?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
}
