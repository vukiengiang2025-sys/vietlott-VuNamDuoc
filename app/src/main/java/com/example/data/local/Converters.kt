package com.example.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromIntList(numbers: List<Int>?): String {
        return numbers?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toIntList(data: String?): List<Int> {
        if (data.isNullOrBlank()) return emptyList()
        return data.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
    }
}
