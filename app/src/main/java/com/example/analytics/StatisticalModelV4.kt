package com.example.analytics

import com.example.data.model.LotteryDraw
import com.example.data.model.LotteryType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

data class NumberAnalysisResult(
    val number: Int,
    val probability: Double,
    val logisticProbability: Double,
    val logitZ: Double,
    val probDifference: Double,
    val ciLow: Double,
    val ciHigh: Double,
    val ciWidth: Double,
    val stability: String,
    val currentGap: Int,
    val avgGap: Double,
    val totalOccurrences: Int,
    val freqAvg: Double,
    val freq10: Double,
    val freq30: Double,
    val zScore: Double,
    val trend: Double
)

data class FeatureImportance(
    val name: String,
    val weight: Double
)

data class LogisticFeatureDetail(
    val name: String,
    val vietnameseLabel: String,
    val weight: Double,            // beta_j
    val oddsRatio: Double,         // exp(beta_j)
    val correlationTarget: Double, // Pearson r(X_j, Y)
    val mean: Double,
    val stdDev: Double,
    val impact: String
)

data class ModelCPerformance(
    val c: Double,
    val trainAuc: Double,
    val testAuc: Double,
    val difference: Double,
    val status: String,
    val ensembleWeight: Double
)

data class NumberLogisticBreakdown(
    val number: Int,
    val rawFeatures: DoubleArray,
    val scaledFeatures: DoubleArray,
    val logitZ: Double,
    val logisticProbability: Double,
    val contributions: List<Pair<String, Double>> // feature name to beta_j * z_j
)

data class ModelSummary(
    val ensembleAuc: Double,
    val logLoss: Double,
    val brierScore: Double,
    val intercept: Double,
    val cScores: List<Pair<Double, Double>>, // C to Test AUC
    val cPerformances: List<ModelCPerformance>,
    val featureWeights: List<FeatureImportance>,
    val logisticFeatures: List<LogisticFeatureDetail>,
    val correlationMatrix: Array<DoubleArray>,
    val featureNames: List<String>,
    val testDrawsEvaluated: Int,
    val backtestAvgHit: Double,
    val theoreticalAvgHit: Double
)

data class FullAnalysisReport(
    val lotteryType: LotteryType,
    val totalDraws: Int,
    val fromDrawId: String,
    val toDrawId: String,
    val theoreticalProb: Double,
    val summary: ModelSummary,
    val allNumbers: List<NumberAnalysisResult>,
    val top15: List<NumberAnalysisResult>,
    val top6Numbers: List<Int>,
    val textReport: String,
    val csvReport: String,
    val numberBreakdowns: Map<Int, NumberLogisticBreakdown>,
    val generatedAt: Long = System.currentTimeMillis()
)

private fun sigmoid(z: Double): Double {
    return when {
        z > 35.0 -> 1.0
        z < -35.0 -> 0.0
        else -> 1.0 / (1.0 + exp(-z))
    }
}

class StatisticalModelV4(private val seed: Long = 42L) {

    private val random = Random(seed)

