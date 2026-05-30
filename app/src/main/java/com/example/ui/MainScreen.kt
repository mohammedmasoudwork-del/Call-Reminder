package com.example.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CallRecord
import com.example.data.ReminderConfig
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ReminderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configState by viewModel.config.collectAsStateWithLifecycle()
    val callHistory by viewModel.callHistory.collectAsStateWithLifecycle()

    // Form states
    var contactName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var intervalDays by remember { mutableStateOf(1) }
    var snoozeMinutes by remember { mutableStateOf(30) }
    var minDurationSeconds by remember { mutableStateOf("30") }

    // Synchronize form states when database config loads
    LaunchedEffect(configState) {
        configState?.let {
            contactName = it.contactName
            phoneNumber = it.phoneNumber
            intervalDays = it.intervalDays
            snoozeMinutes = it.snoozeMinutes
            minDurationSeconds = it.minCallDurationSeconds.toString()
        }
    }

    // Permission handling launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val postNotif = permissions[android.Manifest.permission.POST_NOTIFICATIONS] ?: true
        val readCall = permissions[android.Manifest.permission.READ_CALL_LOG] ?: true

        if (!postNotif) {
            Toast.makeText(context, context.getString(R.string.permission_notif_denied), Toast.LENGTH_LONG).show()
        }
        if (!readCall) {
            Toast.makeText(context, context.getString(R.string.permission_call_log_denied), Toast.LENGTH_LONG).show()
        }
    }

    // Check and trigger permissions on launch
    LaunchedEffect(Unit) {
        val permissionsNeeded = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.READ_CALL_LOG)
        }
        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    // Process viewmodel toast channel events
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { event ->
            val message = when (event) {
                "settings_saved" -> context.getString(R.string.settings_saved)
                "phone_number_required" -> context.getString(R.string.phone_number_required)
                "reminder_enabled" -> context.getString(R.string.reminder_status_enabled)
                "reminder_disabled" -> context.getString(R.string.reminder_status_disabled)
                "call_confirmed_manual" -> context.getString(R.string.call_confirmed_manual)
                else -> event
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Enforce RTL Layout Direction explicitly for Arab support
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 24.sp
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier.testTag("app_bar")
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            val config = configState
            if (config == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding() + 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Current Status Dashboard
                    item {
                        DashboardCard(config = config)
                    }

                    // 2. Settings Config Block
                    item {
                        SettingsCard(
                            contactName = contactName,
                            onContactNameChange = { contactName = it },
                            phoneNumber = phoneNumber,
                            onPhoneNumberChange = { phoneNumber = it },
                            intervalDays = intervalDays,
                            onIntervalDaysChange = { intervalDays = it },
                            snoozeMinutes = snoozeMinutes,
                            onSnoozeMinutesChange = { snoozeMinutes = it },
                            minDurationSeconds = minDurationSeconds,
                            onMinDurationSecondsChange = { minDurationSeconds = it },
                            onSave = {
                                val dur = minDurationSeconds.toIntOrNull() ?: 30
                                viewModel.saveSettings(
                                    contactName = contactName,
                                    phoneNumber = phoneNumber,
                                    intervalDays = intervalDays,
                                    snoozeMinutes = snoozeMinutes,
                                    minCallDurationSeconds = dur
                                )
                            }
                        )
                    }

                    // 3. Operational Action Controls
                    item {
                        ActionControls(
                            isEnabled = config.isReminderEnabled,
                            phoneNumberValid = phoneNumber.trim().isNotEmpty(),
                            onToggleEnable = {
                                if (config.isReminderEnabled) {
                                    viewModel.disableReminder()
                                } else {
                                    viewModel.enableReminder()
                                }
                            },
                            onTestNotification = { viewModel.triggerTestNotification() },
                            onMarkCalledToday = { viewModel.confirmCallToday() }
                        )
                    }

                    // 4. Access Permission Alert Status
                    item {
                        PermissionAlertView(context = context, onGrantRequested = {
                            val permissionsList = mutableListOf<String>()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionsList.add(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionsList.add(android.Manifest.permission.READ_CALL_LOG)
                            requestPermissionLauncher.launch(permissionsList.toTypedArray())
                        })
                    }

                    // 5. Historical Log Label
                    item {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "سجل الاتصالات السابق",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 18.sp
                            )
                            if (callHistory.isNotEmpty()) {
                                Text(
                                    text = "مسح السجل",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .clickable { viewModel.clearHistory() }
                                        .padding(4.dp)
                                        .testTag("clear_history_btn")
                                )
                            }
                        }
                    }

                    // 6. Historical Listing Items / Empty State
                    if (callHistory.isEmpty()) {
                        item {
                            EmptyHistoryCard()
                        }
                    } else {
                        items(callHistory) { record ->
                            CallHistoryItem(record = record)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardCard(config: ReminderConfig) {
    val isCalledToday = isSameDay(config.lastCallTimestamp)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_card"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = config.contactName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (config.phoneNumber.isEmpty()) "لا يوجد رقم هاتف مسجل" else config.phoneNumber,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                // Check Circle/Alert status sign
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isCalledToday) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCalledToday) Icons.Default.Favorite else Icons.Default.DateRange,
                        contentDescription = null,
                        tint = if (isCalledToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Current state logs
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Reminder Switch Status
                StateRow(
                    label = "حالة التذكير تلقائيًا:",
                    value = if (config.isReminderEnabled) "نشط ومفعّل" else "متوقّف حاليًا",
                    isSuccess = config.isReminderEnabled
                )

                // Last verified Call time
                StateRow(
                    label = "آخر مكالمة تم تسجيلها:",
                    value = formatDateTime(config.lastCallTimestamp),
                    isHighlight = config.lastCallTimestamp > 0L
                )

                // Called today?
                StateRow(
                    label = "حالة الاتصال اليوم:",
                    value = if (isCalledToday) "تم الاتصال بنجاح! ❤️" else "لم يتم الاتصال اليوم ⚠️",
                    isSuccess = isCalledToday
                )

                // Next scheduled run trigger
                if (config.isReminderEnabled && config.nextReminderTimestamp > 0L) {
                    StateRow(
                        label = "التنبيه التالي المجدول:",
                        value = formatDateTime(config.nextReminderTimestamp),
                        isHighlight = true
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    contactName: String,
    onContactNameChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    intervalDays: Int,
    onIntervalDaysChange: (Int) -> Unit,
    snoozeMinutes: Int,
    onSnoozeMinutesChange: (Int) -> Unit,
    minDurationSeconds: String,
    onMinDurationSecondsChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_card"),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "إعدادات الاتصال والمواعيد",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )

            // Name Input
            OutlinedTextField(
                value = contactName,
                onValueChange = onContactNameChange,
                label = { Text("اسم الشخص") },
                maxLines = 1,
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contact_name_input")
            )

            // Phone Input
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = onPhoneNumberChange,
                label = { Text("رقم الهاتف") },
                maxLines = 1,
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phone_number_input")
            )

            // Interval days Chips Selectors (Filter Chips M3)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "تكرار التنبيه الدوري:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val intervals = listOf(
                        1 to stringResource(R.string.every_day),
                        2 to stringResource(R.string.every_2_days),
                        7 to stringResource(R.string.every_week)
                    )
                    intervals.forEach { (days, label) ->
                        val selected = intervalDays == days
                        FilterChip(
                            selected = selected,
                            onClick = { onIntervalDaysChange(days) },
                            label = { Text(label, fontSize = 13.sp) },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("interval_chip_$days")
                        )
                    }
                }
            }

            // Snooze retry selection chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "طلب تذكير إضافي في حالة تجاهل الإشعار:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val snoozes = listOf(
                        30 to stringResource(R.string.snooze_30_min),
                        60 to stringResource(R.string.snooze_1_hour),
                        120 to stringResource(R.string.snooze_2_hours)
                    )
                    snoozes.forEach { (minutes, label) ->
                        val selected = snoozeMinutes == minutes
                        FilterChip(
                            selected = selected,
                            onClick = { onSnoozeMinutesChange(minutes) },
                            label = { Text(label, fontSize = 13.sp) },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("snooze_chip_$minutes")
                        )
                    }
                }
            }

            // Min Call Duration text field
            OutlinedTextField(
                value = minDurationSeconds,
                onValueChange = { onMinDurationSecondsChange(it.filter { char -> char.isDigit() }) },
                label = { Text("أقل مدة مكالمة لاعتبار الاتصال ناجحًا (ثانية)") },
                maxLines = 1,
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("min_duration_input")
            )

            // Submit Button
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_settings_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("حفظ البيانات الكليّة", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun ActionControls(
    isEnabled: Boolean,
    phoneNumberValid: Boolean,
    onToggleEnable: () -> Unit,
    onTestNotification: () -> Unit,
    onMarkCalledToday: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("action_controls_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "التحكم في المنبّه اليدوي والتلقائي",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )

            // Enable / Disable Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Main switch button
                Button(
                    onClick = onToggleEnable,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("toggle_reminder_btn")
                ) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Default.Close else Icons.Default.Notifications,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnabled) "إيقاف التذكير" else "تفعيل التذكير",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Quick test alarm triggers action
                OutlinedButton(
                    onClick = onTestNotification,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("test_notification_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اختبار الإشعار", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Quick Mark-as-completed Today (أنا اتصلت اليوم)
            Button(
                onClick = onMarkCalledToday,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("manual_confirm_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "لقد اتصلت واطمأننت عليها اليوم! ❤️",
                    color = MaterialTheme.colorScheme.onSecondary,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun PermissionAlertView(
    context: Context,
    onGrantRequested: () -> Unit
) {
    val postNotifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    val callLogGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    if (!postNotifGranted || !callLogGranted) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("permission_alert_view")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "صلاحيات الهاتف المطلوبة",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 15.sp
                    )
                }

                if (!postNotifGranted) {
                    Text(
                        text = "⚠️ صلاحية الإشعارات غير مفعلة. يرجى تفعيلها حتى يذكرك تطبيق Call Reminder بالاتصال تلقائيًا.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }

                if (!callLogGranted) {
                    Text(
                        text = "⚠️ صلاحية سجل الاتصالات معطلة. سيعمل التطبيق بطريقة التأكيد والمتابعة اليدوية فقط دون استقصاء وتأكيد آلي للمكالمة الصادرة.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }

                Button(
                    onClick = onGrantRequested,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("منح الصلاحيات الآن", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun CallHistoryItem(record: CallRecord) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("call_history_item"),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (record.isManual) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (record.isManual) Icons.Default.Favorite else Icons.Default.Phone,
                        contentDescription = null,
                        tint = if (record.isManual) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "اطمأننت على " + record.callerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatDateTime(record.timestamp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Tag indicator (Manual vs Automatic check)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (record.isManual) MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (record.isManual) "يدويّ" else "تأكيد تلقائي (" + record.durationSeconds + "ث)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (record.isManual) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
fun EmptyHistoryCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_history_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "ابدأ اتصالك الأوّل وسجّله للتاريخ اليوم! ❤️",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun StateRow(label: String, value: String, isSuccess: Boolean = false, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                isSuccess -> MaterialTheme.colorScheme.tertiary
                isHighlight -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
            }
        )
    }
}

// Utility conversion helper to verify calendar day equality
fun isSameDay(timestamp: Long): Boolean {
    if (timestamp == 0L) return false
    val calChecked = Calendar.getInstance().apply { timeInMillis = timestamp }
    val calToday = Calendar.getInstance()
    return calChecked.get(Calendar.YEAR) == calToday.get(Calendar.YEAR) &&
            calChecked.get(Calendar.DAY_OF_YEAR) == calToday.get(Calendar.DAY_OF_YEAR)
}

// Format Unix Timestamp into clean localized Arabic style
fun formatDateTime(timestamp: Long): String {
    if (timestamp == 0L) return "لا يوجد مكالمات مسجلة"
    val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar"))
    return sdf.format(Date(timestamp))
}
