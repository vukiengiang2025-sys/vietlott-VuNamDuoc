package com.example

import com.example.analytics.StatisticalModelV4
import com.example.data.model.LotteryDraw
import com.example.data.model.LotteryType
import com.example.data.parser.JsonlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticalModelV4Test {

    @Test
    fun testJsonlParser_normalAndConcatenated() {
        val sampleJsonl = """
            {"date":"2017-10-25","id":"00198","result":[12,17,23,25,34,38]}
            {"date":"2017-10-27","id":"00199","result":[4,10,13,21,22,38]}{"date":"2017-10-29","id":"00200","result":[1,5,28,31,44,45]}
        """.trimIndent()

        val parsed = JsonlParser.parse(sampleJsonl, LotteryType.MEGA_645)
        assertEquals(3, parsed.size)
        assertEquals("00198", parsed[0].drawId)
        assertEquals(listOf(12, 17, 23, 25, 34, 38), parsed[0].numbers)
        assertEquals("00199", parsed[1].drawId)
        assertEquals("00200", parsed[2].drawId)
    }

    @Test
    fun testJsonlParser_power655_withBonusBall() {
        val sampleJsonl = """
            {"date":"2017-08-01","id":"00001","result":[5,10,14,23,24,38,35]}
        """.trimIndent()

        val parsed = JsonlParser.parse(sampleJsonl, LotteryType.POWER_655)
        assertEquals(1, parsed.size)
        assertEquals(listOf(5, 10, 14, 23, 24, 38), parsed[0].numbers)
        assertEquals(35, parsed[0].bonusNumber)
    }

    @Test
    fun testStatisticalModelV4_runsAnalysisSuccessfully() {
        val draws = mutableListOf<LotteryDraw>()
        for (i in 1..40) {
            val drawId = String.format("%05d", i)
            val date = "2023-01-" + String.format("%02d", (i % 28) + 1)
            // Generate valid 6 numbers in 1..45
            val nums = ((i % 10 + 1)..(i % 10 + 6)).map { if (it > 45) it - 40 else it }.distinct().sorted()
            val validNums = if (nums.size == 6) nums else listOf(1, 2, 3, 4, 5, 6)
            draws.add(
                LotteryDraw(
                    id = "MEGA_$drawId",
                    lotteryType = LotteryType.MEGA_645.code,
                    drawId = drawId,
                    drawDate = date,
                    numbers = validNums
                )
            )
        }

        val model = StatisticalModelV4(seed = 42L)
        val report = model.analyze(draws, LotteryType.MEGA_645)

        assertNotNull(report)
        assertEquals(40, report!!.totalDraws)
        assertEquals(LotteryType.MEGA_645, report.lotteryType)
        assertEquals(6, report.top6Numbers.size)
        assertTrue(report.top15.size >= 6)
        assertTrue(report.textReport.contains("BÁO CÁO MEGA 6/45"))
        assertTrue(report.csvReport.contains("So,XacSuat"))

        // Verify Binary Logistic Regression outputs & correlations
        val summary = report.summary
        assertEquals(7, summary.logisticFeatures.size)
        assertEquals(4, summary.cPerformances.size)
        assertEquals(7, summary.correlationMatrix.size)

        // Verify Odds ratios > 0
        for (feat in summary.logisticFeatures) {
            assertTrue("Odds ratio must be positive", feat.oddsRatio > 0.0)
            assertTrue("Correlation must be between -1 and 1", feat.correlationTarget in -1.0..1.0)
        }

        // Verify number breakdowns exist and probabilities are valid
        assertTrue(report.numberBreakdowns.isNotEmpty())
        val sampleBreakdown = report.numberBreakdowns[1]
        assertNotNull(sampleBreakdown)
        assertTrue(sampleBreakdown!!.logisticProbability in 0.0..1.0)
        assertEquals(7, sampleBreakdown.contributions.size)
    }
}