    fun analyze(draws: List<LotteryDraw>, lotteryType: LotteryType): FullAnalysisReport? {
        if (draws.size < 35) return null

        val sortedDraws = draws.sortedWith(compareBy({ it.drawDate }, { it.drawId }))
        val totalDraws = sortedDraws.size
        val totalNumbers = lotteryType.totalNumbers
        val theoreticalProb = lotteryType.theoreticalProbability // 6/45 or 6/55

        // Build occurrence series: chuoi[so][t] = 1.0 if appeared, 0.0 otherwise
        val chuoi = Array(totalNumbers + 1) { DoubleArray(totalDraws) }
        for (t in 0 until totalDraws) {
            val drawNums = sortedDraws[t].numbers.toSet()
            for (s in 1..totalNumbers) {
                chuoi[s][t] = if (s in drawNums) 1.0 else 0.0
            }
        }

        // Feature extractor
        fun extractFeaturesAtDraw(s: Int, t: Int): DoubleArray? {
            if (t < 30) return null
            val ls = chuoi[s].copyOfRange(0, t)
            val tsTb = ls.average()
            val ts10 = if (t >= 10) ls.copyOfRange(t - 10, t).average() else tsTb
            val ts30 = if (t >= 30) ls.copyOfRange(t - 30, t).average() else ts10

            // Std deviation across all numbers' overall mean at draw t
            val allMeans = DoubleArray(totalNumbers) { numIdx ->
                chuoi[numIdx + 1].copyOfRange(0, t).average()
            }
            val globalMean = allMeans.average()
            val variance = allMeans.map { (it - globalMean) * (it - globalMean) }.average()
            val tsStd = sqrt(variance)
            val zScore = if (tsStd > 1e-7) (tsTb - theoreticalProb) / tsStd else 0.0

            // Gap analysis (Lô gan)
            var gapHien = 0
            for (idx in t - 1 downTo 0) {
                if (chuoi[s][idx] == 0.0) {
                    gapHien++
                } else {
                    break
                }
            }

            val viTriRa = mutableListOf<Int>()
            for (idx in 0 until t) {
                if (chuoi[s][idx] == 1.0) viTriRa.add(idx)
            }

            val khoangCachTb = if (viTriRa.size >= 2) {
                var diffSum = 0.0
                for (i in 1 until viTriRa.size) {
                    diffSum += (viTriRa[i] - viTriRa[i - 1])
                }
                diffSum / (viTriRa.size - 1)
            } else {
                t.toDouble() / max(viTriRa.size.toDouble(), 1.0)
            }

            val gapTl = gapHien.toDouble() / max(khoangCachTb, 1.0)

            // Trend
            val xuHuong = if (t >= 20) {
                val meanGan = ls.copyOfRange(t - 10, t).average()
                val meanTruoc = ls.copyOfRange(t - 20, t - 10).average()
                meanGan - meanTruoc
            } else {
                0.0
            }

            return doubleArrayOf(tsTb, ts10, ts30, zScore, gapHien.toDouble(), gapTl, xuHuong)
        }

        val featureNames = listOf("ts_tb", "ts_10", "ts_30", "z_score", "gap_hien", "gap_tl", "xu_huong")
        val featureLabels = listOf(
            "Tần suất trung bình",
            "Tần suất 10 kỳ",
            "Tần suất 30 kỳ",
            "Z-Score độ lệch chuẩn",
            "Lô gan hiện tại",
            "Tỉ lệ chu kỳ gan",
            "Xu hướng biến thiên"
        )

        // Build training & test matrices
        val xList = mutableListOf<DoubleArray>()
        val yList = mutableListOf<Double>()

        for (t in 30 until totalDraws) {
            val drawNums = sortedDraws[t].numbers.toSet()
            for (s in 1..totalNumbers) {
                val dt = extractFeaturesAtDraw(s, t) ?: continue
                xList.add(dt)
                yList.add(if (s in drawNums) 1.0 else 0.0)
            }
        }

        val sampleCount = xList.size
        val trainCount = (sampleCount * 0.8).toInt()
        val xTrain = xList.subList(0, trainCount)
        val yTrain = yList.subList(0, trainCount)
        val xTest = xList.subList(trainCount, sampleCount)
        val yTest = yList.subList(trainCount, sampleCount)

        // Fit StandardScaler
        val numFeatures = featureNames.size
        val means = DoubleArray(numFeatures)
        val variances = DoubleArray(numFeatures)

        for (j in 0 until numFeatures) {
            var sum = 0.0
            for (i in 0 until trainCount) {
                sum += xTrain[i][j]
            }
            means[j] = sum / max(trainCount, 1)

            var varSum = 0.0
            for (i in 0 until trainCount) {
                val diff = xTrain[i][j] - means[j]
                varSum += diff * diff
            }
            variances[j] = max(varSum / max(trainCount, 1), 1e-6)
        }

        fun scale(sample: DoubleArray): DoubleArray {
            val res = DoubleArray(numFeatures)
            for (j in 0 until numFeatures) {
                res[j] = (sample[j] - means[j]) / sqrt(variances[j])
            }
            return res
        }

        val xTrainScaled = Array(trainCount) { scale(xTrain[it]) }
        val xTestScaled = Array(xTest.size) { scale(xTest[it]) }

        // Pearson Correlation calculation: Feature vs Target and Feature vs Feature
        val yMean = yTrain.average()
        val yVar = yTrain.map { (it - yMean) * (it - yMean) }.average()
        val yStd = sqrt(max(yVar, 1e-6))

        val correlationsWithTarget = DoubleArray(numFeatures)
        for (j in 0 until numFeatures) {
            var cov = 0.0
            var xVar = 0.0
            for (i in 0 until trainCount) {
                val xDiff = xTrain[i][j] - means[j]
                val yDiff = yTrain[i] - yMean
                cov += xDiff * yDiff
                xVar += xDiff * xDiff
            }
            val denom = sqrt(max(xVar * (yVar * trainCount), 1e-9))
            correlationsWithTarget[j] = if (denom > 0) (cov / denom).coerceIn(-1.0, 1.0) else 0.0
        }

        // Inter-feature correlation matrix (7x7)
        val corrMatrix = Array(numFeatures) { DoubleArray(numFeatures) }
        for (j in 0 until numFeatures) {
            for (k in 0 until numFeatures) {
                if (j == k) {
                    corrMatrix[j][k] = 1.0
                } else {
                    var cov = 0.0
                    var varJ = 0.0
                    var varK = 0.0
                    for (i in 0 until trainCount) {
                        val diffJ = xTrain[i][j] - means[j]
                        val diffK = xTrain[i][k] - means[k]
                        cov += diffJ * diffK
                        varJ += diffJ * diffJ
                        varK += diffK * diffK
                    }
                    val denom = sqrt(max(varJ * varK, 1e-9))
                    corrMatrix[j][k] = if (denom > 0) (cov / denom).coerceIn(-1.0, 1.0) else 0.0
                }
            }
        }

        // Train Logistic Regression Ensemble with C = [0.05, 0.1, 0.2, 0.5]
        val cValues = listOf(0.05, 0.1, 0.2, 0.5)
        val models = mutableListOf<TrainedModel>()
        val cScores = mutableListOf<Pair<Double, Double>>()
        val cPerformances = mutableListOf<ModelCPerformance>()

        // Class weights
        val posCount = yTrain.count { it == 1.0 }.toDouble()
        val negCount = (trainCount - posCount)
        val w1 = if (posCount > 0) trainCount.toDouble() / (2.0 * posCount) else 1.0
        val w0 = if (negCount > 0) trainCount.toDouble() / (2.0 * negCount) else 1.0

        for (c in cValues) {
            val model = trainLogisticRegression(
                xTrain = xTrainScaled,
                yTrain = yTrain,
                c = c,
                w0 = w0,
                w1 = w1,
                epochs = 300,
                learningRate = 0.05
            )

            val trainPreds = DoubleArray(xTrainScaled.size) { model.predict(xTrainScaled[it]) }
            val trainAuc = computeAucRoc(yTrain, trainPreds)

            val testPreds = DoubleArray(xTestScaled.size) { model.predict(xTestScaled[it]) }
            val testAuc = computeAucRoc(yTest, testPreds)

            val diff = trainAuc - testAuc
            val status = when {
                diff < 0.03 -> "✅ Ổn định"
                diff < 0.05 -> "⚠️ Overfitting nhẹ"
                else -> "❌ Overfitting"
            }

            models.add(TrainedModel(model.weights, model.bias, c, testAuc))
            cScores.add(Pair(c, testAuc))
            cPerformances.add(
                ModelCPerformance(
                    c = c,
                    trainAuc = trainAuc,
                    testAuc = testAuc,
                    difference = diff,
                    status = status,
                    ensembleWeight = max(testAuc - 0.5, 0.001)
                )
            )
        }

        // Normalize ensemble weights
        val sumRawWeights = cPerformances.map { it.ensembleWeight }.sum()
        val normalizedPerformances = cPerformances.map {
            it.copy(ensembleWeight = if (sumRawWeights > 0) it.ensembleWeight / sumRawWeights else 0.25)
        }

        // Weighted ensemble predictions on test set
        val weights = models.map { max(it.auc - 0.5, 0.001) }
        val sumWeights = weights.sum()

        val yPredEnsemble = DoubleArray(xTestScaled.size)
        for (i in 0 until xTestScaled.size) {
            var sumP = 0.0
            for (mIdx in models.indices) {
                sumP += models[mIdx].predict(xTestScaled[i]) * weights[mIdx]
            }
            yPredEnsemble[i] = sumP / sumWeights
        }

        val ensembleAuc = computeAucRoc(yTest, yPredEnsemble)
        val logLoss = computeLogLoss(yTest, yPredEnsemble)
        val brierScore = computeBrierScore(yTest, yPredEnsemble)

        // Feature importance and weights averages
        val avgFeatureWeights = DoubleArray(numFeatures)
        var avgBias = 0.0
        for (m in models) {
            avgBias += m.bias / models.size
            for (j in 0 until numFeatures) {
                avgFeatureWeights[j] += m.weights[j] / models.size
            }
        }

        val featureImportanceList = featureNames.indices.map { j ->
            FeatureImportance(featureNames[j], avgFeatureWeights[j])
        }.sortedByDescending { abs(it.weight) }

        val logisticFeaturesList = featureNames.indices.map { j ->
            val w = avgFeatureWeights[j]
            val or = exp(w)
            val r = correlationsWithTarget[j]
            val impact = when {
                w > 0.08 -> "Tăng xác suất (Thuận chiều)"
                w < -0.08 -> "Giảm xác suất (Nghịch chiều)"
                else -> "Tác động trung tính / Nhẹ"
            }
            LogisticFeatureDetail(
                name = featureNames[j],
                vietnameseLabel = featureLabels[j],
                weight = w,
                oddsRatio = or,
                correlationTarget = r,
                mean = means[j],
                stdDev = sqrt(variances[j]),
                impact = impact
            )
        }.sortedByDescending { abs(it.weight) }

        // Bootstrap Confidence Intervals & Final Probability per Number
        val bootstrapSamples = 200
        val numberAnalysisList = mutableListOf<NumberAnalysisResult>()
        val breakdowns = mutableMapOf<Int, NumberLogisticBreakdown>()

        for (s in 1..totalNumbers) {
            val ls = chuoi[s]
            val tsTb = ls.average()

            // Bootstrap 95% CI
            val ciLow: Double
            val ciHigh: Double
            if (ls.size >= 30) {
                val bootstrapMeans = DoubleArray(bootstrapSamples)
                for (b in 0 until bootstrapSamples) {
                    var sum = 0.0
                    for (k in ls.indices) {
                        sum += ls[random.nextInt(ls.size)]
                    }
                    bootstrapMeans[b] = sum / ls.size
                }
                bootstrapMeans.sort()
                val idx25 = (bootstrapSamples * 0.025).toInt().coerceIn(0, bootstrapSamples - 1)
                val idx975 = (bootstrapSamples * 0.975).toInt().coerceIn(0, bootstrapSamples - 1)
                ciLow = bootstrapMeans[idx25]
                ciHigh = bootstrapMeans[idx975]
            } else {
                val se = sqrt(tsTb * (1.0 - tsTb) / max(ls.size, 1))
                ciLow = max(0.0, tsTb - 1.96 * se)
                ciHigh = min(1.0, tsTb + 1.96 * se)
            }

            val ciWidth = ciHigh - ciLow
            val stability = when {
                ciWidth < 0.020 -> "Rất ổn định"
                ciWidth < 0.035 -> "Ổn định"
                else -> "Cần thêm dữ liệu"
            }

            val dt = extractFeaturesAtDraw(s, totalDraws)
            val scaledDt = if (dt != null) scale(dt) else DoubleArray(numFeatures)

            var logitZ = avgBias
            for (j in 0 until numFeatures) {
                logitZ += avgFeatureWeights[j] * scaledDt[j]
            }
            val pureLogisticProb = sigmoid(logitZ)

            val probMh = if (dt != null) {
                var sumP = 0.0
                for (mIdx in models.indices) {
                    sumP += models[mIdx].predict(scaledDt) * weights[mIdx]
                }
                sumP / sumWeights
            } else {
                tsTb
            }

            val probTt = if (dt != null) {
                val ts10 = dt[1]
                val gapTl = dt[5]
                val trend = dt[6]
                (ts10 * 0.5 + (1.0 / (1.0 + gapTl)) * 0.3 + trend * 0.2).coerceIn(0.02, 0.35)
            } else {
                tsTb
            }

            val finalProb = (0.6 * probMh + 0.4 * probTt).coerceIn(0.02, 0.35)

            val currentGap = dt?.get(4)?.toInt() ?: 0
            val avgGap = if (dt != null) {
                val gapTl = dt[5]
                if (gapTl > 0) currentGap / gapTl else 1.0
            } else 1.0

            val totalHits = ls.count { it == 1.0 }

            numberAnalysisList.add(
                NumberAnalysisResult(
                    number = s,
                    probability = finalProb,
                    logisticProbability = pureLogisticProb,
                    logitZ = logitZ,
                    probDifference = finalProb - theoreticalProb,
                    ciLow = ciLow,
                    ciHigh = ciHigh,
                    ciWidth = ciWidth,
                    stability = stability,
                    currentGap = currentGap,
                    avgGap = avgGap,
                    totalOccurrences = totalHits,
                    freqAvg = tsTb,
                    freq10 = dt?.get(1) ?: tsTb,
                    freq30 = dt?.get(2) ?: tsTb,
                    zScore = dt?.get(3) ?: 0.0,
                    trend = dt?.get(6) ?: 0.0
                )
            )

            if (dt != null) {
                val contribs = featureNames.indices.map { j ->
                    Pair(featureLabels[j], avgFeatureWeights[j] * scaledDt[j])
                }
                breakdowns[s] = NumberLogisticBreakdown(
                    number = s,
                    rawFeatures = dt,
                    scaledFeatures = scaledDt,
                    logitZ = logitZ,
                    logisticProbability = pureLogisticProb,
                    contributions = contribs
                )
            }
        }

        // Sort by final probability descending
        numberAnalysisList.sortByDescending { it.probability }
        val top15 = numberAnalysisList.take(15)
        val top6Numbers = numberAnalysisList.take(6).map { it.number }.sorted()

        // Backtesting evaluation on latest test draws
        val nTrain = (totalDraws * 0.75).toInt()
        val nVal = (totalDraws * 0.10).toInt()
        val nTest = totalDraws - nTrain - nVal
        var totalHitsInTest = 0

        if (nTest >= 5) {
            for (idxKy in (nTrain + nVal) until totalDraws) {
                val actual = sortedDraws[idxKy].numbers.toSet()
                val scores = mutableListOf<Pair<Int, Double>>()

                for (s in 1..totalNumbers) {
                    val lsBefore = chuoi[s].copyOfRange(0, idxKy)
                    val ts10 = if (idxKy >= 10) lsBefore.copyOfRange(idxKy - 10, idxKy).average() else lsBefore.average()
                    var gap = 0
                    for (vIdx in idxKy - 1 downTo 0) {
                        if (lsBefore[vIdx] == 0.0) gap++ else break
                    }
                    val score = ts10 / (1.0 + gap / 10.0)
                    scores.add(Pair(s, score))
                }

                scores.sortByDescending { it.second }
                val predictedTop6 = scores.take(6).map { it.first }.toSet()
                totalHitsInTest += (predictedTop6 intersect actual).size
            }
        }

        val backtestAvgHit = if (nTest >= 5) totalHitsInTest.toDouble() / nTest.toDouble() else 0.0
        val theoreticalAvgHit = 6.0 * theoreticalProb

        val summary = ModelSummary(
            ensembleAuc = ensembleAuc,
            logLoss = logLoss,
            brierScore = brierScore,
            intercept = avgBias,
            cScores = cScores,
            cPerformances = normalizedPerformances,
            featureWeights = featureImportanceList,
            logisticFeatures = logisticFeaturesList,
            correlationMatrix = corrMatrix,
            featureNames = featureNames,
            testDrawsEvaluated = nTest,
            backtestAvgHit = backtestAvgHit,
            theoreticalAvgHit = theoreticalAvgHit
        )

        // Generate reports
        val textReport = buildTextReport(
            lotteryType = lotteryType,
            totalDraws = totalDraws,
            theoreticalProb = theoreticalProb,
            summary = summary,
            top15 = top15,
            top6Numbers = top6Numbers
        )

        val csvReport = buildCsvReport(numberAnalysisList)

        return FullAnalysisReport(
            lotteryType = lotteryType,
            totalDraws = totalDraws,
            fromDrawId = sortedDraws.first().drawId,
            toDrawId = sortedDraws.last().drawId,
            theoreticalProb = theoreticalProb,
            summary = summary,
            allNumbers = numberAnalysisList,
            top15 = top15,
            top6Numbers = top6Numbers,
            textReport = textReport,
            csvReport = csvReport,
            numberBreakdowns = breakdowns
        )
    }

