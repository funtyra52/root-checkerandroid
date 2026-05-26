package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CheckItem
import com.example.CheckStatus
import com.example.OverallDeviceStatus
import com.example.RootCheckerState
import com.example.RootCheckerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootCheckerScreen(
    viewModel: RootCheckerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val terminalListState = rememberLazyListState()
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Onboarding Transition states
    var isStartingTransition by remember { mutableStateOf(false) }
    var transitionProgress by remember { mutableFloatStateOf(0f) }
    var transitionPhaseText by remember { mutableStateOf("") }

    // Auto scroll terminal log to bottom on entry changes
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            terminalListState.animateScrollToItem(state.logs.size - 1)
        }
    }

    // Trigger transition phases when starts
    LaunchedEffect(isStartingTransition) {
        if (isStartingTransition) {
            val phases = if (state.language == "en") {
                listOf(
                    "Registering preferences...",
                    "Configuring visual parameters...",
                    "Adapting translations...",
                    "Starting kernel checkers...",
                    "System Ready!"
                )
            } else {
                listOf(
                    "Сохранение параметров...",
                    "Конфигурация цветовой темы...",
                    "Адаптация перевода интерфейса...",
                    "Запуск тестов ядра...",
                    "Окружение готово к безопасности!"
                )
            }

            for (i in phases.indices) {
                transitionPhaseText = phases[i]
                val targetProgress = (i + 1).toFloat() / phases.size
                animate(
                    initialValue = transitionProgress,
                    targetValue = targetProgress,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                ) { value, _ ->
                    transitionProgress = value
                }
                delay(300)
            }
            delay(200)
            viewModel.completeOnboarding()
            isStartingTransition = false
        }
    }

    if (!state.isOnboardingCompleted) {
        OnboardingScreen(
            state = state,
            viewModel = viewModel,
            isTransitioning = isStartingTransition,
            transitionProgress = transitionProgress,
            transitionText = transitionPhaseText,
            onStartTransition = {
                isStartingTransition = true
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "App Icon",
                                tint = com.example.ui.theme.CleanBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = viewModel.getLocalizedText("app_title"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                                color = com.example.ui.theme.CleanTextPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("settings_gear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = com.example.ui.theme.CleanBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Overall Status Card
                item {
                    StatusOverviewPanel(
                        state = state,
                        viewModel = viewModel,
                        onStartScan = { viewModel.startSystemScan(context) }
                    )
                }

                // 2. Action buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startSystemScan(context) },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(54.dp)
                                .testTag("scan_button"),
                            enabled = !state.isScanning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = com.example.ui.theme.CleanBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(27.dp)
                        ) {
                            if (state.isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = viewModel.getLocalizedText("scanning_btn"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Scan")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = viewModel.getLocalizedText("start_test_btn"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.requestInteractiveSu() },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("request_su_button"),
                            enabled = !state.isScanning && !state.isRequestingActiveSu,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = com.example.ui.theme.CleanBlue
                            ),
                            border = BorderStroke(1.5.dp, com.example.ui.theme.CleanBlue),
                            shape = RoundedCornerShape(27.dp)
                        ) {
                            if (state.isRequestingActiveSu) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = com.example.ui.theme.CleanBlue,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Build, contentDescription = "interactive su")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = viewModel.getLocalizedText("test_su_call_btn"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // 3. Mini Explanatory Banner about active su request
                if (state.activeSuGranted != null) {
                    item {
                        SuRequestFeedbackBanner(
                            granted = state.activeSuGranted!!,
                            viewModel = viewModel
                        )
                    }
                }

                // 4. Detailed audit breakdown section header
                item {
                    Text(
                        text = viewModel.getLocalizedText("details_label"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // 5. Checklist items
                items(state.checks) { check ->
                    CheckItemCard(
                        check = check,
                        viewModel = viewModel
                    )
                }

                // 6. Terminal Console Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.getLocalizedText("terminal_title"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (state.logs.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearLogs() },
                                modifier = Modifier.testTag("clear_logs")
                            ) {
                                Text(
                                    text = if (state.language == "en") "Clear" else "Очистить",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                item {
                    TerminalConsole(
                        logs = state.logs,
                        listState = terminalListState,
                        viewModel = viewModel
                    )
                }

                // 7. Security Guide Details / Education block
                item {
                    EducationBlock(viewModel = viewModel)
                }

                // Margin bottom
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Settings Dialog Box
        if (showSettingsDialog) {
            SettingsDialog(
                state = state,
                viewModel = viewModel,
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    state: RootCheckerState,
    viewModel: RootCheckerViewModel,
    isTransitioning: Boolean,
    transitionProgress: Float,
    transitionText: String,
    onStartTransition: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isTransitioning) {
            // Elegant scanning / transition phase text animation
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(140.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { transitionProgress },
                        modifier = Modifier.size(120.dp),
                        color = com.example.ui.theme.CleanBlue,
                        strokeWidth = 6.dp,
                    )
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = com.example.ui.theme.CleanBlue,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = transitionText,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = com.example.ui.theme.CleanTextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { transitionProgress },
                    modifier = Modifier
                        .width(200.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = com.example.ui.theme.CleanBlue,
                    trackColor = com.example.ui.theme.CleanSurface
                )
            }
        } else {
            // Beautiful minimalist configure state onboarding screen view
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main visual logo container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(com.example.ui.theme.CleanBlueContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Shield logo",
                        tint = com.example.ui.theme.CleanBlue,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = viewModel.getLocalizedText("welcome"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = com.example.ui.theme.CleanTextPrimary,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = viewModel.getLocalizedText("onboarding_desc"),
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    color = com.example.ui.theme.CleanTextSecondary,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Preferences selectors card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = viewModel.getLocalizedText("onboarding_config_label"),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = com.example.ui.theme.CleanTextPrimary
                        )

                        // 1. Theme Picker option (Toggle)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = viewModel.getLocalizedText("select_theme"),
                                fontSize = 14.sp,
                                color = com.example.ui.theme.CleanTextSecondary
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(4.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.setTheme(false) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!state.isDarkTheme) com.example.ui.theme.CleanBlue else Color.Transparent,
                                        contentColor = if (!state.isDarkTheme) Color.White else com.example.ui.theme.CleanTextSecondary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(viewModel.getLocalizedText("light_theme"), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                Button(
                                    onClick = { viewModel.setTheme(true) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.isDarkTheme) com.example.ui.theme.CleanBlue else Color.Transparent,
                                        contentColor = if (state.isDarkTheme) Color.White else com.example.ui.theme.CleanTextSecondary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(viewModel.getLocalizedText("dark_theme"), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        // 2. Language selection row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = viewModel.getLocalizedText("select_lang"),
                                fontSize = 14.sp,
                                color = com.example.ui.theme.CleanTextSecondary
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(4.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.setLanguage(context, "ru") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.language == "ru") com.example.ui.theme.CleanBlue else Color.Transparent,
                                        contentColor = if (state.language == "ru") Color.White else com.example.ui.theme.CleanTextSecondary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("RU", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.setLanguage(context, "en") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.language == "en") com.example.ui.theme.CleanBlue else Color.Transparent,
                                        contentColor = if (state.language == "en") Color.White else com.example.ui.theme.CleanTextSecondary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("EN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(44.dp))

                // Play custom transition animation triggers onwards
                Button(
                    onClick = onStartTransition,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("onboarding_complete_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.CleanBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text(
                        text = viewModel.getLocalizedText("get_started"),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    state: RootCheckerState,
    viewModel: RootCheckerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = viewModel.getLocalizedText("settings_btn"),
                fontWeight = FontWeight.Bold,
                color = com.example.ui.theme.CleanTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Theme parameter option
                Column {
                    Text(
                        text = viewModel.getLocalizedText("select_theme"),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = com.example.ui.theme.CleanTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!state.isDarkTheme) com.example.ui.theme.CleanBlue else Color.Transparent)
                                .clickable { viewModel.setTheme(false) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = viewModel.getLocalizedText("light_theme"),
                                fontWeight = FontWeight.Medium,
                                color = if (!state.isDarkTheme) Color.White else com.example.ui.theme.CleanTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (state.isDarkTheme) com.example.ui.theme.CleanBlue else Color.Transparent)
                                .clickable { viewModel.setTheme(true) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = viewModel.getLocalizedText("dark_theme"),
                                fontWeight = FontWeight.Medium,
                                color = if (state.isDarkTheme) Color.White else com.example.ui.theme.CleanTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Language switching option
                Column {
                    Text(
                        text = viewModel.getLocalizedText("select_lang"),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = com.example.ui.theme.CleanTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (state.language == "ru") com.example.ui.theme.CleanBlue else Color.Transparent)
                                .clickable { viewModel.setLanguage(context, "ru") }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = viewModel.getLocalizedText("lang_ru"),
                                fontWeight = FontWeight.Medium,
                                color = if (state.language == "ru") Color.White else com.example.ui.theme.CleanTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (state.language == "en") com.example.ui.theme.CleanBlue else Color.Transparent)
                                .clickable { viewModel.setLanguage(context, "en") }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = viewModel.getLocalizedText("lang_en"),
                                fontWeight = FontWeight.Medium,
                                color = if (state.language == "en") Color.White else com.example.ui.theme.CleanTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.CleanBlue),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = viewModel.getLocalizedText("close_btn"),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.testTag("settings_dialog")
    )
}

@Composable
fun StatusOverviewPanel(
    state: RootCheckerState,
    viewModel: RootCheckerViewModel,
    onStartScan: () -> Unit
) {
    // Colors matching state with clean minimalism palette
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val statusColor = when (state.overallStatus) {
        OverallDeviceStatus.UNSCANNED -> com.example.ui.theme.CleanOutline
        OverallDeviceStatus.SECURE -> com.example.ui.theme.CleanGreen
        OverallDeviceStatus.SUSPICIOUS -> com.example.ui.theme.CleanOrange
        OverallDeviceStatus.ROOTED -> com.example.ui.theme.CleanRed
    }

    val circleBgColor = when (state.overallStatus) {
        OverallDeviceStatus.UNSCANNED -> MaterialTheme.colorScheme.surface
        OverallDeviceStatus.SECURE -> com.example.ui.theme.CleanGreenContainer
        OverallDeviceStatus.SUSPICIOUS -> com.example.ui.theme.CleanOrangeContainer
        OverallDeviceStatus.ROOTED -> com.example.ui.theme.CleanRedContainer
    }

    val statusIcon = when (state.overallStatus) {
        OverallDeviceStatus.UNSCANNED -> Icons.Default.Search
        OverallDeviceStatus.SECURE -> Icons.Default.Check
        OverallDeviceStatus.SUSPICIOUS -> Icons.Default.Warning
        OverallDeviceStatus.ROOTED -> Icons.Default.Lock
    }

    val mainStatusText = when {
        state.isScanning -> if (state.language == "en") "Scanning Device..." else "Выполняется сканирование..."
        state.overallStatus == OverallDeviceStatus.UNSCANNED -> viewModel.getLocalizedText("device_unscanned")
        state.overallStatus == OverallDeviceStatus.SECURE -> viewModel.getLocalizedText("device_secure")
        state.overallStatus == OverallDeviceStatus.SUSPICIOUS -> viewModel.getLocalizedText("device_suspicious")
        state.overallStatus == OverallDeviceStatus.ROOTED -> viewModel.getLocalizedText("device_rooted")
        else -> ""
    }

    val descStatusText = when {
        state.isScanning -> {
            if (state.language == "en") "Analyzing kernel binaries, environment build signatures and system partitions."
            else "Анализируем бинарные файлы, окружение сборки и права доступа."
        }
        state.overallStatus == OverallDeviceStatus.UNSCANNED -> viewModel.getLocalizedText("overall_status_sub_unscanned")
        state.overallStatus == OverallDeviceStatus.SECURE -> viewModel.getLocalizedText("overall_status_sub_secure")
        state.overallStatus == OverallDeviceStatus.SUSPICIOUS -> viewModel.getLocalizedText("overall_status_sub_suspicious")
        state.overallStatus == OverallDeviceStatus.ROOTED -> viewModel.getLocalizedText("overall_status_sub_rooted")
        else -> ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Sleek minimalist Central Icon Circle (w-24 h-24 -> 96.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(circleBgColor)
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = "Status Icon",
                    tint = statusColor,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = mainStatusText,
                color = com.example.ui.theme.CleanTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = descStatusText,
                color = com.example.ui.theme.CleanTextSecondary,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }
    }
}

@Composable
fun SuRequestFeedbackBanner(
    granted: Boolean,
    viewModel: RootCheckerViewModel
) {
    val containerCol = if (granted) com.example.ui.theme.CleanRedContainer else com.example.ui.theme.CleanSurface
    val textCol = if (granted) com.example.ui.theme.CleanRed else com.example.ui.theme.CleanTextSecondary
    val iconColor = if (granted) com.example.ui.theme.CleanRed else com.example.ui.theme.CleanOutline
    val textMessage = if (granted) {
        viewModel.getLocalizedText("su_granted_banner_title")
    } else {
        viewModel.getLocalizedText("su_denied_banner_title")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerCol),
        border = BorderStroke(1.dp, if (granted) com.example.ui.theme.CleanRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.Warning else Icons.Default.Lock,
                contentDescription = "su info icon",
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = textMessage,
                color = textCol,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun CheckItemCard(
    check: CheckItem,
    viewModel: RootCheckerViewModel
) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when (check.status) {
        CheckStatus.NOT_RUN -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        CheckStatus.SECURE -> com.example.ui.theme.CleanGreen
        CheckStatus.SUSPICIOUS -> com.example.ui.theme.CleanOrange
        CheckStatus.ROOT_FOUND -> com.example.ui.theme.CleanRed
    }

    val statusIcon = when (check.status) {
        CheckStatus.NOT_RUN -> Icons.Default.Info
        CheckStatus.SECURE -> Icons.Default.Check
        CheckStatus.SUSPICIOUS -> Icons.Default.Warning
        CheckStatus.ROOT_FOUND -> Icons.Default.Close
    }

    // Dynamic strings based on language selection
    val isEn = viewModel.state.value.language == "en"
    val displayTitle = if (isEn) check.titleEn else check.titleRu
    val displayDesc = if (isEn) check.descEn else check.descRu

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Sleek Clean white box / light gray depending on theme for status icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (viewModel.state.value.isDarkTheme) Color(0xFF1E1F28) else Color.White)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = "Check Status Icon",
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Titles with minimalist styling
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = displayTitle,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = com.example.ui.theme.CleanTextPrimary
                    )
                    Text(
                        text = displayDesc,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = com.example.ui.theme.CleanTextSecondary,
                        lineHeight = 16.sp,
                        maxLines = if (expanded) 5 else 1
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand Indicator",
                    tint = com.example.ui.theme.CleanTextSecondary
                )
            }

            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Extra Tech Info Block
                Column {
                    Text(
                        text = viewModel.getLocalizedText("tech_label"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.CleanOutline
                    )
                    Text(
                        text = "${check.titleEn} — ${check.descEn}",
                        fontSize = 12.sp,
                        color = com.example.ui.theme.CleanTextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = viewModel.getLocalizedText("details_label"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.CleanOutline
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (viewModel.state.value.isDarkTheme) Color(0xFF1E1F28) else Color.White,
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (check.status == CheckStatus.NOT_RUN) {
                                viewModel.getLocalizedText("not_run_details")
                            } else {
                                check.details
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (check.status == CheckStatus.ROOT_FOUND) com.example.ui.theme.CleanRed else com.example.ui.theme.CleanTextPrimary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalConsole(
    logs: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    viewModel: RootCheckerViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1017)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = viewModel.getLocalizedText("terminal_placeholder"),
                    color = Color(0xFF9CA3AF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    val color = when {
                        log.contains("❌") || log.contains("THREAT") || log.contains("ОБНАРУЖЕН") -> Color(0xFFF87171)
                        log.contains("⚠️") || log.contains("Warning") || log.contains("Предупреждение") -> Color(0xFFFBBF24)
                        log.contains("✔️") -> Color(0xFF34D399)
                        log.contains("===") -> Color(0xFF60A5FA)
                        else -> Color(0xFF00FF66) // Hacker green
                    }

                    Text(
                        text = log,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EducationBlock(
    viewModel: RootCheckerViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = com.example.ui.theme.CleanBlueContainer.copy(alpha = 0.2f)
        ),
        border = BorderStroke(1.dp, com.example.ui.theme.CleanBlue.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "security info",
                    tint = com.example.ui.theme.CleanBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = viewModel.getLocalizedText("edu_header"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = com.example.ui.theme.CleanBlue
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = viewModel.getLocalizedText("edu_desc"),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = com.example.ui.theme.CleanTextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = viewModel.getLocalizedText("edu_risks_header"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = com.example.ui.theme.CleanTextPrimary
            )

            BulletItem(text = viewModel.getLocalizedText("edu_bullet_1"))
            BulletItem(text = viewModel.getLocalizedText("edu_bullet_2"))
            BulletItem(text = viewModel.getLocalizedText("edu_bullet_3"))
        }
    }
}

@Composable
fun BulletItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            fontWeight = FontWeight.Bold,
            color = com.example.ui.theme.CleanBlue,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = com.example.ui.theme.CleanTextSecondary
        )
    }
}
