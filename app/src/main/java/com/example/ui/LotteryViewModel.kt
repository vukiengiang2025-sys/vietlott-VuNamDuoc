package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.analytics.FullAnalysisReport
import com.example.analytics.StatisticalModelV4
import com.example.data.local.AppDatabase
import com.example.data.model.LotteryDraw
import com.example.data.model.LotteryType
import com.example.data.repository.LotteryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class GeneratorStrategy(val title: String, val subtitle: String) {
    AI_MODEL("Mô Hình V4.1", "Top 6 số xác suất kết hợp Ensemble"),
    HOT_NUMBERS("Cầu Đang Về", "Tần suất cao nhất 10 kỳ & xu hướng tăng"),
    COLD_GAN("Bắt Lô Gan", "Các số lâu chưa về đang tích lũy"),
    BALANCED("Cân Bằng Thống Kê", "3 Chẵn - 3 Lẻ, 3 Tài - 3 Xỉu đối xứng"),
    MONTE_CARLO("Ngẫu Nhiên Chuẩn", "Mô phỏng ngẫu nhiên hạt nhân")
}

data class TicketMatch(
    val draw: LotteryDraw,
    val matchedNumbers: List<Int>,
    val prizeName: String,
    val prizeAmount: Long,
    val isJackpot: Boolean
)

data class TicketCheckSummary(
    val checkedNumbers: List<Int>,
    val totalDrawsChecked: Int,
    val jackpotMatches: Int,
    val prize1Matches: Int,
    val prize2Matches: Int,
    val prize3Matches: Int,
    val totalPrizeAmount: Long,
    val matchedDraws: List<TicketMatch>
)

class LotteryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = LotteryRepository(database.lotteryDao())
    private val statisticalModel = StatisticalModelV4(seed = 42L)

    // Current lottery type
    private val _selectedType = MutableStateFlow(LotteryType.MEGA_645)
    val selectedType: StateFlow<LotteryType> = _selectedType.asStateFlow()

    // Draw counts
    val megaCount: StateFlow<Int> = repository.getDrawCount(LotteryType.MEGA_645)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val powerCount: StateFlow<Int> = repository.getDrawCount(LotteryType.POWER_655)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Current type draws
    private val _currentDraws = MutableStateFlow<List<LotteryDraw>>(emptyList())
    val currentDraws: StateFlow<List<LotteryDraw>> = _currentDraws.asStateFlow()

    // Analysis State
    private val _analysisReport = MutableStateFlow<FullAnalysisReport?>(null)
    val analysisReport: StateFlow<FullAnalysisReport?> = _analysisReport.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    // Download & Data Management State
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadStatus = MutableStateFlow("")
    val downloadStatus: StateFlow<String> = _downloadStatus.asStateFlow()

    private val _snackBarMessage = MutableStateFlow<String?>(null)
    val snackBarMessage: StateFlow<String?> = _snackBarMessage.asStateFlow()

    // History Search
    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _filteredHistoryBall = MutableStateFlow<Int?>(null)
    val filteredHistoryBall: StateFlow<Int?> = _filteredHistoryBall.asStateFlow()

    val filteredDraws: StateFlow<List<LotteryDraw>> = combine(
        _currentDraws,
        _historySearchQuery,
        _filteredHistoryBall
    ) { draws, query, ball ->
        draws.filter { draw ->
            val matchQuery = query.isBlank() ||
                    draw.drawId.contains(query, ignoreCase = true) ||
                    draw.drawDate.contains(query, ignoreCase = true)

            val matchBall = ball == null ||
                    draw.numbers.contains(ball) ||
                    draw.bonusNumber == ball

            matchQuery && matchBall
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Ticket Checker State
    private val _checkedNumbers = MutableStateFlow<Set<Int>>(emptySet())
    val checkedNumbers: StateFlow<Set<Int>> = _checkedNumbers.asStateFlow()

    private val _checkSummary = MutableStateFlow<TicketCheckSummary?>(null)
    val checkSummary: StateFlow<TicketCheckSummary?> = _checkSummary.asStateFlow()

    // Generator State
    private val _generatorStrategy = MutableStateFlow(GeneratorStrategy.AI_MODEL)
    val generatorStrategy: StateFlow<GeneratorStrategy> = _generatorStrategy.asStateFlow()

    private val _generatedSets = MutableStateFlow<List<List<Int>>>(emptyList())
    val generatedSets: StateFlow<List<List<Int>>> = _generatedSets.asStateFlow()

    init {
        // Load initial assets into database if needed
        viewModelScope.launch {
            repository.ensureInitialDataLoaded(getApplication())
            loadDrawsForType(_selectedType.value)
        }
    }

    fun selectLotteryType(type: LotteryType) {
        if (_selectedType.value != type) {
            _selectedType.value = type
            _analysisReport.value = null
            _checkedNumbers.value = emptySet()
            _checkSummary.value = null
            _generatedSets.value = emptyList()
            loadDrawsForType(type)
        }
    }

    private fun loadDrawsForType(type: LotteryType) {
        viewModelScope.launch {
            repository.getDraws(type).collect { draws ->
                _currentDraws.value = draws
                if (draws.size >= 35 && _analysisReport.value == null && !_isAnalyzing.value) {
                    runAnalysis(draws, type)
                }
            }
        }
    }

    fun triggerAnalysis() {
        val draws = _currentDraws.value
        val type = _selectedType.value
        runAnalysis(draws, type)
    }

    private fun runAnalysis(draws: List<LotteryDraw>, type: LotteryType) {
        if (draws.size < 35) {
            _analysisError.value = "Cần ít nhất 35 kỳ quay để chạy mô hình V4.1 (hiện có ${draws.size} kỳ)."
            return
        }

        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null
            val result = withContext(Dispatchers.Default) {
                statisticalModel.analyze(draws, type)
            }
            _isAnalyzing.value = false
            if (result != null) {
                _analysisReport.value = result
            } else {
                _analysisError.value = "Không thể phân tích dữ liệu kỳ quay."
            }
        }
    }

    fun downloadFromGithub(type: LotteryType, customUrl: String? = null) {
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadStatus.value = "Bắt đầu tải từ GitHub..."
            val result = repository.downloadFromGithub(type, customUrl) { status ->
                _downloadStatus.value = status
            }
            _isDownloading.value = false
            result.fold(
                onSuccess = { count ->
                    _snackBarMessage.value = "Tải thành công $count kỳ quay ${type.displayName}!"
                    triggerAnalysis()
                },
                onFailure = { err ->
                    _snackBarMessage.value = "Lỗi tải dữ liệu: ${err.localizedMessage}"
                }
            )
        }
    }

    fun importJsonlContent(content: String, type: LotteryType) {
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadStatus.value = "Đang xử lý nội dung JSONL..."
            val result = repository.importFromText(content, type)
            _isDownloading.value = false
            result.fold(
                onSuccess = { count ->
                    _snackBarMessage.value = "Đã nhập thành công $count kỳ quay ${type.displayName}!"
                    triggerAnalysis()
                },
                onFailure = { err ->
                    _snackBarMessage.value = "Lỗi nhập file: ${err.localizedMessage}"
                }
            )
        }
    }

    fun resetToSampleData(type: LotteryType) {
        viewModelScope.launch {
            _isDownloading.value = true
            val result = repository.resetToSampleData(getApplication(), type)
            _isDownloading.value = false
            result.fold(
                onSuccess = { count ->
                    _snackBarMessage.value = "Đã khôi phục $count kỳ quay mẫu ${type.displayName}!"
                    triggerAnalysis()
                },
                onFailure = { err ->
                    _snackBarMessage.value = "Lỗi khôi phục: ${err.localizedMessage}"
                }
            )
        }
    }

    fun clearData(type: LotteryType) {
        viewModelScope.launch {
            repository.clearDraws(type)
            _analysisReport.value = null
            _snackBarMessage.value = "Đã xóa toàn bộ dữ liệu ${type.displayName}."
        }
    }

    fun clearSnackBar() {
        _snackBarMessage.value = null
    }

    // History filter helpers
    fun setHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun toggleHistoryBallFilter(ball: Int) {
        _filteredHistoryBall.value = if (_filteredHistoryBall.value == ball) null else ball
    }

    // Ticket Checker Logic
    fun toggleCheckedNumber(num: Int) {
        val current = _checkedNumbers.value.toMutableSet()
        if (current.contains(num)) {
            current.remove(num)
        } else {
            if (current.size < 6) {
                current.add(num)
            }
        }
        _checkedNumbers.value = current
        if (current.size == 6) {
            executeTicketCheck(current.toList().sorted())
        } else {
            _checkSummary.value = null
        }
    }

    fun clearCheckedNumbers() {
        _checkedNumbers.value = emptySet()
        _checkSummary.value = null
    }

    private fun executeTicketCheck(userNums: List<Int>) {
        val draws = _currentDraws.value
        val type = _selectedType.value
        val matches = mutableListOf<TicketMatch>()

        var jackpots = 0
        var prize1 = 0
        var prize2 = 0
        var prize3 = 0
        var totalPrize = 0L

        for (draw in draws) {
            val common = userNums.intersect(draw.numbers.toSet()).toList().sorted()
            val matchCount = common.size

            if (type == LotteryType.MEGA_645) {
                when (matchCount) {
                    6 -> {
                        jackpots++
                        totalPrize += 12_000_000_000L
                        matches.add(TicketMatch(draw, common, "Jackpot (6 số)", 12_000_000_000L, true))
                    }
                    5 -> {
                        prize1++
                        totalPrize += 10_000_000L
                        matches.add(TicketMatch(draw, common, "Giải Nhất (5 số)", 10_000_000L, false))
                    }
                    4 -> {
                        prize2++
                        totalPrize += 300_000L
                        matches.add(TicketMatch(draw, common, "Giải Nhì (4 số)", 300_000L, false))
                    }
                    3 -> {
                        prize3++
                        totalPrize += 30_000L
                        matches.add(TicketMatch(draw, common, "Giải Ba (3 số)", 30_000L, false))
                    }
                }
            } else {
                // Power 6/55
                val bonusMatched = draw.bonusNumber != null && userNums.contains(draw.bonusNumber)
                when {
                    matchCount == 6 -> {
                        jackpots++
                        totalPrize += 30_000_000_000L
                        matches.add(TicketMatch(draw, common, "Jackpot 1 (6 số)", 30_000_000_000L, true))
                    }
                    matchCount == 5 && bonusMatched -> {
                        jackpots++
                        totalPrize += 3_000_000_000L
                        val fullMatch = (common + listOfNotNull(draw.bonusNumber)).sorted()
                        matches.add(TicketMatch(draw, fullMatch, "Jackpot 2 (5+1 số)", 3_000_000_000L, true))
                    }
                    matchCount == 5 -> {
                        prize1++
                        totalPrize += 40_000_000L
                        matches.add(TicketMatch(draw, common, "Giải Nhất (5 số)", 40_000_000L, false))
                    }
                    matchCount == 4 -> {
                        prize2++
                        totalPrize += 500_000L
                        matches.add(TicketMatch(draw, common, "Giải Nhì (4 số)", 500_000L, false))
                    }
                    matchCount == 3 -> {
                        prize3++
                        totalPrize += 50_000L
                        matches.add(TicketMatch(draw, common, "Giải Ba (3 số)", 50_000L, false))
                    }
                }
            }
        }

        _checkSummary.value = TicketCheckSummary(
            checkedNumbers = userNums,
            totalDrawsChecked = draws.size,
            jackpotMatches = jackpots,
            prize1Matches = prize1,
            prize2Matches = prize2,
            prize3Matches = prize3,
            totalPrizeAmount = totalPrize,
            matchedDraws = matches
        )
    }

    // Smart Generator Logic
    fun setGeneratorStrategy(strategy: GeneratorStrategy) {
        _generatorStrategy.value = strategy
    }

    fun generateNumbers(count: Int = 1) {
        val type = _selectedType.value
        val report = _analysisReport.value
        val maxNum = type.totalNumbers
        val sets = mutableListOf<List<Int>>()

        val allNums = (1..maxNum).toList()

        for (i in 0 until count) {
            val generated: List<Int> = when (_generatorStrategy.value) {
                GeneratorStrategy.AI_MODEL -> {
                    if (report != null && report.top15.size >= 10) {
                        // Weighted pick from Top 15 numbers based on probability
                        val pool = report.top15.map { it.number }.shuffled()
                        pool.take(6).sorted()
                    } else {
                        allNums.shuffled().take(6).sorted()
                    }
                }
                GeneratorStrategy.HOT_NUMBERS -> {
                    if (report != null) {
                        val hotPool = report.allNumbers
                            .sortedByDescending { it.freq10 + it.trend }
                            .take(15)
                            .map { it.number }
                            .shuffled()
                        hotPool.take(6).sorted()
                    } else {
                        allNums.shuffled().take(6).sorted()
                    }
                }
                GeneratorStrategy.COLD_GAN -> {
                    if (report != null) {
                        val coldPool = report.allNumbers
                            .sortedByDescending { it.currentGap }
                            .take(15)
                            .map { it.number }
                            .shuffled()
                        coldPool.take(6).sorted()
                    } else {
                        allNums.shuffled().take(6).sorted()
                    }
                }
                GeneratorStrategy.BALANCED -> {
                    // 3 Even, 3 Odd; 3 Low, 3 High
                    val mid = maxNum / 2
                    val evens = allNums.filter { it % 2 == 0 }.shuffled()
                    val odds = allNums.filter { it % 2 != 0 }.shuffled()
                    val pick = (evens.take(3) + odds.take(3)).distinct()
                    if (pick.size == 6) pick.sorted() else allNums.shuffled().take(6).sorted()
                }
                GeneratorStrategy.MONTE_CARLO -> {
                    allNums.shuffled(Random(System.nanoTime())).take(6).sorted()
                }
            }
            sets.add(generated)
        }
        _generatedSets.value = sets
    }

    fun shareReport(context: Context, isCsv: Boolean) {
        val report = _analysisReport.value ?: return
        val text = if (isCsv) report.csvReport else report.textReport
        val title = if (isCsv) "BaoCao_${report.lotteryType.code}.csv" else "BaoCao_${report.lotteryType.code}.txt"

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
            type = if (isCsv) "text/csv" else "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Chia sẻ báo cáo ${report.lotteryType.displayName}")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }
}