    private data class TrainedModel(
        val weights: DoubleArray,
        val bias: Double,
        val c: Double,
        val auc: Double
    ) {
        fun predict(x: DoubleArray): Double {
            var z = bias
            for (i in x.indices) {
                z += weights[i] * x[i]
            }
            return sigmoid(z)
        }
    }

    private fun trainLogisticRegression(
        xTrain: Array<DoubleArray>,
        yTrain: List<Double>,
        c: Double,
        w0: Double,
        w1: Double,
        epochs: Int,
        learningRate: Double
    ): TrainedModel {
        val numFeatures = xTrain[0].size
        val weights = DoubleArray(numFeatures)
        var bias = 0.0
        val l2Lambda = 1.0 / c
        val n = xTrain.size.toDouble()

        for (epoch in 0 until epochs) {
            val gradWeights = DoubleArray(numFeatures)
            var gradBias = 0.0

            for (i in 0 until xTrain.size) {
                val x = xTrain[i]
                val y = yTrain[i]
                var z = bias
                for (j in 0 until numFeatures) {
                    z += weights[j] * x[j]
                }
                val pred = sigmoid(z)
                val sampleWeight = if (y == 1.0) w1 else w0
                val error = (pred - y) * sampleWeight

                for (j in 0 until numFeatures) {
                    gradWeights[j] += error * x[j]
                }
                gradBias += error
            }

            for (j in 0 until numFeatures) {
                val regularizer = l2Lambda * weights[j]
                weights[j] -= learningRate * ((gradWeights[j] / n) + regularizer / n)
            }
            bias -= learningRate * (gradBias / n)
        }

        return TrainedModel(weights, bias, c, 0.5)
    }

