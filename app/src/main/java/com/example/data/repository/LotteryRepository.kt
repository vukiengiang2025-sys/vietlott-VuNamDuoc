package com.example.data.repository

import android.content.Context
import com.example.data.local.LotteryDao
import com.example.data.model.LotteryDraw
import com.example.data.model.LotteryType
import com.example.data.parser.JsonlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

class LotteryRepository(
    private val lotteryDao: LotteryDao,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    fun getDraws(type: LotteryType): Flow<List<LotteryDraw>> {
        return lotteryDao.getDrawsByType(type.code)
    }

    fun getDrawCount(type: LotteryType): Flow<Int> {
        return lotteryDao.getDrawCount(type.code)
    }

    fun getLatestDraw(type: LotteryType): Flow<LotteryDraw?> {
        return lotteryDao.getLatestDraw(type.code)
    }

    suspend fun getDrawsAscending(type: LotteryType): List<LotteryDraw> = withContext(Dispatchers.IO) {
        lotteryDao.getDrawsByTypeAscending(type.code)
    }

    suspend fun ensureInitialDataLoaded(context: Context) = withContext(Dispatchers.IO) {
        for (type in LotteryType.entries) {
            val existing = lotteryDao.getDrawsByTypeAscending(type.code)
            // If empty or less than 1000 draws (from previous sample), load the full historical dataset
            if (existing.size < 1000) {
                val assetName = if (type == LotteryType.MEGA_645) {
                    "power645.jsonl"
                } else {
                    "power655.jsonl"
                }
                try {
                    val stream: InputStream = context.assets.open(assetName)
                    val content = stream.bufferedReader().use { it.readText() }
                    val parsed = JsonlParser.parse(content, type)
                    if (parsed.isNotEmpty()) {
                        lotteryDao.insertDraws(parsed)
                    }
                } catch (_: Exception) {
                    // Asset file might be missing or failed, ignore
                }
            }
        }
    }

    suspend fun downloadFromGithub(
        lotteryType: LotteryType,
        customUrl: String? = null,
        onProgress: ((String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            onProgress?.invoke("Đang kết nối tới máy chủ GitHub...")
            var targetUrl = customUrl?.trim()
            if (targetUrl.isNullOrEmpty()) {
                targetUrl = lotteryType.rawGithubUrl
            } else {
                // If user entered a GitHub blob URL, transform to raw.githubusercontent.com
                if (targetUrl.contains("github.com") && targetUrl.contains("/blob/")) {
                    targetUrl = targetUrl
                        .replace("github.com", "raw.githubusercontent.com")
                        .replace("/blob/", "/")
                }
            }

            val request = Request.Builder()
                .url(targetUrl)
                .addHeader("User-Agent", "Vietlott-Analytics-Android")
                .build()

            onProgress?.invoke("Đang tải tệp dữ liệu...")
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: ""
            if (body.isBlank()) {
                return@withContext Result.failure(Exception("Dữ liệu tải về trống"))
            }

            onProgress?.invoke("Đang phân tích các kỳ quay...")
            val parsedDraws = JsonlParser.parse(body, lotteryType)
            if (parsedDraws.isEmpty()) {
                return@withContext Result.failure(Exception("Không tìm thấy kỳ quay hợp lệ trong tệp dữ liệu"))
            }

            onProgress?.invoke("Đang lưu ${parsedDraws.size} kỳ quay vào cơ sở dữ liệu...")
            lotteryDao.insertDraws(parsedDraws)

            Result.success(parsedDraws.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromText(text: String, lotteryType: LotteryType): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val parsed = JsonlParser.parse(text, lotteryType)
            if (parsed.isEmpty()) {
                return@withContext Result.failure(Exception("Không tìm thấy kỳ quay hợp lệ trong nội dung đã nhập"))
            }
            lotteryDao.insertDraws(parsed)
            Result.success(parsed.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearDraws(lotteryType: LotteryType) = withContext(Dispatchers.IO) {
        lotteryDao.deleteDrawsByType(lotteryType.code)
    }

    suspend fun resetToSampleData(context: Context, lotteryType: LotteryType): Result<Int> = withContext(Dispatchers.IO) {
        try {
            lotteryDao.deleteDrawsByType(lotteryType.code)
            val assetName = if (lotteryType == LotteryType.MEGA_645) {
                "power645.jsonl"
            } else {
                "power655.jsonl"
            }
            val stream = context.assets.open(assetName)
            val content = stream.bufferedReader().use { it.readText() }
            val parsed = JsonlParser.parse(content, lotteryType)
            lotteryDao.insertDraws(parsed)
            Result.success(parsed.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
