package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lottery_draws",
    indices = [
        Index(value = ["lotteryType", "drawId"], unique = true),
        Index(value = ["lotteryType", "drawDate"])
    ]
)
data class LotteryDraw(
    @PrimaryKey
    val id: String,
    val lotteryType: String,
    val drawId: String,
    val drawDate: String,
    val numbers: List<Int>,
    val bonusNumber: Int? = null,
    val page: Int? = null,
    val processTime: String? = null
)