    private fun computeAucRoc(yTrue: List<Double>, yPred: DoubleArray): Double {
        val pairs = yTrue.indices.map { Pair(yPred[it], yTrue[it]) }
            .sortedByDescending { it.first }

        var posCount = 0.0
        var negCount = 0.0
        for (p in pairs) {
            if (p.second == 1.0) posCount++ else negCount++
        }

        if (posCount == 0.0 || negCount == 0.0) return 0.5

        var cumulativePos = 0.0
        var auc = 0.0
        for (p in pairs) {
            if (p.second == 1.0) {
                cumulativePos++
            } else {
                auc += cumulativePos
            }
        }

        return auc / (posCount * negCount)
    }

    private fun computeLogLoss(yTrue: List<Double>, yPred: DoubleArray): Double {
        var sum = 0.0
        for (i in yTrue.indices) {
            val y = yTrue[i]
            val p = yPred[i].coerceIn(1e-7, 1.0 - 1e-7)
            sum += y * ln(p) + (1.0 - y) * ln(1.0 - p)
        }
        return -sum / max(yTrue.size, 1)
    }

    private fun computeBrierScore(yTrue: List<Double>, yPred: DoubleArray): Double {
        var sum = 0.0
        for (i in yTrue.indices) {
            val diff = yPred[i] - yTrue[i]
            sum += diff * diff
        }
        return sum / max(yTrue.size, 1)
    }

