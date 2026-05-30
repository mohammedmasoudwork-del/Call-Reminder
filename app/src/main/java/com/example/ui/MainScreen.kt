package com.example.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CallRecord
import com.example.data.Person
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ReminderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val people by viewModel.filteredPeopleList.collectAsStateWithLifecycle()
    val callHistory by viewModel.callHistory.collectAsStateWithLifecycle()
    val pendingWhatsappPerson by viewModel.pendingWhatsAppConfirm.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var personToEdit by remember { mutableStateOf<Person?>(null) }

    // Request permissions
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val postNotif = permissions[android.Manifest.permission.POST_NOTIFICATIONS] ?: true
        val readCall = permissions[android.Manifest.permission.READ_CALL_LOG] ?: true

        if (!postNotif) {
            Toast.makeText(context, "لم يتم تفعيل الإشعارات، قد تفوتك تذكيرات التواصل!", Toast.LENGTH_LONG).show()
        }
        if (!readCall) {
            Toast.makeText(context, "لم يتم تفعيل صلاحية سجل المكالمات، ستحتاج لتسجيل نجاح تواصلك يدوياً.", Toast.LENGTH_LONG).show()
        }
    }

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
                "person_saved" -> "تم حفظ بيانات الشخص بنجاح ✨"
                "person_deleted" -> "تم حذف الشخص بنجاح"
                "phone_number_required" -> "يرجى إضافة رقم الهاتف للتفعيل!"
                "reminder_enabled" -> "تم تفعيل التنبيهات بنجاح 🔔"
                "reminder_disabled" -> "تم إلغاء التنبيهات لهذا الشخص 🔕"
                "call_confirmed_manual" -> "تم تسجيل التواصل بنجاح! طاب يومك ❤️"
                "whatsapp_confirmed" -> "تم تسجيل التواصل عبر واتساب بنجاح! 🌸"
                "whatsapp_snoozed" -> "تم تأجيل تذكير واتساب"
                "whatsapp_not_installed" -> "تطبيق واتساب غير مثبت على هذا الجهاز!"
                "history_cleared" -> "تم مسح سجل النشاط بنجاح"
                else -> event
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Enforce RTL explicitly for Arabian warmth
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "صِلَة",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 32.sp
                            )
                            Text(
                                text = "تواصل مع من تحب ولا تنسى صلة رحمك",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier.testTag("app_bar")
                )
            },
            floatingActionButton = {
                if (selectedTab == 0) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            personToEdit = null
                            showAddEditDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("إضافة شخص", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("add_person_fab")
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Tab Selection Layout
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Home, contentDescription = null)
                                Text("الأشخاص والمتابعة", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                                Text("المؤشرات والنشاط", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                // Main Content View Switcher
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        slideInHorizontally { width -> if (selectedTab == 0) width else -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> if (selectedTab == 0) -width else width } + fadeOut()
                    },
                    label = "tab_switch"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> {
                            PeopleTabContent(
                                people = people,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                onEditPerson = { person ->
                                    personToEdit = person
                                    showAddEditDialog = true
                                },
                                onToggleReminder = { person, enabled ->
                                    viewModel.toggleReminder(person, enabled)
                                },
                                onCall = { person -> viewModel.triggerCall(context, person) },
                                onWhatsApp = { person -> viewModel.triggerWhatsApp(context, person) },
                                onManualConfirm = { person -> viewModel.triggerManualConfirm(person) },
                                onTestNotification = { person -> viewModel.triggerTestNotification(person) }
                            )
                        }
                        1 -> {
                            InsightsTabContent(
                                people = people,
                                callHistory = callHistory,
                                onClearHistory = { viewModel.clearAllHistory() },
                                onRequestPermission = {
                                    val permissionsList = mutableListOf<String>()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissionsList.add(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    permissionsList.add(android.Manifest.permission.READ_CALL_LOG)
                                    requestPermissionLauncher.launch(permissionsList.toTypedArray())
                                }
                            )
                        }
                    }
                }
            }

            // WhatsApp confirmation pop-up
            pendingWhatsappPerson?.let { person ->
                AlertDialog(
                    onDismissRequest = { viewModel.confirmWhatsAppContact(person, false) },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmWhatsAppContact(person, true) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("نعم، تم بنجاح")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.confirmWhatsAppContact(person, false) }
                        ) {
                            Text("لا، لاحقاً", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = { Text("تأكيد التواصل عبر واتساب", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            text = "هل نجح تواصلك مع ${person.name} باستخدام رسالة واتساب المجهزة؟",
                            textAlign = TextAlign.Center
                        )
                    },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }

            // Add/Edit Dialog sheet
            if (showAddEditDialog) {
                AddEditPersonDialog(
                    person = personToEdit,
                    onDismiss = { showAddEditDialog = false },
                    onSave = { name, phone, avatar, commType, interval, snooze, minCall, msg, notes, enabled ->
                        viewModel.savePerson(
                            id = personToEdit?.id ?: 0,
                            name = name,
                            phoneNumber = phone,
                            photoUri = avatar,
                            preferredCommType = commType,
                            intervalDays = interval,
                            snoozeMinutes = snooze,
                            minCallDurationSeconds = minCall,
                            defaultWhatsAppMessage = msg,
                            notes = notes,
                            isReminderEnabled = enabled
                        )
                        showAddEditDialog = false
                    },
                    onDelete = personToEdit?.let {
                        {
                            viewModel.deletePerson(it)
                            showAddEditDialog = false
                        }
                    }
                )
            }
        }
    }
}

