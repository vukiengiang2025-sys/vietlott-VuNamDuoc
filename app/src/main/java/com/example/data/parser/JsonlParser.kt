package com.example.data.parser

import com.example.data.model.LotteryDraw
import com.example.data.model.LotteryType

object JsonlParser {

    private val numbersRegex = Regex(""""(?:result|numbers|sorted_numbers|winning_numbers)"\s*:\s*\[([^\]]+)\]""")
    private val dateRegex = Regex(""""(?:draw_date|date)"\s*:\s*"([^"]+)"""")
    private val idRegex = Regex(""""(?:draw_id|id)"\s*:\s*(?:"([^"]+)"|(\d+))""")
    private val pageRegex = Regex(""""page"\s*:\s*(\d+)""")
    private val processTimeRegex = Regex(""""process_time"\s*:\s*"([^"]+)"""")

    /**
     * Parses JSONL content which may have newlines or concatenated `}{` objects.
     * Pure Kotlin implementation that works in JVM tests without Android framework stubs.
     */
    fun parse(content: String, lotteryType: LotteryType): List<LotteryDraw> {
        val resultList = mutableListOf<LotteryDraw>()
        val seenDrawIds = mutableSetOf<String>()

        if (content.isBlank()) return emptyList()

        // Normalize potential concatenated JSON objects: replace "}{" with "}\n{"
        val normalized = content.replace("}{", "}\n{")
        val lines = normalized.lineSequence()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || !line.startsWith("{")) continue

            try {
                // Extract numbers
                val numMatch = numbersRegex.find(line) ?: continue
                val numsRaw = numMatch.groupValues[1]
                val allNums = numsRaw.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }

                if (allNums.size < 6) continue

                val maxAllowed = lotteryType.totalNumbers
                val first6 = allNums.take(6)
                if (first6.toSet().size != 6) continue
                if (!first6.all { it in 1..maxAllowed }) continue

                val bonusNum = if (lotteryType == LotteryType.POWER_655 && allNums.size >= 7) {
                    val candidate = allNums[6]
                    if (candidate in 1..maxAllowed) candidate else null
                } else null

                val sortedNumbers = first6.sorted()

                // Extract date
                val dateMatch = dateRegex.find(line)
                val drawDate = dateMatch?.groupValues?.get(1)?.trim() ?: "?"

                // Extract drawId
                val idMatch = idRegex.find(line)
                var drawId = idMatch?.let {
                    it.groupValues[1].ifEmpty { it.groupValues[2] }
                }?.trim() ?: ""

                if (drawId.isEmpty() || drawId == "0") {
                    drawId = drawDate.replace("-", "").replace("/", "").take(8)
                }
                if (drawId.isEmpty()) {
                    drawId = "DRAW_${resultList.size + 1}"
                }

                // Deduplicate
                val dedupeKey = "${lotteryType.code}_$drawId"
                if (seenDrawIds.add(dedupeKey)) {
                    val page = pageRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
                    val processTime = processTimeRegex.find(line)?.groupValues?.get(1)

                    resultList.add(
                        LotteryDraw(
                            id = dedupeKey,
                            lotteryType = lotteryType.code,
                            drawId = drawId,
                            drawDate = drawDate,
                            numbers = sortedNumbers,
                            bonusNumber = bonusNum,
                            page = page,
                            processTime = processTime
                        )
                    )
                }
            } catch (_: Exception) {
                // Skip invalid entry
            }
        }

        // Sort ascending by drawId or date for chronological analysis
        return resultList.sortedWith(compareBy({ it.drawDate }, { it.drawId }))
    }
}
