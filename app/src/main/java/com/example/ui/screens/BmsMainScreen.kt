package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Overview", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.GridOn, contentDescription = "Cells") },
                    label = { Text("Cells", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = "Bluetooth") },
                    label = { Text("Connect", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Setup") },
                    label = { Text("Calibrate", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.History, contentDescription = "Logs") },
                    label = { Text("Analytics", fontSize = 11.sp) }
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
            when (selectedTab) {
                0 -> BmsDashboardTab(telemetry, viewModel)
                1 -> BmsCellsTab(telemetry, settingsState)
                2 -> BmsBluetoothTab(connectionState, scannedDevices, viewModel)
                3 -> BmsSetupTab(settingsState, viewModel)
                4 -> BmsAnalyticsTab(historyLogs, viewModel)
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 0: DASHBOARD OVERVIEW
// -------------------------------------------------------------
@Composable
fun BmsDashboardTab(telemetry: BmsTelemetry, viewModel: BmsViewModel) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warning Banners (Alarm Codes)
        if (telemetry.connectionState == BmsConnectionState.CONNECTED && telemetry.activeAlerts.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = MaterialTheme.shapes.medium,
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

        // Realtime Visual gauges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Large circular SOC Gauge
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "charging")
                val angleOffset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "angleRotation"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2.0f
                    val strokeWidth = 14.dp.toPx()
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)

                    // Draw Background Gray Circle
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.15f),
                        radius = radius - strokeWidth,
                        center = centerOffset,
                        style = Stroke(width = strokeWidth)
                    )

                    // Draw Colored SOC Arc
                    val sweepAngle = (telemetry.soc / 100f) * 360f
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(CyberGreen, CyberBlue, CyberGreen)
                        ),
                        startAngle = if (telemetry.isCharging) angleOffset else -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(strokeWidth, strokeWidth),
                        size = Size(size.width - strokeWidth * 2f, size.height - strokeWidth * 2f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${telemetry.soc.toInt()}%",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = CyberGreen
                    )
                    Text(
                        text = "${String.format("%.1f", telemetry.capacityAh)} Ah",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // High priority parameters
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // State Indicator
                val stateText = when {
                    telemetry.isCharging -> "CHARGING"
                    telemetry.isDischarging -> "DISCHARGING"
                    else -> "IDLE"
                }
                val stateColor = when {
                    telemetry.isCharging -> CyberGreen
                    telemetry.isDischarging -> CyberOrange
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "STATUS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (telemetry.isCharging) Icons.Default.Bolt else Icons.Default.Power,
                                contentDescription = "Status",
                                tint = stateColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stateText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = stateColor
                            )
                        }
                    }
                }

                // Instant power consumption (W = V * A)
                val powerWatts = telemetry.totalVoltage * telemetry.current
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "INSTANT POWER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${String.format("%.1f", powerWatts)} W",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (powerWatts > 0) CyberGreen else if (powerWatts < 0) CyberOrange else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Telemetry values grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Total Voltage
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Gray.copy(alpha = 0.15f), MaterialTheme.shapes.medium)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("VOLTAGE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.ElectricBolt, contentDescription = "Voltage", tint = CyberGreen, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${String.format("%.2f", telemetry.totalVoltage)} V",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyberGreen
                    )
                }
            }

            // Total Current
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Gray.copy(alpha = 0.15f), MaterialTheme.shapes.medium)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CURRENT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.FlashOn, contentDescription = "Current", tint = CyberBlue, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${String.format("%.2f", telemetry.current)} A",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (telemetry.current > 0f) CyberGreen else if (telemetry.current < 0f) CyberOrange else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Temperature grid
        Text(
            text = "THERMAL SENSORS",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val packTemp = telemetry.temperatures.getOrNull(0) ?: 0f
            val ambTemp = telemetry.temperatures.getOrNull(1) ?: 0f
            val mosTemp = telemetry.temperatures.getOrNull(2) ?: 0f

            // Pack Temp
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("PACK CORE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${String.format("%.1f", packTemp)}°C",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (packTemp > 50f) CyberRed else CyberGreen
                    )
                }
            }

            // Amb Temp
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("AMBIENT", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${String.format("%.1f", ambTemp)}°C",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = CyberBlue
                    )
                }
            }

            // MOS Temp
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("MOSFET CORE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${String.format("%.1f", mosTemp)}°C",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (mosTemp > 75f) CyberRed else CyberGreen
                    )
                }
            }
        }

        // Charge/Discharge Switches (The physical Mosfet relays control)
        Text(
            text = "HARDWARE RELAY SWITCHES",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Gray.copy(alpha = 0.15f), MaterialTheme.shapes.medium)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Power,
                            contentDescription = "Charge switch",
                            tint = if (telemetry.chargeSwitchOn) CyberGreen else Color.Gray
                        )
                        Column {
                            Text("Charge Control MOSFET", fontWeight = FontWeight.Bold)
                            Text(
                                "Allow charging current into pack",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = telemetry.chargeSwitchOn,
                        onCheckedChange = { viewModel.toggleChargeSwitch(it) },
                        modifier = Modifier.testTag("charge_switch")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Power,
                            contentDescription = "Discharge switch",
                            tint = if (telemetry.dischargeSwitchOn) CyberGreen else Color.Gray
                        )
                        Column {
                            Text("Discharge Control MOSFET", fontWeight = FontWeight.Bold)
                            Text(
                                "Allow discharge output current from pack",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = telemetry.dischargeSwitchOn,
                        onCheckedChange = { viewModel.toggleDischargeSwitch(it) },
                        modifier = Modifier.testTag("discharge_switch")
                    )
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cell Overview Analytics
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("HIGHEST CELL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Cell #$maxCellIndex",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        .background(Color.Gray.copy(alpha = 0.3f))
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("LOWEST CELL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Cell #$minCellIndex",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        .background(Color.Gray.copy(alpha = 0.3f))
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("CELL DELTA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Gray.copy(alpha = 0.15f), MaterialTheme.shapes.small)
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
                            modifier = Modifier.width(55.dp)
                        )

                        // Progress representation of Voltage
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .background(Color.Gray.copy(alpha = 0.1f), shape = MaterialTheme.shapes.extraSmall)
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
                                    imageVector = Icons.Default.RotateLeft,
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "BLUETOOTH CONNECTIVITY",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Scanning Card Controls
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Gray.copy(alpha = 0.15f), MaterialTheme.shapes.medium)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Search for Hardware BMS",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ensure your lithium pack's BLE dongle is powered on and within 10 meters of your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (connectionState == BmsConnectionState.SCANNING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (connectionState == BmsConnectionState.SCANNING) {
                        Button(
                            onClick = { viewModel.stopScan() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Stop Scanning")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startScan() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scan_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan BLE Devices")
                        }
                    }
                }
            }
        }

        // Devices Scanned List
        Text(
            text = "AVAILABLE HARDWARE",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            containerColor = if (isDemo) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.Gray.copy(alpha = 0.15f), MaterialTheme.shapes.small)
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
                                    tint = if (isDemo) CyberGreen else CyberBlue
                                )
                                Column {
                                    Text(dev.name, fontWeight = FontWeight.Bold)
                                    Text(dev.address, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("${dev.rssi} dBm", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(
                                    onClick = { viewModel.connectDevice(dev.address) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDemo) CyberGreen else MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("connect_${dev.address.replace(":", "_")}")
                                ) {
                                    Text("Pair")
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
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Gray.copy(alpha = 0.15f), MaterialTheme.shapes.medium)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Test and Simulate BMS Alarms Offline",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
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
                        Text("Connect 120V Grid Charger", fontWeight = FontWeight.Medium)
                        Text("Plugs in simulated 25A charger", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isSimChargerOn,
                        onCheckedChange = {
                            isSimChargerOn = it
                            viewModel.setSimulatedCharger(it)
                        },
                        modifier = Modifier.testTag("simulate_charger_switch")
                    )
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Engage High Current Inverter Load", fontWeight = FontWeight.Medium)
                        Text("Simulates 45A discharging load", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isSimLoadOn,
                        onCheckedChange = {
                            isSimLoadOn = it
                            viewModel.setSimulatedLoad(it)
                        },
                        modifier = Modifier.testTag("simulate_load_switch")
                    )
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))

                Text("Instant Protection Triggers", fontWeight = FontWeight.Medium, fontSize = 13.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.triggerSimulatedCellImbalance() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Imbalance cells")
                    }

                    OutlinedButton(
                        onClick = { viewModel.triggerSimulatedHighTemp() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Overtemp (62°C)")
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
fun BmsSetupTab(settings: BatterySettings?, viewModel: BmsViewModel) {
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "BATTERY PROFILE CALIBRATION",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Chemistry configuration Selectors
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Gray.copy(alpha = 0.15f), MaterialTheme.shapes.medium)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Select Chemistry Profile", fontWeight = FontWeight.Bold)

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cell_count_input")
                )

                OutlinedTextField(
                    value = inputCapacity,
                    onValueChange = { inputCapacity = it },
                    label = { Text("Nominal Pack Capacity (Ah)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_profile_button")
                ) {
                    Text("Apply Profile Parameters")
                }
            }
        }

        // Safety Alerts sliders
        Text(
            text = "SAFETY LIMIT ALERT PARAMETERS",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Gray.copy(alpha = 0.15f), MaterialTheme.shapes.medium)
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
                        Text("High Temperature Protection", fontWeight = FontWeight.Medium)
                        Text("${sliderMaxTemp.toInt()}°C", color = CyberRed, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = sliderMaxTemp,
                        onValueChange = { sliderMaxTemp = it },
                        valueRange = 35f..80f
                    )
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))

                // Min Temp
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Low Temperature Charge Lock", fontWeight = FontWeight.Medium)
                        Text("${sliderMinTemp.toInt()}°C", color = CyberBlue, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = sliderMinTemp,
                        onValueChange = { sliderMinTemp = it },
                        valueRange = -20f..15f
                    )
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))

                // Max safety current
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Overcurrent Tripping Relay", fontWeight = FontWeight.Medium)
                        Text("${sliderMaxCurrent.toInt()} A", color = CyberOrange, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = sliderMaxCurrent,
                        onValueChange = { sliderMaxCurrent = it },
                        valueRange = 10f..150f
                    )
                }

                Button(
                    onClick = {
                        viewModel.updateSafetyLimits(sliderMaxTemp, sliderMinTemp, sliderMaxCurrent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Save Safety Alert Parameters")
                }
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "HISTORICAL METRICS & CHARTS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(
                onClick = { viewModel.clearHistory() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Clear logs")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear logs", fontSize = 12.sp)
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
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
                                    textAlign = TextAlign.Right
                                )
                            }
                        }
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}
