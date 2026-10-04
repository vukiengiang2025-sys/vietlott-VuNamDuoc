package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.LotteryViewModel
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.CheckerScreen
import com.example.ui.screens.DataManagementScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LogisticModelScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    ANALYTICS("Thống Kê", Icons.Filled.Analytics, Icons.Outlined.Analytics, "nav_analytics"),
    LOGISTIC("Mô Hình AI", Icons.Filled.Functions, Icons.Outlined.Functions, "nav_logistic"),
    HISTORY("Lịch Sử", Icons.Filled.History, Icons.Outlined.History, "nav_history"),
    CHECKER("Dò Vé", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle, "nav_checker"),
    DATA("Dữ Liệu", Icons.Filled.Storage, Icons.Outlined.Storage, "nav_data")
}

class MainActivity : ComponentActivity() {

    private val viewModel: LotteryViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var currentTab by remember { mutableStateOf(AppNavTab.ANALYTICS) }
                val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val snackbarMsg by viewModel.snackBarMessage.collectAsStateWithLifecycle()

                LaunchedEffect(snackbarMsg) {
                    snackbarMsg?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearSnackBar()
                    }
                }

                // Custom back handler: return to Analytics tab if on another tab
                if (currentTab != AppNavTab.ANALYTICS) {
                    BackHandler {
                        currentTab = AppNavTab.ANALYTICS
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "Vietlott Stat • ${selectedType.displayName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            AppNavTab.entries.forEach { tab ->
                                val isSelected = currentTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(text = tab.label, fontSize = 11.sp) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            AppNavTab.ANALYTICS -> AnalyticsScreen(viewModel = viewModel)
                            AppNavTab.LOGISTIC -> LogisticModelScreen(viewModel = viewModel)
                            AppNavTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                            AppNavTab.CHECKER -> CheckerScreen(viewModel = viewModel)
                            AppNavTab.DATA -> DataManagementScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
