package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.bluetooth.BmsConnectionState
import com.example.bluetooth.BmsTelemetry
import com.example.bluetooth.ScanDevice
import com.example.data.BatteryHistoryLog
import com.example.data.BatterySettings
import com.example.ui.BmsViewModel
import com.example.ui.components.BmsTelemetryChart
import com.example.ui.components.ChartMetric
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BmsMainScreen(viewModel: BmsViewModel) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val scannedDevices by viewModel.scannedDevices.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val historyLogs by viewModel.historyLogs.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var showDeveloperPage by remember { mutableStateOf(false) }

    val activityContext = androidx.compose.ui.platform.LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }

    // Intercept back button for developer screen
    androidx.activity.compose.BackHandler(enabled = showDeveloperPage) {
        showDeveloperPage = false
    }

    // Intercept back button for main screen to show exit dialog
    androidx.activity.compose.BackHandler(enabled = !showDeveloperPage) {
        showExitDialog = true
    }

    val statusText = when (connectionState) {
        BmsConnectionState.DISCONNECTED -> "Offline"
        BmsConnectionState.SCANNING -> "Scanning..."
        BmsConnectionState.CONNECTING -> "Connecting..."
        BmsConnectionState.CONNECTED -> "Online"
    }

    val statusColor = when (connectionState) {
        BmsConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error
        BmsConnectionState.SCANNING -> CyberBlue
        BmsConnectionState.CONNECTING -> CyberOrange
        BmsConnectionState.CONNECTED -> CyberGreen
    }

    if (showDeveloperPage) {
        BmsDeveloperProfileScreen(onBack = { showDeveloperPage = false })
    } else {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column {
                            Text(
                                text = "BMS MANAGER",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                letterSpacing = 1.5.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(statusColor, shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$statusText ${if (connectionState == BmsConnectionState.CONNECTED) "(${telemetry.connectedDeviceName})" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                },
                actions = {
                    val currentThemeMode = settingsState?.themeMode ?: "System"
                    val (themeIcon, nextModeLabel) = when (currentThemeMode) {
                        "Light" -> Pair(Icons.Default.LightMode, "Switch to Dark")
                        "Dark" -> Pair(Icons.Default.DarkMode, "Switch to System Auto")
                        else -> Pair(Icons.Default.BrightnessAuto, "Switch to Light")
                    }
                    IconButton(
                        onClick = {
                            val nextMode = when (currentThemeMode) {
                                "System" -> "Light"
                                "Light" -> "Dark"
                                else -> "System"
                            }
                            viewModel.updateTheme(nextMode)
                        },
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = themeIcon,
                            contentDescription = nextModeLabel,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { showDeveloperPage = true },
                        modifier = Modifier.testTag("developer_profile_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About Developer",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (connectionState == BmsConnectionState.CONNECTED) {
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Disconnect BMS",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RectangleShape),
                windowInsets = WindowInsets.navigationBars
            ) {
                val activeColor = MaterialTheme.colorScheme.primary
                val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
                val indicatorBg = MaterialTheme.colorScheme.secondaryContainer

                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dash", fontSize = 10.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = activeColor,
                        selectedTextColor = activeColor,
                        unselectedIconColor = inactiveColor,
                        unselectedTextColor = inactiveColor,
                        indicatorColor = indicatorBg
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.GridOn, contentDescription = "Cells") },
                    label = { Text("Cells", fontSize = 10.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = activeColor,
                        selectedTextColor = activeColor,
                        unselectedIconColor = inactiveColor,
                        unselectedTextColor = inactiveColor,
                        indicatorColor = indicatorBg
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = "Bluetooth") },
                    label = { Text("Connect", fontSize = 10.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = activeColor,
                        selectedTextColor = activeColor,
                        unselectedIconColor = inactiveColor,
                        unselectedTextColor = inactiveColor,
                        indicatorColor = indicatorBg
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Setup") },
                    label = { Text("System", fontSize = 10.sp, fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = activeColor,
                        selectedTextColor = activeColor,
                        unselectedIconColor = inactiveColor,
                        unselectedTextColor = inactiveColor,
                        indicatorColor = indicatorBg
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.History, contentDescription = "Logs") },
                    label = { Text("Stats", fontSize = 10.sp, fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = activeColor,
                        selectedTextColor = activeColor,
                        unselectedIconColor = inactiveColor,
                        unselectedTextColor = inactiveColor,
                        indicatorColor = indicatorBg
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val showLockScreen = connectionState == BmsConnectionState.CONNECTED && telemetry.isEncrypted && !telemetry.isAuthorized
            if (showLockScreen) {
                BmsPasscodeLockScreen(viewModel)
            } else {
                when (selectedTab) {
                    0 -> BmsDashboardTab(telemetry, settingsState, viewModel)
                    1 -> BmsCellsTab(telemetry, settingsState)
                    2 -> BmsBluetoothTab(connectionState, scannedDevices, viewModel)
                    3 -> BmsSetupTab(settingsState, viewModel, onDeveloperClick = { showDeveloperPage = true })
                    4 -> BmsAnalyticsTab(historyLogs, viewModel)
                }
            }
        }
    } }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text("Exit OpenBMS?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to exit the Battery Management System? Any active monitoring session will be disconnected.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        (activityContext as? android.app.Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("exit_btn_confirm")
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitDialog = false },
                    modifier = Modifier.testTag("exit_btn_cancel")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// TAB 0: DASHBOARD OVERVIEW
// -------------------------------------------------------------
@Composable
fun BmsDashboardTab(
    telemetry: BmsTelemetry,
    settings: BatterySettings?,
    viewModel: BmsViewModel,
    onViewLogsClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Warning Banners (Alarm Codes)
        if (telemetry.connectionState == BmsConnectionState.CONNECTED && telemetry.activeAlerts.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Protection Warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "BMS Protection Triggered (${telemetry.activeAlerts.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    telemetry.activeAlerts.forEach { alert ->
                        Text(
                            text = "• $alert",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        if (telemetry.connectionState != BmsConnectionState.CONNECTED) {
            // Offline Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothDisabled,
                        contentDescription = "Disconnected",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "BMS Device Disconnected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Please go to the 'Connect' tab to pair a Bluetooth BMS or run the Demo Simulator.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
            return
        }

        // 1. Prominent State of Charge Card (bg: E1E2EC, rounded-3xl, shadow)
        StateOfChargeCard(telemetry = telemetry)

        // 2. High Density 2x2 Telemetry Grid (bg: White, border: C7C6D0, rounded-2xl)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val cellVoltages = telemetry.cellVoltages
                val avgCell = if (cellVoltages.isNotEmpty()) cellVoltages.average() else 3.280
                MetricGridCard(
                    label = "Voltage",
                    value = "${String.format("%.2f", telemetry.totalVoltage)} V",
                    subtext = "Avg Cell: ${String.format("%.3f", avgCell)}V",
                    subtextColor = Color(0xFF16A34A), // green-600
                    modifier = Modifier.testTag("voltage_card")
                )

                val packTemp = telemetry.temperatures.getOrNull(0) ?: 28.4f
                val t1 = telemetry.temperatures.getOrNull(1) ?: 28.2f
                val t2 = telemetry.temperatures.getOrNull(2) ?: 28.6f
                MetricGridCard(
                    label = "Temperature",
                    value = "${String.format("%.1f", packTemp)} °C",
                    subtext = "T1: ${String.format("%.1f", t1)} / T2: ${String.format("%.1f", t2)}",
                    subtextColor = Color(0xFF2563EB), // blue-600
                    modifier = Modifier.testTag("temperature_card")
                )
            }

            // Right Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val currentSign = if (telemetry.current > 0) "+" else ""
                val currentText = "$currentSign${String.format("%.1f", telemetry.current)} A"
                val stateTxt = when {
                    telemetry.isCharging -> "Charging"
                    telemetry.isDischarging -> "Discharging"
                    else -> "Idle"
                }
                MetricGridCard(
                    label = "Current",
                    value = currentText,
                    subtext = stateTxt,
                    subtextColor = if (telemetry.current < -0.1f) Color(0xFFDC2626) else if (telemetry.current > 0.1f) Color(0xFF16A34A) else Color(0xFF44474E),
                    modifier = Modifier.testTag("current_card")
                )

                val powerWatts = telemetry.totalVoltage * telemetry.current
                val estHours = if (telemetry.current < -0.1f) {
                    val h = telemetry.capacityAh / Math.abs(telemetry.current)
                    val hrs = h.toInt()
                    val mins = ((h - hrs) * 60).toInt()
                    "${hrs}h ${mins}m"
                } else if (telemetry.current > 0.1f) {
                    val remainingToFill = maxOf(0f, 100f - telemetry.capacityAh)
                    val h = remainingToFill / telemetry.current
                    val hrs = h.toInt()
                    val mins = ((h - hrs) * 60).toInt()
                    "${hrs}h ${mins}m"
                } else {
                    "--"
                }
                val powerSubtext = if (telemetry.current < -0.1f) "Est. Time: $estHours" else if (telemetry.current > 0.1f) "Time to full: $estHours" else "Est. Time: --"
                MetricGridCard(
                    label = "Power Out",
                    value = "${String.format("%.1f", Math.abs(powerWatts))} W",
                    subtext = powerSubtext,
                    modifier = Modifier.testTag("power_card")
                )
            }
        }

        // 3. Power Control Card (bg: White, border: C7C6D0, rounded-3xl)
        PowerControlCard(telemetry = telemetry, settings = settings, viewModel = viewModel)

        // 4. System Logs Card (bg: F1F0F4, rounded-2xl)
        SystemLogsCard(logs = viewModel.historyLogs.value, onViewDetailedHistory = onViewLogsClick)
    }
}

@Composable
fun StateOfChargeCard(telemetry: BmsTelemetry) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "State of Charge",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "${telemetry.soc.toInt()}",
                        style = MaterialTheme.typography.displayMedium.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        ),
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = 2.dp)
                    )
                }
            }

            // Circular progress gauge using theme tokens
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                val bgCircleColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                val arcColor = MaterialTheme.colorScheme.onPrimaryContainer

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2.0f
                    val strokeWidth = 8.dp.toPx()
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)

                    // Draw Background Circle
                    drawCircle(
                        color = bgCircleColor,
                        radius = radius - strokeWidth / 2f,
                        center = centerOffset,
                        style = Stroke(width = strokeWidth)
                    )

                    // Draw Colored SOC Arc
                    val sweepAngle = (telemetry.soc / 100f) * 360f
                    drawArc(
                        color = arcColor,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Charging bolt status",
                    tint = arcColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun MetricGridCard(
    label: String,
    value: String,
    subtext: String,
    subtextColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = subtextColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp
                )
            )
        }
    }
}

