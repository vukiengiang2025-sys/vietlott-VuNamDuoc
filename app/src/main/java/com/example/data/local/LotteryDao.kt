package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.LotteryDraw
import kotlinx.coroutines.flow.Flow

@Dao
interface LotteryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraws(draws: List<LotteryDraw>)

    @Query("SELECT * FROM lottery_draws WHERE lotteryType = :type ORDER BY drawDate DESC, drawId DESC")
    fun getDrawsByType(type: String): Flow<List<LotteryDraw>>

    @Query("SELECT * FROM lottery_draws WHERE lotteryType = :type ORDER BY drawDate ASC, drawId ASC")
    suspend fun getDrawsByTypeAscending(type: String): List<LotteryDraw>

    @Query("SELECT COUNT(*) FROM lottery_draws WHERE lotteryType = :type")
    fun getDrawCount(type: String): Flow<Int>

    @Query("SELECT * FROM lottery_draws WHERE lotteryType = :type ORDER BY drawDate DESC, drawId DESC LIMIT 1")
    fun getLatestDraw(type: String): Flow<LotteryDraw?>

    @Query("DELETE FROM lottery_draws WHERE lotteryType = :type")
    suspend fun deleteDrawsByType(type: String)

    @Query("DELETE FROM lottery_draws")
    suspend fun clearAll()
}