    private fun buildTextReport(
        lotteryType: LotteryType,
        totalDraws: Int,
        theoreticalProb: Double,
        summary: ModelSummary,
        top15: List<NumberAnalysisResult>,
        top6Numbers: List<Int>
    ): String {
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        sb.append("=================================================================\n")
        sb.append("BÁO CÁO ${lotteryType.displayName.uppercase(Locale.getDefault())} — MÔ HÌNH BINARY LOGISTIC V4.1\n")
        sb.append("Ngày: $dateStr\n")
        sb.append("Tổng kỳ: $totalDraws | Xác suất lý thuyết: ${String.format(Locale.US, "%.2f", theoreticalProb * 100)}%\n")
        sb.append("=================================================================\n\n")

        sb.append("📊 TỔNG QUAN BINARY LOGISTIC ENSEMBLE:\n")
        sb.append(String.format(Locale.US, "  Hệ số chặn Intercept (beta_0) = %.4f\n", summary.intercept))
        sb.append(String.format(Locale.US, "  Ensemble AUC                 = %.4f\n", summary.ensembleAuc))
        sb.append(String.format(Locale.US, "  Log Loss                     = %.4f\n", summary.logLoss))
        sb.append(String.format(Locale.US, "  Brier Score                  = %.4f\n\n", summary.brierScore))

        sb.append("🤖 CHI TIẾT 4 MÔ HÌNH LOGISTIC THEO THAM SỐ C:\n")
        for (cp in summary.cPerformances) {
            sb.append(
                String.format(
                    Locale.US,
                    "  C = %-4.2f | Train AUC = %.4f | Test AUC = %.4f | Chênh lệch = %+.4f | %s | Trọng số = %.1f%%\n",
                    cp.c, cp.trainAuc, cp.testAuc, cp.difference, cp.status, cp.ensembleWeight * 100
                )
            )
        }

        sb.append("\n📈 HỆ SỐ TƯƠNG QUAN VÀ TRỌNG SỐ BIẾN (ODDS RATIO):\n")
        sb.append("─────────────────────────────────────────────────────────────────\n")
        sb.append("Tên biến       Trọng số (Beta)  Odds Ratio  Tương quan r(Y)  Tác động\n")
        sb.append("─────────────────────────────────────────────────────────────────\n")
        for (f in summary.logisticFeatures) {
            sb.append(
                String.format(
                    Locale.US,
                    "%-14s %14.4f %11.4f %15.4f  %s\n",
                    f.name, f.weight, f.oddsRatio, f.correlationTarget, f.impact
                )
            )
        }

        sb.append("\n🏆 TOP 15 SỐ — XÁC SUẤT KỲ TIẾP THEO (CI 95% & LOGISTIC P):\n")
        sb.append("─────────────────────────────────────────────────────────────────\n")
        sb.append("Số  XS Cuối   XS Logistic   Logit Z    CI95%             LôGan  Độ tin cậy\n")
        sb.append("─────────────────────────────────────────────────────────────────\n")

        for (r in top15) {
            sb.append(
                String.format(
                    Locale.US,
                    "%02d  %6.2f%%    %6.2f%%      %+5.2f    [%5.2f~%5.2f]     %3d    %s\n",
                    r.number,
                    r.probability * 100,
                    r.logisticProbability * 100,
                    r.logitZ,
                    r.ciLow * 100,
                    r.ciHigh * 100,
                    r.currentGap,
                    r.stability
                )
            )
        }

        val avgProbTop6 = top15.take(6).map { it.probability }.average()
        sb.append("\n🎯 BỘ 6 SỐ: [ ${top6Numbers.joinToString(" ") { String.format(Locale.US, "%02d", it) }} ]\n")
        sb.append(String.format(Locale.US, "Xác suất TB: %.2f%%\n\n", avgProbTop6 * 100))

        sb.append("📈 THEO DÕI HIỆU SUẤT THỰC TẾ (TEST SET):\n")
        sb.append(String.format(Locale.US, "  Trúng TB/kỳ: %.2f số (Kỳ vọng ngẫu nhiên: %.2f số)\n", summary.backtestAvgHit, summary.theoreticalAvgHit))

        sb.append("=================================================================\n")
        sb.append("⚠️ KẾT LUẬN KHOA HỌC\n")
        sb.append("=================================================================\n")
        sb.append("• Mỗi kỳ quay hoàn toàn độc lập → xác suất mỗi số cố định ≈${String.format(Locale.US, "%.2f", theoreticalProb * 100)}%\n")
        sb.append("• AUC ≈ 0.5 → không mô hình nào có thể vượt qua tính ngẫu nhiên thuần túy\n")
        sb.append("• Kết quả chỉ mang tính tham khảo thống kê & giải trí — KHÔNG đầu tư tiền thật!\n")

        return sb.toString()
    }

    private fun buildCsvReport(numbers: List<NumberAnalysisResult>): String {
        val sb = StringBuilder()
        sb.append("So,XacSuatCuoi,XacSuatLogistic,LogitZ,ChenhLech,CI_Low,CI_High,CI_Rong,LogGan,TinCay,TanSuat,ZScore,XuHuong\n")
        for (r in numbers) {
            sb.append(
                String.format(
                    Locale.US,
                    "%d,%.6f,%.6f,%.4f,%.6f,%.6f,%.6f,%.6f,%d,%s,%d,%.4f,%.4f\n",
                    r.number,
                    r.probability,
                    r.logisticProbability,
                    r.logitZ,
                    r.probDifference,
                    r.ciLow,
                    r.ciHigh,
                    r.ciWidth,
                    r.currentGap,
                    r.stability,
                    r.totalOccurrences,
                    r.zScore,
                    r.trend
                )
            )
        }
        return sb.toString()
    }
}