// ---------------------- PEOPLE TAB ----------------------

@Composable
fun PeopleTabContent(
    people: List<Person>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEditPerson: (Person) -> Unit,
    onToggleReminder: (Person, Boolean) -> Unit,
    onCall: (Person) -> Unit,
    onWhatsApp: (Person) -> Unit,
    onManualConfirm: (Person) -> Unit,
    onTestNotification: (Person) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Polished Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("البحث في الأهل والأقارب...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            } else null,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_field"),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (people.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = if (searchQuery.isEmpty()) "لم تقم بإضافة أشخاص بعد!" else "لا توجد نتائج مطابقة لبحثك",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "اضغط على الزر بالأسفل لإضافة أول شخص تود تذكره.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        } else {
            // Priority list of check-in status
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(people, key = { it.id }) { person ->
                    PersonCard(
                        person = person,
                        onClick = { onEditPerson(person) },
                        onToggleReminder = { onToggleReminder(person, it) },
                        onCall = { onCall(person) },
                        onWhatsApp = { onWhatsApp(person) },
                        onManualConfirm = { onManualConfirm(person) },
                        onTestNotification = { onTestNotification(person) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(84.dp)) // Extra padding for FAB overlay
                }
            }
        }
    }
}

@Composable
fun PersonCard(
    person: Person,
    onClick: () -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onManualConfirm: () -> Unit,
    onTestNotification: () -> Unit
) {
    val status = getCheckinStatus(person)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("person_card_${person.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Customized avatar with dynamic Material Initial backgrounds
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(getAvatarColor(person.photoUri), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = person.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }

                // Name and details
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = person.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Preferred Channel Icon Indicator
                        Icon(
                            imageVector = when (person.preferredCommType) {
                                "WHATSAPP" -> Icons.Default.Send
                                "CALL" -> Icons.Default.Phone
                                else -> Icons.Default.Share
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "هاتف: ${person.phoneNumber}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "التكرار: كل ${getArabicDaysText(person.intervalDays)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Alarm toggle switches
                Switch(
                    checked = person.isReminderEnabled,
                    onCheckedChange = onToggleReminder,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.scale(0.85f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Check-in Priority Alert status badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(status.backgroundColor.copy(alpha = 0.08f))
                    .border(0.5.dp, status.backgroundColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(status.backgroundColor, CircleShape)
                    )
                    Text(
                        text = status.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = status.textColor
                    )
                }
                Text(
                    text = if (person.lastContactTimestamp == 0L) "لم يتم التواصل بعد" else "آخر تواصل: " + formatRelativeDays(person.lastContactTimestamp),
                    fontSize = 12.sp,
                    color = status.textColor.copy(alpha = 0.9f)
                )
            }

            // Optional note preview
            if (person.notes.trim().isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📝 " + person.notes,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Dynamic interactive quick action row buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Call quick launcher
                if (person.preferredCommType == "CALL" || person.preferredCommType == "BOTH") {
                    OutlinedButton(
                        onClick = onCall,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("اتصل الآن", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // WhatsApp quick launcher
                if (person.preferredCommType == "WHATSAPP" || person.preferredCommType == "BOTH") {
                    Button(
                        onClick = onWhatsApp,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مراسلة واتساب", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Manual check todays action
                IconButton(
                    onClick = onManualConfirm,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "تم التواصل اليوم",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Small debug triggers notification
                IconButton(
                    onClick = onTestNotification,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "تجربة إشعار",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ---------------------- INSIGHTS / ACTIVITY TAB ----------------------

@Composable
fun InsightsTabContent(
    people: List<Person>,
    callHistory: List<CallRecord>,
    onClearHistory: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()

    // Calculate core analytical statistics based on specification requirements
    val contactedThisWeekCount = remember(people, now) {
        people.count { it.lastContactTimestamp >= now - (7L * 24 * 60 * 60 * 1000) && it.lastContactTimestamp > 0L }
    }
    val overdueMonthCount = remember(people, now) {
        people.count {
            val daysSince = (now - it.lastContactTimestamp) / (24 * 60 * 60 * 1000)
            (it.lastContactTimestamp == 0L && it.isReminderEnabled) || (daysSince >= 30 && it.isReminderEnabled)
        }
    }
    val avgMonthlyCountCalculated = remember(callHistory, now) {
        // Total logs within last 30 days
        val lastMonthLogs = callHistory.count { it.timestamp >= now - (30L * 24 * 60 * 60 * 1000) }
        lastMonthLogs
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
    ) {
        // 1. Core Simple Statistics Dashboard Horizontal Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "الإحصائيات والتحليلات",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatCard(
                        title = "تواصل هذا الأسبوع",
                        value = contactedThisWeekCount.toString(),
                        subtitle = "أشخاص مؤكدين",
                        color = MaterialTheme.colorScheme.primaryContainer,
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = Icons.Default.Favorite,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "لم يتصلوا > شهر",
                        value = overdueMonthCount.toString(),
                        subtitle = "بحاجة صلة وتفقد",
                        color = MaterialTheme.colorScheme.errorContainer,
                        textColor = MaterialTheme.colorScheme.onErrorContainer,
                        icon = Icons.Default.Warning,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "معدل الشهري الكلي",
                        value = avgMonthlyCountCalculated.toString(),
                        subtitle = "عملية تواصل مؤكدة",
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Priority visual dashboard overview cards (لوحة المتابعة حسب الأولوية)
        item {
            PrioritySummaryView(people = people)
        }

        // 3. Operational Permission Status Alert info
        item {
            PermissionAlertView(context = context, onGrantRequested = onRequestPermission)
        }

        // 4. Activity Logs header label
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "سجل النشاط والتواصل التاريخي",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp
                )
                if (callHistory.isNotEmpty()) {
                    Text(
                        text = "مسح السجل",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onClearHistory() }
                            .padding(4.dp)
                            .testTag("clear_history_btn")
                    )
                }
            }
        }

        // 5. Historical List records
        if (callHistory.isEmpty()) {
            item {
                EmptyHistoryCard()
            }
        } else {
            items(callHistory) { record ->
                DetailedHistoryItem(record = record)
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    textColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = value,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                color = textColor,
                lineHeight = 24.sp
            )
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = textColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun PrioritySummaryView(people: List<Person>) {
    val overdue = remember(people) {
        people.filter {
            getCheckinStatus(it) is CheckinStatus.Overdue
        }
    }
    val close = remember(people) {
        people.filter {
            getCheckinStatus(it) is CheckinStatus.Warning
        }
    }
    val safe = remember(people) {
        people.filter {
            getCheckinStatus(it) is CheckinStatus.Safe
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "جاهزية الاتصال ومستويات المتابعة",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PriorityCategoryRow(
                    label = "متأخر جداً في التواصل ⚠️",
                    count = overdue.size,
                    color = MaterialTheme.colorScheme.error,
                    description = overdue.joinToString { it.name }.ifEmpty { "لا أحد للآن، أحسنت!" }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                PriorityCategoryRow(
                    label = "يحتاج تواصل قريباً ⏳",
                    count = close.size,
                    color = Color(0xFFF2994A), // Orange warmth
                    description = close.joinToString { it.name }.ifEmpty { "كل الأحباب مرويين بالوصال" }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                PriorityCategoryRow(
                    label = "تم التواصل مؤخراً ومطمئن ✅",
                    count = safe.size,
                    color = Color(0xFF27AE60), // Mint green
                    description = safe.joinToString { it.name }.ifEmpty { "يرجى تفقد قائمة صلة رحمك" }
                )
            }
        }
    }
}

@Composable
fun PriorityCategoryRow(
    label: String,
    count: Int,
    color: Color,
    description: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
                Text(text = label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(text = "$count شخص", fontWeight = FontWeight.Black, color = color, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 18.dp)
        )
    }
}

@Composable
fun DetailedHistoryItem(record: CallRecord) {
    val isWhatsapp = record.commType == "WHATSAPP"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isWhatsapp) Color(0xFF25D366).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWhatsapp) Icons.Default.Send else Icons.Default.Phone,
                        contentDescription = null,
                        tint = if (isWhatsapp) Color(0xFF25D366) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "اطمأننت على " + record.personName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatDateTime(record.timestamp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Record verification type indicator
            val tagColor = if (isWhatsapp) Color(0xFF25D366) else MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(tagColor.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isWhatsapp) "رسالة واتساب" else if (record.isManual) "اتصال يدوي" else "اتصال مؤكد (${record.durationSeconds} ث)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tagColor
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
                        text = "⚠️ صلاحية الإشعارات غير مفعلة. يرجى تفعيلها حتى يذكرك تطبيق صِلَة بالاتصال تلقائيًا.",
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

// ---------------------- ADD / EDIT DIAOLOG ----------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPersonDialog(
    person: Person?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        phone: String,
        avatar: String,
        commType: String,
        interval: Int,
        snooze: Int,
        minCall: Int,
        whatsappMsg: String,
        notes: String,
        enabled: Boolean
    ) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(person?.name ?: "") }
    var phone by remember { mutableStateOf(person?.phoneNumber ?: "") }
    var avatar by remember { mutableStateOf(person?.photoUri ?: "avatar_1") }
    var commType by remember { mutableStateOf(person?.preferredCommType ?: "CALL") }
    var interval by remember { mutableStateOf(person?.intervalDays ?: 1) }
    var snooze by remember { mutableStateOf(person?.snoozeMinutes ?: 30) }
    var minCall by remember { mutableStateOf(person?.minCallDurationSeconds?.toString() ?: "30") }
    var whatsappMsg by remember { mutableStateOf(person?.defaultWhatsAppMessage ?: "السلام عليكم، كيف حالك؟ ❤️") }
    var notes by remember { mutableStateOf(person?.notes ?: "") }
    var isEnabled by remember { mutableStateOf(person?.isReminderEnabled ?: true) }

    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty() || phone.trim().isEmpty()) {
                        showError = true
                    } else {
                        onSave(
                            name,
                            phone,
                            avatar,
                            commType,
                            interval,
                            snooze,
                            minCall.toIntOrNull() ?: 30,
                            whatsappMsg,
                            notes,
                            isEnabled
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("حفظ البيانات")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("حذف الشخص")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        title = {
            Text(
                text = if (person == null) "إضافة صلة تواصل جديدة" else "تعديل الملف ومواعيد الوصل",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("add_edit_dialog"),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (showError) {
                    Text(
                        text = "الرجاء تعبئة الاسم ورقم الهاتف للوصل!",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // 1. Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم") },
                    maxLines = 1,
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().testTag("add_contact_name_input")
                )

                // 2. Phone Input
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    placeholder = { Text("مثال: 01123456789") },
                    maxLines = 1,
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().testTag("add_phone_number_input")
                )

                // 3. Avatar selection slider / Preset picker chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("لون الرمز التعريفي:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("avatar_1", "avatar_2", "avatar_3", "avatar_4", "avatar_5").forEach { av ->
                            val selected = avatar == av
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(getAvatarColor(av))
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { avatar = av }
                            )
                        }
                    }
                }

                // 4. Comm Preference Type segmented row (CALL, WHATSAPP, BOTH)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("نوع التواصل المفضل:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "CALL" to "اتصال هاتفي",
                            "WHATSAPP" to "واتساب فقط",
                            "BOTH" to "اتصال أو واتساب"
                        ).forEach { (type, title) ->
                            val selected = commType == type
                            FilterChip(
                                selected = selected,
                                onClick = { commType = type },
                                label = { Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(14.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Show default Whatsapp message template field if WHATSAPP or BOTH
                if (commType == "WHATSAPP" || commType == "BOTH") {
                    OutlinedTextField(
                        value = whatsappMsg,
                        onValueChange = { whatsappMsg = it },
                        label = { Text("رسالة الواتساب الافتراضية") },
                        maxLines = 3,
                        placeholder = { Text("أكتب رسالتك لفتحها تلقائياً عند النقر...") },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 5. Interval Selection Dropdown/Chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("تكرار التنبيه الدوري للاطمئنان:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            1 to "يومي",
                            2 to "يومين",
                            7 to "أسبوعي",
                            14 to "كل ١٤ يوم",
                            30 to "شهري"
                        ).forEach { (days, label) ->
                            val selected = interval == days
                            FilterChip(
                                selected = selected,
                                onClick = { interval = days },
                                label = { Text(label, fontSize = 11.sp) },
                                shape = RoundedCornerShape(14.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }

                // 6. Snooze & Call parameters
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("مدة إعادة التذكير (في حال التجاهل):", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            30 to "٣٠ دقيقة",
                            60 to "ساعة",
                            120 to "ساعتين"
                        ).forEach { (minutes, label) ->
                            val selected = snooze == minutes
                            FilterChip(
                                selected = selected,
                                onClick = { snooze = minutes },
                                label = { Text(label, fontSize = 11.sp) },
                                shape = RoundedCornerShape(14.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }

                // Min Call verification duration
                if (commType == "CALL" || commType == "BOTH") {
                    OutlinedTextField(
                        value = minCall,
                        onValueChange = { minCall = it.filter { char -> char.isDigit() } },
                        label = { Text("الحد الأدنى للمكالمة الناجحة (بالثواني)") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 7. Optional Notes field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات اختيارية") },
                    placeholder = { Text("مثال: الأوقات المفضلة، تفاصيل عن العائلة...") },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )

                // 8. Enabler toggle status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("تنبيهات المتابعة التلقائية مفعلة؟", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
            }
        }
    )
}

// ---------------------- UTILITIES & FORMATTING ----------------------

sealed class CheckinStatus(val label: String, val backgroundColor: Color, val textColor: Color) {
    data class Overdue(val daysOver: Int) : CheckinStatus("متأخر جداً في التواصل ⚠️", Color(0xFFEB5757), Color(0xFFC0392B))
    data class Warning(val daysLeft: Int) : CheckinStatus("يحتاج تواصل قريباً ⏳", Color(0xFFF2994A), Color(0xFFD35400))
    data class Safe(val daysRecent: Int) : CheckinStatus("تم التواصل معه مؤخراً ✅", Color(0xFF27AE60), Color(0xFF1E8449))
}

fun getCheckinStatus(person: Person): CheckinStatus {
    if (person.lastContactTimestamp == 0L) {
        return CheckinStatus.Overdue(999)
    }
    val elapsedMillis = System.currentTimeMillis() - person.lastContactTimestamp
    val elapsedDays = (elapsedMillis / (24 * 60 * 60 * 1000)).toInt()

    return when {
        elapsedDays >= person.intervalDays * 1.5 -> CheckinStatus.Overdue(elapsedDays - person.intervalDays)
        elapsedDays >= person.intervalDays -> CheckinStatus.Warning(person.intervalDays * 2 - elapsedDays)
        else -> CheckinStatus.Safe(elapsedDays)
    }
}

fun formatRelativeDays(timestamp: Long): String {
    val distanceMillis = System.currentTimeMillis() - timestamp
    val distanceDays = (distanceMillis / (24 * 60 * 60 * 1000)).toInt()

    return when {
        distanceDays <= 0 -> "اليوم"
        distanceDays == 1 -> "منذ يوم"
        distanceDays == 2 -> "منذ يومين"
        distanceDays in 3..10 -> "منذ $distanceDays أيام"
        else -> "منذ $distanceDays يوم"
    }
}

fun getArabicDaysText(days: Int): String {
    return when (days) {
        1 -> "يوم"
        2 -> "يومين"
        7 -> "أسبوع"
        14 -> "أسبوعين"
        30 -> "شهر"
        else -> "$days يوم"
    }
}

fun getAvatarColor(photoUri: String): Color {
    return when (photoUri) {
        "avatar_1" -> Color(0xFFE74C3C) // Cozy Red
        "avatar_2" -> Color(0xFF9B59B6) // Purple royalty
        "avatar_3" -> Color(0xFF3498DB) // Ocean blue
        "avatar_4" -> Color(0xFF16A085) // Sage teal
        "avatar_5" -> Color(0xFFF1C40F) // Sunbeam yellow
        else -> Color(0xFF7F8C8D) // Warm grey slate
    }
}

fun formatDateTime(timestamp: Long): String {
    if (timestamp == 0L) return "لا يوجد مكالمات مسجلة"
    val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar"))
    return sdf.format(Date(timestamp))
}