@Composable
fun PowerControlCard(
    telemetry: BmsTelemetry,
    settings: BatterySettings?,
    viewModel: BmsViewModel
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Power Control",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                )

                val chemistry = settings?.chemistry ?: "LiFePO4"
                val cellCount = settings?.cellCount ?: 8
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$chemistry (${cellCount}S)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Charge Control Switch
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Charge",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    )
                    Switch(
                        checked = telemetry.chargeSwitchOn,
                        onCheckedChange = { viewModel.toggleChargeSwitch(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("charge_switch")
                    )
                }

                // Discharge Control Switch
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discharge",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    )
                    Switch(
                        checked = telemetry.dischargeSwitchOn,
                        onCheckedChange = { viewModel.toggleDischargeSwitch(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("discharge_switch")
                    )
                }
            }
        }
    }
}

@Composable
fun SystemLogsCard(
    logs: List<BatteryHistoryLog>,
    onViewDetailedHistory: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM LOGS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
                TextButton(
                    onClick = onViewDetailedHistory,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(
                        text = "View Detailed History",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "No system logs available yet.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )
                } else {
                    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                    val recentLogs = logs.takeLast(4).reversed()
                    recentLogs.forEach { log ->
                        val timeStr = timeFormat.format(Date(log.timestamp))
                        val logText = when {
                            log.current > 0.1f -> "Charging current: ${String.format("%.1f", log.current)}A"
                            log.current < -0.1f -> "Discharging load: ${String.format("%.1f", Math.abs(log.current))}A"
                            else -> "BMS idle - Volt: ${String.format("%.1f", log.voltage)}V"
                        }
                        val logColor = when {
                            log.current > 0.1f -> CyberGreen
                            log.current < -0.1f -> CyberRed
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                )
                                Text(
                                    text = logText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = logColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: INDIVIDUAL CELL STATUS
// -------------------------------------------------------------
@Composable
fun BmsCellsTab(telemetry: BmsTelemetry, settings: BatterySettings?) {
    if (telemetry.connectionState != BmsConnectionState.CONNECTED) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Please connect to a BMS device to view cell status.")
        }
        return
    }

    val cellVoltages = telemetry.cellVoltages
    val balancing = telemetry.cellBalancing

    val maxCell = cellVoltages.maxOrNull() ?: 0f
    val minCell = cellVoltages.minOrNull() ?: 0f
    val delta = maxOf(0f, maxCell - minCell)

    val maxCellIndex = cellVoltages.indexOf(maxCell) + 1
    val minCellIndex = cellVoltages.indexOf(minCell) + 1

    val maxVoltageThreshold = settings?.cellMaxVoltage ?: 3.65f
    val minVoltageThreshold = settings?.cellMinVoltage ?: 2.50f
    val span = maxOf(0.1f, maxVoltageThreshold - minVoltageThreshold)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cell Overview Analytics
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("HIGHEST CELL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Cell #$maxCellIndex",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        "${String.format("%.3f", maxCell)}V",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CyberGreen
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("LOWEST CELL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Cell #$minCellIndex",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        "${String.format("%.3f", minCell)}V",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CyberBlue
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("CELL DELTA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (delta > 0.05f) "High Imbalance" else "Balanced",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (delta > 0.05f) CyberRed else CyberGreen
                    )
                    Text(
                        "${String.format("%.3f", delta)}V",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (delta > 0.05f) CyberRed else CyberGreen
                    )
                }
            }
        }

        Text(
            text = "INDIVIDUAL CELL VOLTAGES",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Cell Voltage bar charts
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            cellVoltages.forEachIndexed { idx, volts ->
                val isHighest = (volts == maxCell)
                val isLowest = (volts == minCell)
                val isBalancing = balancing.getOrNull(idx) ?: false

                val fillFraction = ((volts - minVoltageThreshold) / span).coerceIn(0f, 1f)

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Cell #${idx + 1}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(55.dp)
                        )

                        // Progress representation of Voltage
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraSmall)
                        ) {
                            val barColor = when {
                                isHighest -> CyberGreen
                                isLowest -> CyberBlue
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fillFraction)
                                    .background(barColor, shape = MaterialTheme.shapes.extraSmall)
                            )
                        }

                        // Cell voltage label and balancing indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.width(85.dp)
                        ) {
                            Text(
                                text = "${String.format("%.3f", volts)}V",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isHighest) CyberGreen else if (isLowest) CyberBlue else MaterialTheme.colorScheme.onSurface
                            )

                            if (isBalancing) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                                    contentDescription = "Active Balancing",
                                    tint = CyberOrange,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .rotate(35f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: BLUETOOTH & SIMULATOR DECK
// -------------------------------------------------------------
@Composable
fun BmsBluetoothTab(
    connectionState: BmsConnectionState,
    scannedDevices: List<ScanDevice>,
    viewModel: BmsViewModel
) {
    var isSimChargerOn by remember { mutableStateOf(false) }
    var isSimLoadOn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "BLUETOOTH CONNECTIVITY",
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )

        // Scanning Card Controls
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Search for Hardware BMS",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
                Text(
                    text = "Ensure your lithium pack's BLE dongle is powered on and within 10 meters of your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (connectionState == BmsConnectionState.SCANNING) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (connectionState == BmsConnectionState.SCANNING) {
                        Button(
                            onClick = { viewModel.stopScan() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Stop Scanning", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startScan() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scan_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan BLE Devices", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Devices Scanned List
        Text(
            text = "AVAILABLE HARDWARE",
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )

        if (scannedDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No BLE signals found. Click Scan to search.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (dev in scannedDevices) {
                    val isDemo = dev.address == "00:11:22:33:44:FF"
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDemo) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDemo) Icons.Default.DeveloperBoard else Icons.Default.Bluetooth,
                                    contentDescription = "Device info",
                                    tint = if (isDemo) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = dev.name,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDemo) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = dev.address,
                                        fontSize = 11.sp,
                                        color = if (isDemo) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${dev.rssi} dBm",
                                    fontSize = 11.sp,
                                    color = if (isDemo) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { viewModel.connectDevice(dev.address) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDemo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = if (isDemo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("connect_${dev.address.replace(":", "_")}")
                                ) {
                                    Text("Pair", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Demo Control Deck
        Text(
            text = "DEMO PHYSICS CONTROL BENCH",
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Test and Simulate BMS Alarms Offline",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
                Text(
                    text = "If you are connected to the Demo Simulator BMS, these switches simulate dynamic environment elements to prove the safety and alerting capabilities of the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Connect 120V Grid Charger", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Text("Plugs in simulated 25A charger", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isSimChargerOn,
                        onCheckedChange = {
                            isSimChargerOn = it
                            viewModel.setSimulatedCharger(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("simulate_charger_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Engage High Current Inverter Load", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Text("Simulates 45A discharging load", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isSimLoadOn,
                        onCheckedChange = {
                            isSimLoadOn = it
                            viewModel.setSimulatedLoad(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("simulate_load_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text("Instant Protection Triggers", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.triggerSimulatedCellImbalance() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Imbalance cells", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.triggerSimulatedHighTemp() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Overtemp (62°C)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: SETUP, CALIBRATION & CHEMISTRY
// -------------------------------------------------------------
@Composable
fun BmsSetupTab(
    settings: BatterySettings?,
    viewModel: BmsViewModel,
    onDeveloperClick: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    val currentChemistry = settings?.chemistry ?: "LiFePO4"
    val cellCount = settings?.cellCount ?: 8
    val nominalCapacity = settings?.nominalCapacityAh ?: 100f

    var selectedChemistry by remember(currentChemistry) { mutableStateOf(currentChemistry) }
    var inputCellCount by remember(cellCount) { mutableStateOf(cellCount.toString()) }
    var inputCapacity by remember(nominalCapacity) { mutableStateOf(nominalCapacity.toString()) }

    var sliderMaxTemp by remember(settings) { mutableStateOf(settings?.maxTempAlertThreshold ?: 55f) }
    var sliderMinTemp by remember(settings) { mutableStateOf(settings?.minTempAlertThreshold ?: -5f) }
    var sliderMaxCurrent by remember(settings) { mutableStateOf(settings?.maxCurrentAlertThreshold ?: 80f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "BATTERY PROFILE CALIBRATION",
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )

        // Chemistry configuration Selectors
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Select Chemistry Profile",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )

                val profiles = listOf("LiFePO4", "Li-Ion", "LTO", "Custom")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    profiles.forEach { profile ->
                        val isSelected = selectedChemistry == profile
                        Button(
                            onClick = { selectedChemistry = profile },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(profile, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Threshold indicator
                val voltsText = when (selectedChemistry) {
                    "Li-Ion" -> "Preset: NMC Chemistry (3.0V - 4.2V limits)"
                    "LiFePO4" -> "Preset: Solar LFP Chemistry (2.5V - 3.65V limits)"
                    "LTO" -> "Preset: Fast Charge Titanate (1.5V - 2.8V limits)"
                    else -> "Manual Custom thresholds (modify as needed)"
                }

                Text(voltsText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = inputCellCount,
                    onValueChange = { inputCellCount = it },
                    label = { Text("Cell Series Count (S)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cell_count_input")
                )

                OutlinedTextField(
                    value = inputCapacity,
                    onValueChange = { inputCapacity = it },
                    label = { Text("Nominal Pack Capacity (Ah)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("capacity_input")
                )

                Button(
                    onClick = {
                        keyboardController?.hide()
                        val cells = inputCellCount.toIntOrNull() ?: 8
                        val capacity = inputCapacity.toFloatOrNull() ?: 100f
                        viewModel.updateChemistry(selectedChemistry, cells, capacity)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_profile_button")
                ) {
                    Text("Apply Profile Parameters", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Safety Alerts sliders
        Text(
            text = "SAFETY LIMIT ALERT PARAMETERS",
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Max Temp
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("High Temperature Protection", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Text("${sliderMaxTemp.toInt()}°C", color = CyberRed, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = sliderMaxTemp,
                        onValueChange = { sliderMaxTemp = it },
                        valueRange = 35f..80f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Min Temp
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Low Temperature Charge Lock", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Text("${sliderMinTemp.toInt()}°C", color = CyberBlue, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = sliderMinTemp,
                        onValueChange = { sliderMinTemp = it },
                        valueRange = -20f..15f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Max safety current
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Overcurrent Tripping Relay", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Text("${sliderMaxCurrent.toInt()} A", color = CyberOrange, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = sliderMaxCurrent,
                        onValueChange = { sliderMaxCurrent = it },
                        valueRange = 10f..150f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                Button(
                    onClick = {
                        viewModel.updateSafetyLimits(sliderMaxTemp, sliderMinTemp, sliderMaxCurrent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Save Safety Alert Parameters", fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- BMS SECURITY & ENCRYPTION ---
        Text(
            text = "BMS HANDSHAKE PROTOCOL SECURITY",
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: Encrypt/Decrypt BMS
                var showConfirmDialog by remember { mutableStateOf(false) }
                var confirmPassword by remember { mutableStateOf("") }
                var confirmErrorMessage by remember { mutableStateOf("") }
                var successMessage by remember { mutableStateOf("") }
                var generalErrorMessage by remember { mutableStateOf("") }

                val isEncrypted = settings?.isBmsEncrypted ?: false

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = if (isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Encryption State",
                                tint = if (isEncrypted) CyberGreen else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Encrypt BMS Telemetry",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isEncrypted) "AES Handshake Active. Pack telemetry is locked upon connection." else "Unencrypted. Telemetry streams freely to anyone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = isEncrypted,
                        onCheckedChange = {
                            confirmPassword = ""
                            confirmErrorMessage = ""
                            showConfirmDialog = true
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("encrypt_bms_switch")
                    )
                }

                if (showConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showConfirmDialog = false },
                        title = { Text(if (isEncrypted) "Decrypt BMS" else "Encrypt BMS", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (isEncrypted) "Enter your current security password to disable encryption on this BMS."
                                    else "Enter your current security password to activate high-security encryption on this BMS.",
                                    fontSize = 13.sp
                                )
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it; confirmErrorMessage = "" },
                                    label = { Text("Confirm Password") },
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("confirm_password_dialog_input")
                                )
                                if (confirmErrorMessage.isNotEmpty()) {
                                    Text(confirmErrorMessage, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.toggleBmsEncryption(!isEncrypted, confirmPassword) { success, msg ->
                                        if (success) {
                                            successMessage = msg
                                            showConfirmDialog = false
                                        } else {
                                            confirmErrorMessage = msg
                                        }
                                    }
                                }
                            ) {
                                Text("Confirm", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmDialog = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Section: Change password
                Text(
                    text = "Change Passcode",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )

                var oldPasswordInput by remember { mutableStateOf("") }
                var newPasswordInput by remember { mutableStateOf("") }
                var confirmNewPasswordInput by remember { mutableStateOf("") }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = oldPasswordInput,
                        onValueChange = { oldPasswordInput = it; generalErrorMessage = ""; successMessage = "" },
                        label = { Text("Current Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("old_password_input")
                    )

                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it; generalErrorMessage = ""; successMessage = "" },
                        label = { Text("New Passcode (4+ digits)") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("new_password_input")
                    )

                    OutlinedTextField(
                        value = confirmNewPasswordInput,
                        onValueChange = { confirmNewPasswordInput = it; generalErrorMessage = ""; successMessage = "" },
                        label = { Text("Confirm New Passcode") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("confirm_new_password_input")
                    )

                    if (successMessage.isNotEmpty()) {
                        Text(successMessage, color = CyberGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (generalErrorMessage.isNotEmpty()) {
                        Text(generalErrorMessage, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (newPasswordInput != confirmNewPasswordInput) {
                                generalErrorMessage = "New passcodes do not match"
                                return@Button
                            }
                            viewModel.changeBmsPassword(oldPasswordInput, newPasswordInput) { success, msg ->
                                if (success) {
                                    successMessage = msg
                                    oldPasswordInput = ""
                                    newPasswordInput = ""
                                    confirmNewPasswordInput = ""
                                } else {
                                    generalErrorMessage = msg
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().testTag("update_password_button")
                    ) {
                        Text("Update Security Passcode", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- DEVELOPER BRANDING ACCESS CARD ---
        Text(
            text = "DEVELOPER PROFILE",
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )

        Card(
            onClick = onDeveloperClick,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                .testTag("developer_profile_card_button")
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AXYAN LABS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "OpenBMS Utility • Version 1.0.1",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View Profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 4: ANALYTICS, CHARTS & TERMINAL LOGS
// -------------------------------------------------------------
@Composable
fun BmsAnalyticsTab(logs: List<BatteryHistoryLog>, viewModel: BmsViewModel) {
    var selectedMetric by remember { mutableStateOf(ChartMetric.SOC) }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "HISTORICAL METRICS & CHARTS",
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )

        // Filter chips for Chart
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val metrics = listOf(
                ChartMetric.SOC to "SOC",
                ChartMetric.VOLTAGE to "Volt",
                ChartMetric.CURRENT to "Curr",
                ChartMetric.TEMPERATURE to "Temp"
            )

            metrics.forEach { (met, label) ->
                val isSelected = selectedMetric == met
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedMetric = met },
                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Render Native Glowing Chart
        BmsTelemetryChart(
            logs = logs,
            metric = selectedMetric,
            modifier = Modifier.fillMaxWidth()
        )

        // SQLite Logging statistics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SQL DATABASE RECORDS (${logs.size}/200)",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            )

            TextButton(
                onClick = { viewModel.clearHistory() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Clear logs")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear logs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // SQLite History records list
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saved logs in SQLite. Connect to simulation BMS to stream and save telemetry.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    for (log in logs) {
                        val timeStr = timeFormat.format(Date(log.timestamp))
                        val stateTxt = if (log.current > 0.1f) "CHG" else if (log.current < -0.1f) "DSG" else "IDL"
                        val stateColor = if (log.current > 0.1f) CyberGreen else if (log.current < -0.1f) CyberOrange else Color.Gray

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = timeStr,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1.3f)
                            )

                            Row(
                                modifier = Modifier.weight(2f),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Badge(
                                    containerColor = stateColor,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(stateTxt, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }

                                Text(
                                    text = "${log.soc.toInt()}% | ${String.format("%.1f", log.voltage)}V | ${String.format("%.1f", log.current)}A | ${String.format("%.1f", log.temperaturePack)}°C",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Right
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun BmsPasscodeLockScreen(viewModel: BmsViewModel) {
    var passwordInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1016)) // Dark cyber background
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E202C)),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(28.dp))
                .border(1.dp, Color(0xFF3B3F58), RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Secure Glowing Shield/Lock Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFFE22134).copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, Color(0xFFE22134), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "BMS Locked",
                        tint = Color(0xFFE22134),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "BMS SECURITY HANDSHAKE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.2.sp
                )

                Text(
                    text = "This battery pack's communication channel is encrypted. Enter the connection passcode to authenticate and enable cell telemetry.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF9EA3BA),
                    lineHeight = 18.sp
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        errorMessage = ""
                    },
                    label = { Text("BMS Passcode", color = Color(0xFF9EA3BA)) },
                    placeholder = { Text("Default: 123456", color = Color(0xFF9EA3BA).copy(alpha = 0.5f)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4259A7),
                        unfocusedBorderColor = Color(0xFF3B3F58),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF4259A7)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bms_passcode_input")
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE22134),
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.authorizeBms(passwordInput) { success ->
                            if (!success) {
                                errorMessage = "Invalid password. Access Denied."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4259A7),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("bms_authorize_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = "Decrypt")
                        Text("Decrypt Telemetry", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DEDICATED PAGE: DEVELOPER DETAILS & PROFILE
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BmsDeveloperProfileScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val appVersion = BuildConfig.VERSION_NAME

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "DEVELOPER PROFILE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("developer_profile_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF070B19)
                )
            )
        },
        containerColor = Color(0xFF070B19)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF070B19))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Beautiful AXYAN LABS Original SVG Canvas Logo!
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(24.dp)) // Slate-900
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp)), // Slate-800
                contentAlignment = Alignment.Center
            ) {
                // Pulsing neon backing halo
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF10B981).copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                )

                Canvas(
                    modifier = Modifier
                        .size(80.dp)
                        .testTag("axyan_labs_logo_canvas")
                ) {
                    val scale = size.width / 24f

                    // Linear Gradient from top-left (0%, 0%) to bottom-right (100%, 100%)
                    val axGradLoader = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF10B981), // Emerald
                            0.5f to Color(0xFF06B6D4), // Cyan
                            1.0f to Color(0xFF4F46E5)  // Indigo
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )

                    // 1. Path 1: Arch 'A' (M4 19L11 5C11.3 4.4 12.7 4.4 13 5L20 19)
                    val archPath = Path().apply {
                        moveTo(4f * scale, 19f * scale)
                        lineTo(11f * scale, 5f * scale)
                        cubicTo(
                            x1 = 11.3f * scale, y1 = 4.4f * scale,
                            x2 = 12.7f * scale, y2 = 4.4f * scale,
                            x3 = 13f * scale, y3 = 5f * scale
                        )
                        lineTo(20f * scale, 19f * scale)
                    }
                    drawPath(
                        path = archPath,
                        brush = axGradLoader,
                        style = Stroke(
                            width = 2.5f * scale,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // 2. Path 2: Horizontal crossbar (M6 14.5H18)
                    val crossbarPath = Path().apply {
                        moveTo(6f * scale, 14.5f * scale)
                        lineTo(18f * scale, 14.5f * scale)
                    }
                    drawPath(
                        path = crossbarPath,
                        brush = axGradLoader,
                        style = Stroke(
                            width = 2.5f * scale,
                            cap = StrokeCap.Round
                        )
                    )

                    // 3. Path 3: Inner diagonal / cross slash (M15.5 10L8.5 17)
                    val diagonalPath = Path().apply {
                        moveTo(15.5f * scale, 10f * scale)
                        lineTo(8.5f * scale, 17f * scale)
                    }
                    drawPath(
                        path = diagonalPath,
                        brush = axGradLoader,
                        style = Stroke(
                            width = 2f * scale,
                            cap = StrokeCap.Round
                        )
                    )
                }
            }

            // Developer identity branding text matching image
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "AXYAN LABS",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 4.sp
                    )
                )
                Text(
                    text = "D  E  V  _  U  X  _  L  A  B   ✦",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00B0FF),
                        letterSpacing = 2.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Premium cards for contacts and websites
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Official Website Link Button
                Card(
                    onClick = {
                        try {
                            uriHandler.openUri("https://axyanlabs.qzz.io")
                        } catch (e: Exception) {
                            // Safe guard
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A35)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF00B0FF).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .testTag("visit_website_card")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(18.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF00B0FF).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Color(0xFF00B0FF)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Official Website",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "axyanlabs.qzz.io",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Official Email Button
                Card(
                    onClick = {
                        try {
                            uriHandler.openUri("mailto:contact@axyanlabs.qzz.io")
                        } catch (e: Exception) {
                            // Safe guard
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A35)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF00B0FF).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .testTag("send_email_card")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(18.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFFF9100).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = Color(0xFFFF9100)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Contact Email",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "contact@axyanlabs.qzz.io",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Minimalist version & platform footer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "OpenBMS Utility • Version $appVersion",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "Next-Generation Battery Intelligence Ecosystem",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.4f),
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
    }
}

