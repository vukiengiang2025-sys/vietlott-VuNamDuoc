package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.analytics.NumberAnalysisResult
import com.example.data.model.LotteryType
import com.example.ui.LotteryViewModel
import com.example.ui.components.LotteryBall
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(
    viewModel: LotteryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val megaCount by viewModel.megaCount.collectAsStateWithLifecycle()
    val powerCount by viewModel.powerCount.collectAsStateWithLifecycle()
    val report by viewModel.analysisReport.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val analysisError by viewModel.analysisError.collectAsStateWithLifecycle()

    var showAllNumbers by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Lottery Type Selector Header
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LotteryType.entries.forEach { type ->
                    val isSelected = type == selectedType
                    val count = if (type == LotteryType.MEGA_645) megaCount else powerCount
                    Button(
                        onClick = { viewModel.selectLotteryType(type) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_${type.code}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = type.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "$count kỳ quay",
                                fontSize = 11.sp,
                                color = (if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        // Analysis Status or Loading
        if (isAnalyzing) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Đang chạy mô hình V4.1...",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Huấn luyện Ensemble Logistic (4 tham số C) & Bootstrap 200 mẫu",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (analysisError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Thông báo phân tích",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = analysisError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = { viewModel.triggerAnalysis() }) {
                            Text("Thử lại")
                        }
                    }
                }
            }
        } else if (report != null) {
            val rep = report!!

            // Top Hero Card: Predicted 6-Number Set
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Analytics,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "BỘ 6 SỐ DỰ ĐOÁN V4.1",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(onClick = { viewModel.triggerAnalysis() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Chạy lại",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Render 6 Balls
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rep.top6Numbers.forEach { num ->
                                LotteryBall(
                                    number = num,
                                    size = 46.dp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val avgProb = rep.top15.take(6).map { it.probability }.average() * 100
                        Text(
                            text = String.format(Locale.US, "Xác suất TB xuất hiện: %.2f%% (Lý thuyết: %.2f%%)", avgProb, rep.theoreticalProb * 100),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Executive Metrics Overview (4 stats)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricMiniCard(
                        title = "Tổng kỳ quay",
                        value = "${rep.totalDraws}",
                        subtitle = "Kỳ ${rep.fromDrawId} ➔ ${rep.toDrawId}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCard(
                        title = "XS Lý Thuyết",
                        value = String.format(Locale.US, "%.2f%%", rep.theoreticalProb * 100),
                        subtitle = "6 / ${rep.lotteryType.totalNumbers} số",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricMiniCard(
                        title = "Ensemble AUC",
                        value = String.format(Locale.US, "%.4f", rep.summary.ensembleAuc),
                        subtitle = if (rep.summary.ensembleAuc > 0.52) "Cải thiện nhẹ" else "Dữ liệu ngẫu nhiên",
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCard(
                        title = "Test Trúng TB",
                        value = String.format(Locale.US, "%.2f số", rep.summary.backtestAvgHit),
                        subtitle = String.format(Locale.US, "Kỳ vọng: %.2f số/kỳ", rep.summary.theoreticalAvgHit),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Export Actions Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.shareReport(context, isCsv = false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Báo cáo Text", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.shareReport(context, isCsv = true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xuất CSV", fontSize = 12.sp)
                    }
                }
            }

            // Section Header: Top 15 Numbers
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Top 15 Số Có Xác Suất Cao Nhất (CI 95%)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (showAllNumbers) "Ẩn bớt" else "Xem tất cả",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("toggle_all_numbers")
                    )
                }
            }

            // Items list: Top 15 or All Numbers
            val displayList = if (showAllNumbers) rep.allNumbers else rep.top15
            items(displayList) { item ->
                NumberStatCard(item = item, theoreticalProb = rep.theoreticalProb)
            }

            // Scientific Disclaimer Box
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Kết Luận Khoa Học & Trách Nhiệm",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Mỗi kỳ quay hoàn toàn độc lập, xác suất toán học của mỗi số là cố định (~${String.format(Locale.US, "%.2f", rep.theoreticalProb * 100)}%).\n" +
                                    "• Chỉ số AUC xấp xỉ 0.5 chứng minh xổ số mang tính chất ngẫu nhiên cao.\n" +
                                    "• Các phân tích thống kê, Bootstrap CI và mô hình máy học chỉ mang tính nghiên cứu học thuật & giải trí, KHÔNG cam kết trúng thưởng và không khuyến khích đầu tư tiền thật.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MetricMiniCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun NumberStatCard(
    item: NumberAnalysisResult,
    theoreticalProb: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ball
            LotteryBall(number = item.number, size = 38.dp)

            Spacer(modifier = Modifier.width(12.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "XS: %.2f%%", item.probability * 100),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    val diff = item.probDifference * 100
                    val diffColor = if (diff >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    Text(
                        text = String.format(Locale.US, "%+.2f%%", diff),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = diffColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // CI bar visual
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "CI95: [%.1f~%.1f%%]", item.ciLow * 100, item.ciHigh * 100),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format(Locale.US, "• Logistic: %.1f%% (z: %+.2f)", item.logisticProbability * 100, item.logitZ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Gan: ${item.currentGap} kỳ • Về: ${item.totalOccurrences} lần",
                        fontSize = 10.sp,
                        color = if (item.currentGap >= 12) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.stability,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = when (item.stability) {
                            "Rất ổn định" -> Color(0xFF2E7D32)
                            "Ổn định" -> Color(0xFF1565C0)
                            else -> Color(0xFFE65100)
                        }
                    )
                }
            }
        }
    }
}
