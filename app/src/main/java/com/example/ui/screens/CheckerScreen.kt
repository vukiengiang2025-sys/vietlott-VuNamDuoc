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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LotteryType
import com.example.ui.LotteryViewModel
import com.example.ui.TicketMatch
import com.example.ui.components.LotteryBall
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckerScreen(
    viewModel: LotteryViewModel,
    modifier: Modifier = Modifier
) {
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val checkedNumbers by viewModel.checkedNumbers.collectAsStateWithLifecycle()
    val checkSummary by viewModel.checkSummary.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bộ Số Của Bạn (${checkedNumbers.size}/6)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row {
                            OutlinedButton(
                                onClick = {
                                    val randomPick = (1..selectedType.totalNumbers).shuffled().take(6).toSet()
                                    viewModel.clearCheckedNumbers()
                                    randomPick.forEach { viewModel.toggleCheckedNumber(it) }
                                },
                                modifier = Modifier.testTag("random_ticket_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Chọn ngẫu nhiên", fontSize = 12.sp)
                            }
                            if (checkedNumbers.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                OutlinedButton(onClick = { viewModel.clearCheckedNumbers() }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Selected balls row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val sorted = checkedNumbers.toList().sorted()
                        for (i in 0 until 6) {
                            if (i < sorted.size) {
                                LotteryBall(
                                    number = sorted[i],
                                    size = 42.dp,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    onClick = { viewModel.toggleCheckedNumber(sorted[i]) }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(42.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(21.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "?",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ball Picker Grid (1..totalNumbers)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Chạm để chọn 6 số (1 ➔ ${selectedType.totalNumbers}):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (num in 1..selectedType.totalNumbers) {
                            val isPicked = checkedNumbers.contains(num)
                            LotteryBall(
                                number = num,
                                size = 35.dp,
                                isSelected = isPicked,
                                onClick = { viewModel.toggleCheckedNumber(num) }
                            )
                        }
                    }
                }
            }
        }

        // Summary of historical winnings
        if (checkSummary != null) {
            val sum = checkSummary!!
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (sum.totalPrizeAmount > 0) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = if (sum.totalPrizeAmount > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "KẾT QUẢ ĐỐI SOÁT LỊCH SỬ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (sum.totalPrizeAmount > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val formattedVnd = NumberFormat.getNumberInstance(Locale.US).format(sum.totalPrizeAmount)
                        Text(
                            text = "Tổng tiền thưởng giả định: $formattedVnd VNĐ",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = if (sum.totalPrizeAmount > 0) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            PrizeBadge("Jackpot", "${sum.jackpotMatches} lần", Color(0xFFD32F2F))
                            PrizeBadge("Giải Nhất", "${sum.prize1Matches} lần", Color(0xFFE65100))
                            PrizeBadge("Giải Nhì", "${sum.prize2Matches} lần", Color(0xFF1565C0))
                            PrizeBadge("Giải Ba", "${sum.prize3Matches} lần", Color(0xFF2E7D32))
                        }
                    }
                }
            }

            // List of matching draws
            item {
                Text(
                    text = "Các kỳ đã từng trúng (${sum.matchedDraws.size} kỳ):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            if (sum.matchedDraws.isEmpty()) {
                item {
                    Text(
                        text = "Bộ số này chưa từng trúng giải nào từ 3 số trở lên trong lịch sử dữ liệu.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(sum.matchedDraws) { match ->
                    MatchedDrawCard(match = match, userNums = sum.checkedNumbers)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun PrizeBadge(label: String, count: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = Color.Gray)
        Text(text = count, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchedDrawCard(
    match: TicketMatch,
    userNums: List<Int>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kỳ #${match.draw.drawId} (${match.draw.drawDate})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = match.prizeName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (match.isJackpot) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Balls: highlight matched balls in green
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                match.draw.numbers.forEach { num ->
                    val isMatch = userNums.contains(num)
                    LotteryBall(
                        number = num,
                        size = 32.dp,
                        isMatched = isMatch
                    )
                }
                if (match.draw.bonusNumber != null) {
                    val isBonusMatch = userNums.contains(match.draw.bonusNumber)
                    LotteryBall(
                        number = match.draw.bonusNumber,
                        size = 32.dp,
                        isBonus = true,
                        isMatched = isBonusMatch
                    )
                }
            }
        }
    }
}
